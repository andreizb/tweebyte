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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;
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
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

/**
 * Branch-coverage tests for the conditional paths {@link MediaServiceTests} cannot reach
 * through the public API alone: the content-addressed store's cache-hit short-circuit, the
 * reveal gate's null-access-hash arm, the {@code primaryType} null / slash-less dispatch,
 * and the private image-pipeline guards ({@code toRGB} already-RGB fast-path,
 * {@code gaussianBlur} radius floor, {@code previewImage} dimension cap). The pipeline
 * helpers are reached reflectively because the public preview path always feeds them the
 * one canonical shape.
 */
class MediaServiceBranchTests {

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

	private UUID seedAsset(byte[] data, String contentType) {
		UUID id = UUID.randomUUID();
		this.cache.put(MediaAssetEntity.builder()
			.id(id)
			.contentType(contentType)
			.sizeBytes((long) data.length)
			.checksum(System.nanoTime())
			.createdAt(LocalDateTime.now())
			.data(data)
			.build());
		return id;
	}

	// --- store cache-hit short-circuit (L138 true branch) -------------

	@Test
	void uploadSecondIdenticalUploadHitsCacheAndSkipsRepository() throws Exception {
		byte[] bytes = "content-addressed dedup".getBytes();
		given(this.repository.findByChecksum(anyLong())).willReturn(java.util.Optional.empty());
		given(this.repository.save(any())).willAnswer(AdditionalAnswers.returnsFirstArg());

		ResponseEntity<Map<String, String>> first = this.mediaService
			.upload(new MockMultipartFile("file", "f.txt", "text/plain", bytes))
			.get();
		// Second upload of identical bytes resolves from the populated cache.
		ResponseEntity<Map<String, String>> second = this.mediaService
			.upload(new MockMultipartFile("file", "f.txt", "text/plain", bytes))
			.get();

		assertThat(second.getBody()).isEqualTo(first.getBody());
		// repository.save invoked exactly once — the cache hit short-circuits the insert.
		Mockito.verify(this.repository, Mockito.times(1)).save(any());
	}

	// --- reveal gate: unknown preview id (first || operand) -----------

	@Test
	void revealReturnsNotFoundWhenPreviewIdDoesNotResolve() throws Exception {
		// previewId is in neither the cache nor the repository → resolveAsset returns null,
		// tripping the FIRST operand (preview == null) of the reveal guard.
		given(this.repository.findById(any())).willReturn(java.util.Optional.empty());

		ResponseEntity<StreamingResponseBody> resp = this.mediaService.reveal(UUID.randomUUID(), "pw").get();

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	// --- reveal gate: preview row with a source but no access hash ----

	@Test
	void revealReturnsNotFoundWhenPreviewHasSourceButNoAccessHash() throws Exception {
		UUID origId = seedAsset(new byte[32], "text/plain");
		UUID previewId = UUID.randomUUID();
		this.cache.put(MediaAssetEntity.builder()
			.id(previewId)
			.contentType(MediaType.IMAGE_JPEG_VALUE)
			.sizeBytes(8L)
			.checksum(System.nanoTime())
			.createdAt(LocalDateTime.now())
			.data(new byte[8])
			.sourceMediaId(origId)
			.build());

		ResponseEntity<StreamingResponseBody> resp = this.mediaService.reveal(previewId, "pw").get();

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	// --- primaryType dispatch branches (null + slash-less) ------------

	@Test
	void previewRejectsNullContentTypeAsUnsupported() {
		UUID srcId = seedAsset("bytes".getBytes(), null);

		Throwable ex = catchThrowable(() -> this.mediaService.preview(srcId, "pw").get());
		assertThat(ex.getCause()).isInstanceOf(UnsupportedMediaTypeException.class);
	}

	@Test
	void previewRejectsSlashLessContentTypeAsUnsupported() {
		// "video" has no '/', so primaryType returns it verbatim; not "image" -> 415.
		UUID srcId = seedAsset("bytes".getBytes(), "video");

		Throwable ex = catchThrowable(() -> this.mediaService.preview(srcId, "pw").get());
		assertThat(ex.getCause()).isInstanceOf(UnsupportedMediaTypeException.class);
	}

	@Test
	void previewAcceptsSlashLessImageContentType() throws Exception {
		// "image" with no subtype still routes to the image pipeline.
		UUID srcId = seedAsset(makeImage(48, 48), "image");
		given(this.repository.findByChecksum(anyLong())).willReturn(java.util.Optional.empty());
		given(this.repository.save(any())).willAnswer(AdditionalAnswers.returnsFirstArg());

		ResponseEntity<Map<String, String>> resp = this.mediaService.preview(srcId, "pw").get();

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	// --- private image-pipeline guards (reflective) -------------------

	@Test
	void toRgbReturnsSourceUntouchedWhenAlreadyIntRgb() throws Exception {
		BufferedImage rgb = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
		BufferedImage out = (BufferedImage) invoke("toRGB", new Class<?>[] { BufferedImage.class }, rgb);
		// Already INT_RGB -> the fast-path returns the same instance.
		assertThat(out).isSameAs(rgb);
	}

	@Test
	void toRgbConvertsNonIntRgbToIntRgb() throws Exception {
		BufferedImage argb = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
		BufferedImage out = (BufferedImage) invoke("toRGB", new Class<?>[] { BufferedImage.class }, argb);
		assertThat(out).isNotSameAs(argb);
		assertThat(out.getType()).isEqualTo(BufferedImage.TYPE_INT_RGB);
	}

	@Test
	void gaussianBlurReturnsSourceWhenRadiusBelowOne() throws Exception {
		BufferedImage src = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
		BufferedImage out = (BufferedImage) invoke("gaussianBlur",
				new Class<?>[] { BufferedImage.class, int.class }, src, 0);
		// radius < 1 -> identity, returns the same instance.
		assertThat(out).isSameAs(src);
	}

	@Test
	void gaussianBlurProcessesWhenRadiusAtLeastOne() throws Exception {
		BufferedImage src = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
		BufferedImage out = (BufferedImage) invoke("gaussianBlur",
				new Class<?>[] { BufferedImage.class, int.class }, src, 1);
		assertThat(out).isNotSameAs(src);
	}

	@Test
	void previewImageRejectsOversizedSource() throws Exception {
		byte[] tooWide = makeImage(4097, 8);

		Throwable ex = catchThrowable(() -> invoke("previewImage", new Class<?>[] { byte[].class }, tooWide));
		assertThat(unwrap(ex)).isInstanceOf(IOException.class);
		assertThat(unwrap(ex)).hasMessageContaining("preview limit");
	}

	@Test
	void previewImageRejectsOversizedHeight() throws Exception {
		byte[] tooTall = makeImage(8, 4097);

		Throwable ex = catchThrowable(() -> invoke("previewImage", new Class<?>[] { byte[].class }, tooTall));
		assertThat(unwrap(ex)).isInstanceOf(IOException.class);
		assertThat(unwrap(ex)).hasMessageContaining("preview limit");
	}

	@Test
	void previewImageRejectsCorruptBytes() {
		byte[] notAnImage = "definitely not an image".getBytes();

		Throwable ex = catchThrowable(() -> invoke("previewImage", new Class<?>[] { byte[].class }, notAnImage));
		assertThat(unwrap(ex)).isInstanceOf(IOException.class).hasMessageContaining("Invalid image");
	}

	// --- isClientAbort cause-chain walk: self-referential cause (L232) ---

	@Test
	void isClientAbortStopsOnSelfReferentialCauseChain() throws Exception {
		// An exception whose getCause() returns itself must not loop forever: the walk
		// breaks on (next == c) and returns false (no client-abort marker found).
		IOException selfCaused = new IOException("boom") {
			@Override
			public synchronized Throwable getCause() {
				return this;
			}
		};

		boolean aborted = (boolean) invokeStatic("isClientAbort", new Class<?>[] { Throwable.class }, selfCaused);

		assertThat(aborted).isFalse();
	}

	private Object invokeStatic(String name, Class<?>[] sig, Object... args) throws Exception {
		Method m = MediaService.class.getDeclaredMethod(name, sig);
		m.setAccessible(true);
		return m.invoke(null, args);
	}

	private Object invoke(String name, Class<?>[] sig, Object... args) throws Exception {
		Method m = MediaService.class.getDeclaredMethod(name, sig);
		m.setAccessible(true);
		return m.invoke(this.mediaService, args);
	}

	private static Throwable unwrap(Throwable t) {
		return (t instanceof InvocationTargetException) ? t.getCause() : t;
	}

	private static byte[] makeImage(int w, int h) throws IOException {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setColor(Color.GREEN);
		g.fillRect(0, 0, w, h);
		g.dispose();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write(img, "png", baos);
		return baos.toByteArray();
	}

}
