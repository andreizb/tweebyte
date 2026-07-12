/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import ro.tweebyte.userservice.entity.MediaAssetEntity;
import ro.tweebyte.userservice.exception.UnsupportedMediaTypeException;
import ro.tweebyte.userservice.repository.MediaAssetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

/**
 * file-download is a generic, content-addressed stream: download(id) reads the resident
 * asset bytes from the cache and writes them to a StreamingResponseBody through a 64
 * KiB-chunk / 6.25 MB/s parkNanos throttle. We exercise: - the happy path (cache hit →
 * full body streamed, headers from the asset, 206) - the cache+DB miss path (404) - the
 * StreamingResponseBody abort handling (benign swallow vs. rethrow)
 */
class MediaServiceTests {

	private final ExecutorService cpuExecutor = Executors.newSingleThreadExecutor();

	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	private MediaAssetRepository repository;

	private MediaCache cache;

	private MediaService mediaService;

	@BeforeEach
	void setUp() {
		this.repository = Mockito.mock(MediaAssetRepository.class);
		this.cache = new MediaCache();
		this.mediaService = new MediaService(this.cpuExecutor, this.repository, this.cache, this.passwordEncoder);
	}

	private UUID seedAsset(byte[] data) {
		return seedAsset(data, "text/plain");
	}

	private UUID seedAsset(byte[] data, String contentType) {
		UUID id = UUID.randomUUID();
		this.cache.put(MediaAssetEntity.builder()
			.id(id)
			.contentType(contentType)
			.sizeBytes((long) data.length)
			.checksum(1L)
			.createdAt(LocalDateTime.now())
			.data(data)
			.build());
		return id;
	}

	@Test
	void downloadStreamsCachedBytesAndPopulatesHeaders() throws Exception {
		byte[] data = new byte[256 * 1024];
		UUID id = seedAsset(data);

		ResponseEntity<StreamingResponseBody> resp = this.mediaService.download(id).get();

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
		assertThat(resp.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
		assertThat(resp.getHeaders().getContentLength()).isEqualTo(data.length);
		assertThat(resp.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isNotNull();
		assertThat(resp.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).startsWith("bytes 0-");
		assertThat(resp.getBody()).isNotNull();
	}

	@Test
	void downloadReturnsNotFoundWhenAbsentFromCacheAndDb() throws Exception {
		UUID id = UUID.randomUUID();
		given(this.repository.findById(id)).willReturn(Optional.empty());

		ResponseEntity<StreamingResponseBody> resp = this.mediaService.download(id).get();

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void streamingBodyWritesAllBytes() throws Exception {
		byte[] data = new byte[256 * 1024];
		UUID id = seedAsset(data);
		ResponseEntity<StreamingResponseBody> resp = this.mediaService.download(id).get();

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		resp.getBody().writeTo(baos);
		assertThat(baos.size()).isEqualTo(data.length);
	}

	@Test
	void streamingBodySwallowsClosedChannelException() throws Exception {
		UUID id = seedAsset(new byte[64 * 1024]);
		ResponseEntity<StreamingResponseBody> resp = this.mediaService.download(id).get();
		// Should not throw.
		resp.getBody().writeTo(throwingStream(new ClosedChannelException()));
	}

	@Test
	void streamingBodySwallowsSocketException() throws Exception {
		UUID id = seedAsset(new byte[64 * 1024]);
		ResponseEntity<StreamingResponseBody> resp = this.mediaService.download(id).get();
		resp.getBody().writeTo(throwingStream(new SocketException("broken pipe")));
	}

	@Test
	void streamingBodySwallowsClientAbortException() throws Exception {
		// A class whose binary name ends in "ClientAbortException" must be swallowed.
		class ClientAbortException extends IOException {

			ClientAbortException(String m) {
				super(m);
			}

		}
		UUID id = seedAsset(new byte[64 * 1024]);
		ResponseEntity<StreamingResponseBody> resp = this.mediaService.download(id).get();
		resp.getBody().writeTo(throwingStream(new ClientAbortException("aborted")));
	}

	@Test
	void streamingBodyRethrowsUnknownIOException() throws Exception {
		UUID id = seedAsset(new byte[64 * 1024]);
		ResponseEntity<StreamingResponseBody> resp = this.mediaService.download(id).get();
		assertThatThrownBy(() -> resp.getBody().writeTo(throwingStream(new IOException("disk full"))))
			.isInstanceOf(IOException.class);
	}

	// --- upload (pure, content-addressed) ----------------------------

	@Test
	void uploadStoresBytesAndReturnsContentAddressedId() throws Exception {
		byte[] bytes = "hello content-addressed world".getBytes();
		given(this.repository.findByChecksum(anyLong())).willReturn(Optional.empty());
		given(this.repository.save(any())).willAnswer(AdditionalAnswers.returnsFirstArg());

		ResponseEntity<Map<String, String>> resp = this.mediaService
			.upload(new MockMultipartFile("file", "f.txt", "text/plain", bytes))
			.get();

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(resp.getBody()).containsEntry("id", UUID.nameUUIDFromBytes(bytes).toString());
	}

	@Test
	void uploadFallsBackToOctetStreamWhenContentTypeMissing() throws Exception {
		byte[] bytes = "no content-type header".getBytes();
		given(this.repository.findByChecksum(anyLong())).willReturn(Optional.empty());
		given(this.repository.save(any())).willAnswer(AdditionalAnswers.returnsFirstArg());

		ResponseEntity<Map<String, String>> resp = this.mediaService.upload(new MockMultipartFile("file", bytes)).get();

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(resp.getBody().get("id")).isNotNull();
	}

	@Test
	void uploadDedupsToExistingRowOnChecksumHit() throws Exception {
		byte[] bytes = "duplicate".getBytes();
		UUID existingId = UUID.randomUUID();
		given(this.repository.findByChecksum(anyLong())).willReturn(Optional.of(MediaAssetEntity.builder()
			.id(existingId)
			.contentType("text/plain")
			.sizeBytes((long) bytes.length)
			.checksum(99L)
			.createdAt(LocalDateTime.now())
			.data(bytes)
			.build()));

		ResponseEntity<Map<String, String>> resp = this.mediaService
			.upload(new MockMultipartFile("file", "f.txt", "text/plain", bytes))
			.get();

		assertThat(resp.getBody()).containsEntry("id", existingId.toString());
		Mockito.verify(this.repository, Mockito.never()).save(any());
	}

	// --- preview (derive degraded JPEG behind a bcrypt gate) ----------

	@Test
	void previewDerivesGatedJpegFromSource() throws Exception {
		UUID srcId = seedAsset(makeImage(64, 48), "image/png");
		given(this.repository.findByChecksum(anyLong())).willReturn(Optional.empty());
		given(this.repository.save(any())).willAnswer(AdditionalAnswers.returnsFirstArg());

		ResponseEntity<Map<String, String>> resp = this.mediaService.preview(srcId, "open-sesame").get();

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(resp.getBody().get("id")).isNotNull();
		Mockito.verify(this.repository)
			.save(Mockito.argThat(a -> a.getSourceMediaId().equals(srcId) && a.getAccessHash() != null
					&& MediaType.IMAGE_JPEG_VALUE.equals(a.getContentType())));
	}

	@Test
	void previewReturnsNotFoundWhenSourceAbsent() throws Exception {
		UUID missing = UUID.randomUUID();
		given(this.repository.findById(missing)).willReturn(Optional.empty());

		ResponseEntity<Map<String, String>> resp = this.mediaService.preview(missing, "pw").get();

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void previewRejectsUnsupportedContentTypeWith415() {
		UUID srcId = seedAsset("a plain text file".getBytes(), "text/plain");

		Throwable ex = catchThrowable(() -> this.mediaService.preview(srcId, "pw").get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(UnsupportedMediaTypeException.class);
		assertThat(((UnsupportedMediaTypeException) ex.getCause()).getStatus())
			.isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
	}

	@Test
	void previewThrowsWhenImageContentTypeButBytesAreCorrupt() {
		UUID srcId = seedAsset("not a real image".getBytes(), "image/jpeg");

		Throwable ex = catchThrowable(() -> this.mediaService.preview(srcId, "pw").get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(UncheckedIOException.class);
	}

	// --- reveal (bcrypt-gated original behind a preview) --------------

	@Test
	void revealStreamsOriginalWithCorrectPassword() throws Exception {
		byte[] original = new byte[2048];
		UUID origId = seedAsset(original);
		UUID previewId = seedGatedPreview(origId, "open-sesame");

		ResponseEntity<StreamingResponseBody> resp = this.mediaService.reveal(previewId, "open-sesame").get();

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		resp.getBody().writeTo(baos);
		assertThat(baos.size()).isEqualTo(original.length);
	}

	@Test
	void revealForbidsWrongPassword() throws Exception {
		UUID origId = seedAsset(new byte[64]);
		UUID previewId = seedGatedPreview(origId, "open-sesame");

		ResponseEntity<StreamingResponseBody> resp = this.mediaService.reveal(previewId, "wrong").get();

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void revealReturnsNotFoundWhenIdIsNotAGatedPreview() throws Exception {
		UUID plainId = seedAsset(new byte[64]); // no sourceMediaId / accessHash

		ResponseEntity<StreamingResponseBody> resp = this.mediaService.reveal(plainId, "open-sesame").get();

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void revealReturnsNotFoundWhenSourceOriginalIsGone() throws Exception {
		UUID missingOrigin = UUID.randomUUID();
		UUID previewId = seedGatedPreview(missingOrigin, "open-sesame");
		given(this.repository.findById(missingOrigin)).willReturn(Optional.empty());

		ResponseEntity<StreamingResponseBody> resp = this.mediaService.reveal(previewId, "open-sesame").get();

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	// --- exists -------------------------------------------------------

	@Test
	void existsTrueFromCache() throws Exception {
		UUID id = seedAsset(new byte[16]);
		assertThat(this.mediaService.exists(id).get().getBody().get("exists")).isTrue();
	}

	@Test
	void existsTrueFromDbWhenNotCached() throws Exception {
		UUID id = UUID.randomUUID();
		given(this.repository.existsById(id)).willReturn(true);
		assertThat(this.mediaService.exists(id).get().getBody().get("exists")).isTrue();
	}

	@Test
	void existsFalseWhenAbsentEverywhere() throws Exception {
		UUID id = UUID.randomUUID();
		given(this.repository.existsById(id)).willReturn(false);
		assertThat(this.mediaService.exists(id).get().getBody().get("exists")).isFalse();
	}

	@Test
	void flushCacheEvictsResidentAssets() throws Exception {
		UUID id = seedAsset(new byte[16]);
		this.mediaService.flushCache();
		given(this.repository.existsById(id)).willReturn(false);
		assertThat(this.mediaService.exists(id).get().getBody().get("exists")).isFalse();
	}

	private UUID seedGatedPreview(UUID sourceId, String password) {
		UUID previewId = UUID.randomUUID();
		this.cache.put(MediaAssetEntity.builder()
			.id(previewId)
			.contentType(MediaType.IMAGE_JPEG_VALUE)
			.sizeBytes(8L)
			.checksum(System.nanoTime())
			.createdAt(LocalDateTime.now())
			.data(new byte[8])
			.sourceMediaId(sourceId)
			.accessHash(this.passwordEncoder.encode(password))
			.build());
		return previewId;
	}

	private static byte[] makeImage(int w, int h) throws IOException {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setColor(Color.BLUE);
		g.fillRect(0, 0, w, h);
		g.dispose();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write(img, "png", baos);
		return baos.toByteArray();
	}

	private static OutputStream throwingStream(IOException err) {
		return new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				throw err;
			}

			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				throw err;
			}
		};
	}

}
