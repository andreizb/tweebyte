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
import java.lang.reflect.Method;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CancellationException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import ro.tweebyte.userservice.entity.MediaAssetEntity;
import ro.tweebyte.userservice.exception.UnsupportedMediaTypeException;
import ro.tweebyte.userservice.repository.MediaAssetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

/**
 * file-download is a generic, content-addressed stream: download(id) reads the resident
 * asset bytes from the cache and writes them to the ServerHttpResponse through a 64
 * KiB-chunk / 6.25 MB/s parkNanos throttle. We exercise: - the happy path (cache hit →
 * full body streamed, headers from the asset, 206) - the cache+DB miss path (404) - the
 * static isBenignClientAbort classifier across every branch
 */
class MediaServiceTests {

	private MediaAssetRepository repository;

	private MediaCache cache;

	private MediaService mediaService;

	@BeforeEach
	void setUp() {
		this.repository = Mockito.mock(MediaAssetRepository.class);
		this.cache = new MediaCache();
		// immediate-scheduler keeps the test fast; production uses a bounded-elastic.
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
			.checksum(1L)
			.createdAt(LocalDateTime.now())
			.data(data)
			.build());
		return id;
	}

	@Test
	void downloadStreamsCachedBytesAndPopulatesHeaders() {
		byte[] data = new byte[256 * 1024];
		UUID id = seedAsset(data);
		MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/media/" + id).build());

		StepVerifier.create(this.mediaService.download(id, exchange)).verifyComplete();

		HttpHeaders headers = exchange.getResponse().getHeaders();
		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
		assertThat(headers.getContentLength()).isEqualTo(data.length);
		assertThat(headers.getContentType()).isNotNull();
		assertThat(headers.getFirst(HttpHeaders.CONTENT_RANGE)).startsWith("bytes 0-");
	}

	@Test
	void downloadReturnsNotFoundWhenAbsentFromCacheAndDb() {
		UUID id = UUID.randomUUID();
		given(this.repository.findById(id)).willReturn(Mono.empty());
		MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/media/" + id).build());

		StepVerifier.create(this.mediaService.download(id, exchange)).verifyComplete();

		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	// --- upload (pure, content-addressed) ----------------------------

	@Test
	void uploadStoresBytesAndReturnsContentAddressedId() {
		byte[] bytes = "hello content-addressed world".getBytes();
		given(this.repository.findByChecksum(anyLong())).willReturn(Mono.empty());
		given(this.repository.save(any())).willAnswer(inv -> Mono.just(inv.getArgument(0)));

		StepVerifier.create(this.mediaService.upload(filePart(bytes, MediaType.TEXT_PLAIN))).assertNext(resp -> {
			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(resp.getBody()).containsEntry("id", UUID.nameUUIDFromBytes(bytes).toString());
		}).verifyComplete();
	}

	@Test
	void uploadFallsBackToOctetStreamWhenContentTypeMissing() {
		byte[] bytes = "no content-type header".getBytes();
		given(this.repository.findByChecksum(anyLong())).willReturn(Mono.empty());
		given(this.repository.save(any())).willAnswer(inv -> Mono.just(inv.getArgument(0)));

		StepVerifier.create(this.mediaService.upload(filePart(bytes, null))).assertNext(resp -> {
			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(resp.getBody().get("id")).isNotNull();
		}).verifyComplete();
	}

	@Test
	void uploadDedupsToExistingRowOnChecksumHit() {
		byte[] bytes = "duplicate".getBytes();
		UUID existingId = UUID.randomUUID();
		given(this.repository.findByChecksum(anyLong())).willReturn(Mono.just(MediaAssetEntity.builder()
			.id(existingId)
			.contentType("text/plain")
			.sizeBytes((long) bytes.length)
			.checksum(99L)
			.createdAt(LocalDateTime.now())
			.data(bytes)
			.build()));

		StepVerifier.create(this.mediaService.upload(filePart(bytes, MediaType.TEXT_PLAIN)))
			.assertNext(resp -> assertThat(resp.getBody()).containsEntry("id", existingId.toString()))
			.verifyComplete();
		Mockito.verify(this.repository, Mockito.never()).save(any());
	}

	// --- preview (derive degraded JPEG behind a bcrypt gate) ----------

	@Test
	void previewDerivesGatedJpegFromSource() throws Exception {
		UUID srcId = seedAsset(makeImage(64, 48), "image/png");
		given(this.repository.findByChecksum(anyLong())).willReturn(Mono.empty());
		given(this.repository.save(any())).willAnswer(inv -> Mono.just(inv.getArgument(0)));

		StepVerifier.create(this.mediaService.preview(srcId, "open-sesame")).assertNext(resp -> {
			assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(resp.getBody().get("id")).isNotNull();
		}).verifyComplete();

		Mockito.verify(this.repository)
			.save(Mockito.argThat(a -> a.getSourceMediaId().equals(srcId) && a.getAccessHash() != null
					&& MediaType.IMAGE_JPEG_VALUE.equals(a.getContentType())));
	}

	@Test
	void previewReturnsNotFoundWhenSourceAbsent() {
		UUID missing = UUID.randomUUID();
		given(this.repository.findById(missing)).willReturn(Mono.empty());

		StepVerifier.create(this.mediaService.preview(missing, "pw"))
			.assertNext(resp -> assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND))
			.verifyComplete();
	}

	@Test
	void previewRejectsUnsupportedContentTypeWith415() {
		UUID srcId = seedAsset("a plain text file".getBytes(), "text/plain");

		StepVerifier.create(this.mediaService.preview(srcId, "pw")).expectErrorSatisfies(t -> {
			assertThat(t).isInstanceOf(UnsupportedMediaTypeException.class);
			assertThat(((UnsupportedMediaTypeException) t).getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
		}).verify();
	}

	@Test
	void previewErrorsWhenImageContentTypeButBytesAreCorrupt() {
		UUID srcId = seedAsset("not a real image".getBytes(), "image/jpeg");

		StepVerifier.create(this.mediaService.preview(srcId, "pw")).expectError(UncheckedIOException.class).verify();
	}

	// --- reveal (bcrypt-gated original behind a preview) --------------

	@Test
	void revealStreamsOriginalWithCorrectPassword() {
		byte[] original = new byte[2048];
		UUID origId = seedAsset(original);
		UUID previewId = seedGatedPreview(origId, "open-sesame");
		MockServerWebExchange exchange = MockServerWebExchange
			.from(MockServerHttpRequest.get("/media/" + previewId + "/reveal").build());

		StepVerifier.create(this.mediaService.reveal(previewId, "open-sesame", exchange)).verifyComplete();

		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
		assertThat(exchange.getResponse().getHeaders().getContentLength()).isEqualTo(original.length);
	}

	@Test
	void revealForbidsWrongPassword() {
		UUID origId = seedAsset(new byte[64]);
		UUID previewId = seedGatedPreview(origId, "open-sesame");
		MockServerWebExchange exchange = MockServerWebExchange
			.from(MockServerHttpRequest.get("/media/" + previewId + "/reveal").build());

		StepVerifier.create(this.mediaService.reveal(previewId, "wrong", exchange)).verifyComplete();

		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void revealReturnsNotFoundWhenIdIsNotAGatedPreview() {
		UUID plainId = seedAsset(new byte[64]); // no sourceMediaId / accessHash
		MockServerWebExchange exchange = MockServerWebExchange
			.from(MockServerHttpRequest.get("/media/" + plainId + "/reveal").build());

		StepVerifier.create(this.mediaService.reveal(plainId, "open-sesame", exchange)).verifyComplete();

		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void revealReturnsNotFoundWhenSourceOriginalIsGone() {
		UUID missingOrigin = UUID.randomUUID();
		UUID previewId = seedGatedPreview(missingOrigin, "open-sesame");
		given(this.repository.findById(missingOrigin)).willReturn(Mono.empty());
		MockServerWebExchange exchange = MockServerWebExchange
			.from(MockServerHttpRequest.get("/media/" + previewId + "/reveal").build());

		StepVerifier.create(this.mediaService.reveal(previewId, "open-sesame", exchange)).verifyComplete();

		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	// --- exists / flush -----------------------------------------------

	@Test
	void existsTrueFromCache() {
		UUID id = seedAsset(new byte[16]);
		StepVerifier.create(this.mediaService.exists(id))
			.assertNext(resp -> assertThat(resp.getBody().get("exists")).isTrue())
			.verifyComplete();
	}

	@Test
	void existsTrueFromDbWhenNotCached() {
		UUID id = UUID.randomUUID();
		given(this.repository.existsById(id)).willReturn(Mono.just(true));
		StepVerifier.create(this.mediaService.exists(id))
			.assertNext(resp -> assertThat(resp.getBody().get("exists")).isTrue())
			.verifyComplete();
	}

	@Test
	void existsFalseWhenAbsentEverywhere() {
		UUID id = UUID.randomUUID();
		given(this.repository.existsById(id)).willReturn(Mono.just(false));
		StepVerifier.create(this.mediaService.exists(id))
			.assertNext(resp -> assertThat(resp.getBody().get("exists")).isFalse())
			.verifyComplete();
	}

	@Test
	void flushCacheEvictsResidentAssets() {
		UUID id = seedAsset(new byte[16]);
		StepVerifier.create(this.mediaService.flushCache()).verifyComplete();
		given(this.repository.existsById(id)).willReturn(Mono.just(false));
		StepVerifier.create(this.mediaService.exists(id))
			.assertNext(resp -> assertThat(resp.getBody().get("exists")).isFalse())
			.verifyComplete();
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
			.accessHash(new BCryptPasswordEncoder().encode(password))
			.build());
		return previewId;
	}

	private static FilePart filePart(byte[] bytes, MediaType contentType) {
		FilePart part = Mockito.mock(FilePart.class);
		HttpHeaders headers = new HttpHeaders();
		if (contentType != null) {
			headers.setContentType(contentType);
		}
		given(part.headers()).willReturn(headers);
		DataBuffer buffer = new DefaultDataBufferFactory().wrap(bytes);
		given(part.content()).willReturn(Flux.just(buffer));
		return part;
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

	// --- isBenignClientAbort ------------------------------------------

	private static boolean invokeBenign(Throwable t) throws Exception {
		Method m = MediaService.class.getDeclaredMethod("isBenignClientAbort", Throwable.class);
		m.setAccessible(true);
		return (boolean) m.invoke(null, t);
	}

	@Test
	void benignAbortReturnsFalseWhenNull() throws Exception {
		assertThat(invokeBenign(null)).isFalse();
	}

	@Test
	void benignAbortReturnsTrueForCancellation() throws Exception {
		assertThat(invokeBenign(new CancellationException("cancel"))).isTrue();
	}

	@Test
	void benignAbortReturnsTrueForInterrupted() throws Exception {
		assertThat(invokeBenign(new InterruptedException("int"))).isTrue();
	}

	@Test
	void benignAbortReturnsTrueForClosedChannel() throws Exception {
		assertThat(invokeBenign(new ClosedChannelException())).isTrue();
	}

	@Test
	void benignAbortReturnsTrueForSocketException() throws Exception {
		assertThat(invokeBenign(new SocketException("reset"))).isTrue();
	}

	@Test
	void benignAbortReturnsTrueForAbortedExceptionByName() throws Exception {
		assertThat(invokeBenign(new MyAbortedException("bye"))).isTrue();
	}

	@Test
	void benignAbortFalseForArbitraryRuntimeException() throws Exception {
		assertThat(invokeBenign(new RuntimeException("boom"))).isFalse();
	}

	@Test
	void benignAbortRecursesIntoCause() throws Exception {
		Throwable cause = new ClosedChannelException();
		Throwable wrapper = new RuntimeException("wrap", cause);
		assertThat(invokeBenign(wrapper)).isTrue();
	}

	@Test
	void benignAbortStopsAtNonBenignChain() throws Exception {
		RuntimeException a = new RuntimeException("a");
		RuntimeException b = new RuntimeException("b", a);
		assertThat(invokeBenign(b)).isFalse();
	}

	private static final class MyAbortedException extends RuntimeException {

		MyAbortedException(String msg) {
			super(msg);
		}

	}

}
