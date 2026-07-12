/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongFunction;
import java.util.zip.CRC32;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import ro.tweebyte.userservice.entity.MediaAssetEntity;
import ro.tweebyte.userservice.exception.UnsupportedMediaTypeException;
import ro.tweebyte.userservice.repository.MediaAssetRepository;

@Service
@RequiredArgsConstructor
public class MediaService {

	private static final int CHUNK_BYTES = 64 * 1024;

	private static final long BYTES_PER_SEC = 6_250_000L;

	private static final String FILENAME = "payload.bin";

	// CPU-bound image pipeline (blur/sobel/resize/JPEG) → cpuExecutor (NCPU fixed),
	// defined in ThreadConfiguration.
	@Qualifier("cpuExecutor")
	private final ExecutorService cpuExecutor;

	private final MediaAssetRepository repository;

	private final MediaCache cache;

	private final BCryptPasswordEncoder passwordEncoder;

	// Pure upload: store the bytes as-is (original; source_media_id null, no gate).
	// Content-type comes from the multipart part; accepts anything. Not a benchmark
	// hot path — seeders and product flows create the originals that previews derive
	// from. Content-addressed, so re-uploading identical bytes dedups to one row.
	public CompletableFuture<ResponseEntity<Map<String, String>>> upload(MultipartFile file) {
		try {
			byte[] bytes = file.getBytes();
			String contentType = (file.getContentType() != null) ? file.getContentType()
					: MediaType.APPLICATION_OCTET_STREAM_VALUE;
			UUID id = store(bytes,
					checksum -> MediaAssetEntity.builder()
						.id(UUID.nameUUIDFromBytes(bytes))
						.contentType(contentType)
						.sizeBytes((long) bytes.length)
						.checksum(checksum)
						.createdAt(LocalDateTime.now())
						.data(bytes)
						.isInsertable(true)
						.build());
			return CompletableFuture.completedFuture(ResponseEntity.ok(Map.of("id", id.toString())));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	// Derive a degraded "sensitive-media preview" of an existing
	// original. The CPU pipeline ALWAYS runs; the CRC32 of its OUTPUT keys a
	// content-addressed lookup (cache → DB → insert). The preview points back at its
	// source (keeps the original GC-reachable) and is gated by a bcrypt of the
	// required password — but bcrypt runs ONLY on the genuine insert (the store
	// factory is invoked on a content-addressed miss), so the benchmark, which
	// re-previews the one seeded source, is a pure-CPU cache hit after warmup and
	// never re-hashes. 404 if the source original does not exist.
	public CompletableFuture<ResponseEntity<Map<String, String>>> preview(UUID sourceId, String password) {
		return CompletableFuture.supplyAsync(() -> {
			MediaAssetEntity source = resolveAsset(sourceId);
			if (source == null) {
				return ResponseEntity.notFound().build();
			}
			try {
				byte[] result = derivePreview(source);
				UUID id = store(result,
						checksum -> MediaAssetEntity.builder()
							.id(UUID.nameUUIDFromBytes(result))
							.contentType(MediaType.IMAGE_JPEG_VALUE)
							.sizeBytes((long) result.length)
							.checksum(checksum)
							.createdAt(LocalDateTime.now())
							.data(result)
							.sourceMediaId(sourceId)
							.accessHash(this.passwordEncoder.encode(password))
							.isInsertable(true)
							.build());
				return ResponseEntity.ok(Map.of("id", id.toString()));
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}, this.cpuExecutor);
	}

	// Content-addressed store. entityFactory receives the CRC32 and is invoked ONLY
	// on a cache+DB miss, so any per-insert cost it carries (e.g. the preview's
	// bcrypt) is paid once per distinct content, never on dedup hits.
	private UUID store(byte[] data, LongFunction<MediaAssetEntity> entityFactory) {
		long checksum = crc32(data);

		MediaAssetEntity cached = this.cache.getByChecksum(checksum);
		if (cached != null) {
			return cached.getId();
		}

		return this.repository.findByChecksum(checksum).map(existing -> {
			this.cache.put(existing);
			return existing.getId();
		}).orElseGet(() -> {
			MediaAssetEntity saved = this.repository.save(entityFactory.apply(checksum));
			this.cache.put(saved);
			return saved.getId();
		});
	}

	// Stream the resident asset bytes through the same
	// 64 KiB-chunk / 6.25 MB/s throttle the workload measures. The seeder GETs the
	// blob once to warm the cache; the DB is the off-path cache-miss fallback.
	public CompletableFuture<ResponseEntity<StreamingResponseBody>> download(UUID id) {
		MediaAssetEntity asset = resolveAsset(id);
		if (asset == null) {
			return CompletableFuture.completedFuture(ResponseEntity.notFound().build());
		}
		return CompletableFuture.completedFuture(streamResponse(asset));
	}

	// Reveal the original behind a gated preview: bcrypt-verify the supplied password
	// against the preview's access_hash, then stream the source original's bytes. The
	// pipeline is one-way, so this returns the stored original (via source_media_id),
	// not a reversal of the transform. Not a benchmark path — the bcrypt match runs on
	// the CPU pool. 404 when the id is not a gated preview (or its source is gone),
	// 403 on a password mismatch.
	public CompletableFuture<ResponseEntity<StreamingResponseBody>> reveal(UUID previewId, String password) {
		MediaAssetEntity preview = resolveAsset(previewId);
		if (preview == null || preview.getSourceMediaId() == null || preview.getAccessHash() == null) {
			return CompletableFuture.completedFuture(ResponseEntity.notFound().build());
		}
		if (!this.passwordEncoder.matches(password, preview.getAccessHash())) {
			return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
		}
		MediaAssetEntity original = resolveAsset(preview.getSourceMediaId());
		if (original == null) {
			return CompletableFuture.completedFuture(ResponseEntity.notFound().build());
		}
		return CompletableFuture.completedFuture(streamResponse(original));
	}

	private ResponseEntity<StreamingResponseBody> streamResponse(MediaAssetEntity asset) {
		byte[] data = asset.getData();
		long total = data.length;

		StreamingResponseBody srb = (OutputStream out) -> streamThrottled(out, data);

		return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
			.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + FILENAME + "\"")
			.header(HttpHeaders.CONTENT_RANGE, "bytes 0-" + (total - 1) + "/" + total)
			.contentType(MediaType.parseMediaType(asset.getContentType()))
			.contentLength(total)
			.body(srb);
	}

	// Stream the resident bytes in 64 KiB chunks throttled to BYTES_PER_SEC. A
	// mid-stream client disconnect (reset/abort) surfaces as a benign IOException that
	// we stop on quietly; anything else propagates.
	private void streamThrottled(OutputStream out, byte[] data) throws IOException {
		try {
			int offset = 0;
			while (offset < data.length) {
				int n = Math.min(CHUNK_BYTES, data.length - offset);
				out.write(data, offset, n);
				offset += n;

				long nanos = (long) (n * 1_000_000_000.0 / BYTES_PER_SEC);
				if (nanos > 0) {
					LockSupport.parkNanos(nanos);
				}
			}
			out.flush();
		}
		catch (IOException | RuntimeException ex) {
			if (!isClientAbort(ex)) {
				throw ex;
			}
			// benign mid-stream client disconnect — nothing left to flush.
		}
	}

	private static boolean isClientAbort(Throwable t) {
		Throwable c = t;
		while (c != null) {
			if (c instanceof ClosedChannelException || c instanceof SocketException
					|| c.getClass().getName().endsWith("ClientAbortException")) {
				return true;
			}
			Throwable next = c.getCause();
			if (next == c) {
				break;
			}
			c = next;
		}
		return false;
	}

	public CompletableFuture<ResponseEntity<Map<String, Boolean>>> exists(UUID id) {
		boolean present = this.cache.getById(id) != null || this.repository.existsById(id);
		return CompletableFuture.completedFuture(ResponseEntity.ok(Map.of("exists", present)));
	}

	public void flushCache() {
		this.cache.flush();
	}

	private MediaAssetEntity resolveAsset(UUID id) {
		MediaAssetEntity cached = this.cache.getById(id);
		if (cached != null) {
			return cached;
		}
		return this.repository.findById(id).map(asset -> {
			this.cache.put(asset);
			return asset;
		}).orElse(null);
	}

	private static long crc32(byte[] data) {
		CRC32 crc = new CRC32();
		crc.update(data);
		return crc.getValue();
	}

	// Per-content-type preview dispatch. Each supported source kind gets its own
	// handler; today only image/* is registered (the 256×256 blur/sobel/JPEG pipeline).
	// Unsupported kinds (text, video, audio, …) fall through to 415. Add a case +
	// handler method here to extend the preview to a new media type.
	private byte[] derivePreview(MediaAssetEntity source) throws IOException {
		return switch (primaryType(source.getContentType())) {
			case "image" -> previewImage(source.getData());
			default -> throw new UnsupportedMediaTypeException(source.getContentType());
		};
	}

	private static String primaryType(String contentType) {
		if (contentType == null) {
			return "";
		}
		int slash = contentType.indexOf('/');
		String type = (slash > 0) ? contentType.substring(0, slash) : contentType;
		return type.trim().toLowerCase(Locale.ROOT);
	}

	// Cap the source dimensions before the full-resolution blur/sobel passes: those
	// allocate width×height int arrays, so an oversized source could exhaust the CPU
	// pool's heap. Both stacks reject identically above the limit; seeded/benchmark
	// assets are 256px, well under the cap.
	private static final int MAX_PREVIEW_DIMENSION = 4096;

	private byte[] previewImage(byte[] input) throws IOException {
		try (InputStream in = new ByteArrayInputStream(input)) {
			BufferedImage img = ImageIO.read(in);
			if (img == null) {
				throw new IOException("Invalid image");
			}
			if (img.getWidth() > MAX_PREVIEW_DIMENSION || img.getHeight() > MAX_PREVIEW_DIMENSION) {
				throw new IOException("Image exceeds " + MAX_PREVIEW_DIMENSION + "px preview limit");
			}

			BufferedImage work = toRGB(img);
			for (int i = 0; i < 3; ++i) {
				work = gaussianBlur(work, 5);
			}
			work = sobel(work);
			work = resize(work, 256, 256);
			return jpeg(work, 0.8f);
		}
	}

	private BufferedImage toRGB(BufferedImage src) {
		if (src.getType() == BufferedImage.TYPE_INT_RGB) {
			return src;
		}
		BufferedImage img = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();
		return img;
	}

	private BufferedImage gaussianBlur(BufferedImage src, int radius) {
		if (radius < 1) {
			return src;
		}

		int w = src.getWidth();
		int h = src.getHeight();
		BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		int[] pixels = new int[w * h];
		int[] temp = new int[w * h];
		src.getRGB(0, 0, w, h, pixels, 0, w);

		float[] kernel = gaussian(radius);

		for (int y = 0; y < h; ++y) {
			for (int x = 0; x < w; ++x) {
				float r = 0;
				float g = 0;
				float b = 0;
				for (int k = -radius; k <= radius; ++k) {
					int xx = Math.clamp((long) x + k, 0, w - 1);
					int rgb = pixels[y * w + xx];
					float kval = kernel[k + radius];
					r += ((rgb >> 16) & 0xff) * kval;
					g += ((rgb >> 8) & 0xff) * kval;
					b += (rgb & 0xff) * kval;
				}

				int ir = Math.clamp(Math.round(r), 0, 255);
				int ig = Math.clamp(Math.round(g), 0, 255);
				int ib = Math.clamp(Math.round(b), 0, 255);
				temp[y * w + x] = (ir << 16) | (ig << 8) | ib;
			}
		}

		for (int x = 0; x < w; ++x) {
			for (int y = 0; y < h; ++y) {
				float r = 0;
				float g = 0;
				float b = 0;
				for (int k = -radius; k <= radius; ++k) {
					int yy = Math.clamp((long) y + k, 0, h - 1);
					int rgb = temp[yy * w + x];
					float kval = kernel[k + radius];
					r += ((rgb >> 16) & 0xff) * kval;
					g += ((rgb >> 8) & 0xff) * kval;
					b += (rgb & 0xff) * kval;
				}
				int ir = Math.clamp(Math.round(r), 0, 255);
				int ig = Math.clamp(Math.round(g), 0, 255);
				int ib = Math.clamp(Math.round(b), 0, 255);
				dst.setRGB(x, y, (ir << 16) | (ig << 8) | ib);
			}
		}
		return dst;
	}

	private float[] gaussian(int r) {
		float[] kernel = new float[2 * r + 1];
		float sigma = r / 2.0f;
		float sum = 0;
		for (int i = -r; i <= r; ++i) {
			float val = (float) Math.exp(-(i * i) / (2 * sigma * sigma));
			kernel[i + r] = val;
			sum += val;
		}
		for (int i = 0; i < kernel.length; ++i) {
			kernel[i] /= sum;
		}
		return kernel;
	}

	private BufferedImage sobel(BufferedImage src) {
		int w = src.getWidth();
		int h = src.getHeight();
		BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		int[] gx = { -1, 0, 1, -2, 0, 2, -1, 0, 1 };
		int[] gy = { -1, -2, -1, 0, 0, 0, 1, 2, 1 };

		for (int y = 1; y < h - 1; ++y) {
			for (int x = 1; x < w - 1; ++x) {
				int sxr = 0;
				int sxg = 0;
				int sxb = 0;
				int syr = 0;
				int syg = 0;
				int syb = 0;
				int idx = 0;
				for (int dy = -1; dy <= 1; ++dy) {
					for (int dx = -1; dx <= 1; ++dx) {
						int rgb = src.getRGB(x + dx, y + dy);
						int r = (rgb >> 16) & 0xff;
						int g = (rgb >> 8) & 0xff;
						int b = rgb & 0xff;
						sxr += gx[idx] * r;
						sxg += gx[idx] * g;
						sxb += gx[idx] * b;
						syr += gy[idx] * r;
						syg += gy[idx] * g;
						syb += gy[idx] * b;
						idx++;
					}
				}
				int magr = Math.min(255, (int) Math.hypot(sxr, syr));
				int magg = Math.min(255, (int) Math.hypot(sxg, syg));
				int magb = Math.min(255, (int) Math.hypot(sxb, syb));
				dst.setRGB(x, y, (magr << 16) | (magg << 8) | magb);
			}
		}
		return dst;
	}

	private BufferedImage resize(BufferedImage src, int w, int h) {
		BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = dst.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(src, 0, 0, w, h, null);
		g.dispose();
		return dst;
	}

	private byte[] jpeg(BufferedImage img, float q) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
		if (!writers.hasNext()) {
			throw new IOException("No JPEG writer");
		}
		ImageWriter writer = writers.next();
		ImageWriteParam param = writer.getDefaultWriteParam();
		if (param.canWriteCompressed()) {
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(q);
		}
		try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
			writer.setOutput(ios);
			writer.write(null, new IIOImage(img, null, null), param);
		}
		finally {
			writer.dispose();
		}
		return baos.toByteArray();
	}

}
