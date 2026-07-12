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
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import ro.tweebyte.userservice.entity.MediaAssetEntity;
import ro.tweebyte.userservice.exception.UnsupportedMediaTypeException;
import ro.tweebyte.userservice.repository.MediaAssetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

/**
 * Targets the branches that {@link MediaServiceTests} cannot easily reach: - the
 * {@code onErrorResume} arm in {@link MediaService#download(UUID, ServerWebExchange)}
 * when the downstream write fails with a benign vs. non-benign error - the
 * {@code cancelled.get()} true-branch inside the generator, by cancelling the
 * StepVerifier subscription mid-stream
 */
class MediaServiceBranchTests {

	private MediaAssetRepository repository;

	private MediaCache cache;

	private MediaService mediaService;

	@BeforeEach
	void setUp() {
		this.repository = Mockito.mock(MediaAssetRepository.class);
		this.cache = new MediaCache();
		this.mediaService = new MediaService(this.repository, this.cache, Schedulers.immediate(),
				new BCryptPasswordEncoder());
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
			.checksum(System.nanoTime())
			.createdAt(LocalDateTime.now())
			.data(data)
			.build());
		return id;
	}

	/** Wraps an exchange so that response.writeWith(...) propagates a fixed error. */
	private static ServerWebExchange exchangeFailingWrite(Throwable err) {
		MockServerWebExchange base = MockServerWebExchange.from(MockServerHttpRequest.get("/media/x").build());
		ServerHttpResponse failing = new ServerHttpResponseDecorator(base.getResponse()) {
			@Override
			public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
				// Drain the body to release buffers, then fail.
				return super.writeWith(body).then(Mono.<Void>error(err))
					// Ensure the error is emitted even if body drain returns empty.
					.onErrorResume(t -> Mono.<Void>error(err));
			}
		};
		return new ServerWebExchangeDecorator(base) {
			@Override
			public ServerHttpResponse getResponse() {
				return failing;
			}
		};
	}

	@Test
	void downloadSwallowsBenignClientAbortFromWriteWith() {
		UUID id = seedAsset(new byte[64 * 1024]);
		ServerWebExchange exchange = exchangeFailingWrite(new ClosedChannelException());

		// Benign abort -> onErrorResume returns Mono.empty()
		StepVerifier.create(this.mediaService.download(id, exchange)).verifyComplete();
	}

	@Test
	void downloadPropagatesNonBenignErrorFromWriteWith() {
		UUID id = seedAsset(new byte[64 * 1024]);
		IllegalStateException boom = new IllegalStateException("downstream blew up");
		ServerWebExchange exchange = exchangeFailingWrite(boom);

		// Non-benign -> onErrorResume returns Mono.error(t)
		StepVerifier.create(this.mediaService.download(id, exchange))
			.expectErrorMatches(t -> t instanceof IllegalStateException && "downstream blew up".equals(t.getMessage()))
			.verify();
	}

	@Test
	void downloadSwallowsNonBenignExceptionWithBenignCause() {
		// An exception whose class/message is non-benign but whose CAUSE is a ClosedChannelException.
		// isBenignClientAbort(t): outer checks fail (it's a RuntimeException, name doesn't contain
		// "AbortedException") → walk the cause chain → while (c != null && c != t) fires → c is
		// ClosedChannelException → isBenignClientAbort(c) returns true → swallowed.
		UUID id = seedAsset(new byte[64 * 1024]);
		ClosedChannelException closedChannel = new ClosedChannelException();
		RuntimeException wrapping = new RuntimeException("wrapped benign", closedChannel);
		ServerWebExchange exchange = exchangeFailingWrite(wrapping);

		StepVerifier.create(this.mediaService.download(id, exchange)).verifyComplete();
	}

	@Test
	void downloadHonoursCancellationFromSubscriber() {
		UUID id = seedAsset(new byte[256 * 1024]);
		MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/media/" + id).build());

		// Cancelling mid-stream forces the cancelled.get() == true branch
		// inside the Flux.generate sink as well as the doOnCancel hook.
		StepVerifier.create(this.mediaService.download(id, exchange))
			.thenAwait(Duration.ofMillis(1))
			.thenCancel()
			.verify(Duration.ofSeconds(5));
	}

	// --- store cache-hit short-circuit (L135 true branch) -------------

	@Test
	void uploadSecondIdenticalUploadHitsCacheAndSkipsRepository() {
		byte[] bytes = "content-addressed dedup".getBytes();
		given(this.repository.findByChecksum(anyLong())).willReturn(Mono.empty());
		given(this.repository.save(any())).willAnswer(inv -> Mono.just(inv.getArgument(0)));

		StepVerifier.create(this.mediaService.upload(filePart(bytes))).expectNextCount(1).verifyComplete();
		// Second upload of identical bytes resolves from the now-populated cache.
		StepVerifier.create(this.mediaService.upload(filePart(bytes))).expectNextCount(1).verifyComplete();

		// repository.save invoked exactly once — the cache hit short-circuits the insert.
		Mockito.verify(this.repository, Mockito.times(1)).save(any());
	}

	// --- reveal gate: preview row with a source but no access hash ----

	@Test
	void revealReturnsNotFoundWhenPreviewHasSourceButNoAccessHash() {
		UUID origId = seedAsset(new byte[32]);
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
		MockServerWebExchange exchange = MockServerWebExchange
			.from(MockServerHttpRequest.get("/media/" + previewId + "/reveal").build());

		StepVerifier.create(this.mediaService.reveal(previewId, "pw", exchange)).verifyComplete();

		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	// --- primaryType dispatch branches (null + slash-less) ------------

	@Test
	void previewRejectsNullContentTypeAsUnsupported() {
		UUID srcId = seedAsset("bytes".getBytes(), null);

		StepVerifier.create(this.mediaService.preview(srcId, "pw"))
			.expectError(UnsupportedMediaTypeException.class)
			.verify();
	}

	@Test
	void previewRejectsSlashLessContentTypeAsUnsupported() {
		// "video" has no '/', so primaryType returns it verbatim; not "image" -> 415.
		UUID srcId = seedAsset("bytes".getBytes(), "video");

		StepVerifier.create(this.mediaService.preview(srcId, "pw"))
			.expectError(UnsupportedMediaTypeException.class)
			.verify();
	}

	@Test
	void previewAcceptsSlashLessImageContentType() throws Exception {
		// "image" with no subtype still routes to the image pipeline.
		UUID srcId = seedAsset(makeImage(48, 48), "image");
		given(this.repository.findByChecksum(anyLong())).willReturn(Mono.empty());
		given(this.repository.save(any())).willAnswer(inv -> Mono.just(inv.getArgument(0)));

		StepVerifier.create(this.mediaService.preview(srcId, "pw"))
			.assertNext(resp -> assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK))
			.verifyComplete();
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
		assertThat(unwrap(ex)).isInstanceOf(UncheckedIOException.class);
		assertThat(unwrap(ex).getCause()).hasMessageContaining("preview limit");
	}

	@Test
	void previewImageRejectsOversizedHeight() throws Exception {
		byte[] tooTall = makeImage(8, 4097);

		Throwable ex = catchThrowable(() -> invoke("previewImage", new Class<?>[] { byte[].class }, tooTall));
		assertThat(unwrap(ex)).isInstanceOf(UncheckedIOException.class);
		assertThat(unwrap(ex).getCause()).hasMessageContaining("preview limit");
	}

	@Test
	void previewImageRejectsCorruptBytes() {
		byte[] notAnImage = "definitely not an image".getBytes();

		Throwable ex = catchThrowable(() -> invoke("previewImage", new Class<?>[] { byte[].class }, notAnImage));
		assertThat(unwrap(ex)).isInstanceOf(UncheckedIOException.class);
		assertThat(unwrap(ex).getCause()).hasMessageContaining("Invalid image");
	}

	private Object invoke(String name, Class<?>[] sig, Object... args) throws Exception {
		Method m = MediaService.class.getDeclaredMethod(name, sig);
		m.setAccessible(true);
		return m.invoke(this.mediaService, args);
	}

	private static Throwable unwrap(Throwable t) {
		return (t instanceof InvocationTargetException) ? t.getCause() : t;
	}

	private FilePart filePart(byte[] bytes) {
		FilePart part = Mockito.mock(FilePart.class);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.TEXT_PLAIN);
		given(part.headers()).willReturn(headers);
		DataBuffer buffer = new DefaultDataBufferFactory().wrap(bytes);
		given(part.content()).willReturn(Flux.just(buffer));
		return part;
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
