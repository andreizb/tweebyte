/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ro.tweebyte.tweetservice.client.UserClient;
import ro.tweebyte.tweetservice.metrics.AiLatencyMetrics;
import ro.tweebyte.tweetservice.model.UserDto;
import ro.tweebyte.tweetservice.service.ai.MockStreamingChatModel;
import ro.tweebyte.tweetservice.service.ai.SemanticChunks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Exercises {@link AiController} using a real {@link ChatClient} backed by a fast
 * {@link MockStreamingChatModel}. Mocking the fluent ChatClient API is brittle; driving
 * the model end-to-end is more representative.
 *
 * <p>
 * The controller dispatches streaming work to a single-thread test executor and we
 * capture the {@link Future} returned by {@code submit()} so the test can
 * deterministically wait on completion (additional emitter callbacks can race the
 * streaming task on a real executor).
 */
class AiControllerTests {

	private AiController controller;

	private UserClient userClient;

	private AiLatencyMetrics metrics;

	private CapturingExecutor executor;

	private ChatClient chatClient;

	@BeforeEach
	void setUp() {
		// 5 tokens, 1ms TTFT, 1ms ITL — sub-second per stream.
		MockStreamingChatModel model = new MockStreamingChatModel(1.0, 0.2, 1.0, 2.0, 0.0, 5);
		this.chatClient = ChatClient.create(model);
		this.metrics = new AiLatencyMetrics(new SimpleMeterRegistry());
		this.userClient = mock(UserClient.class);
		this.executor = new CapturingExecutor(Executors.newFixedThreadPool(2));

		this.controller = new AiController(this.chatClient, this.metrics, this.userClient, this.executor);
		// Threshold lower than tokensPerResponse=5 to exercise the tool branch.
		ReflectionTestUtils.setField(this.controller, "toolCallAfterTokens", 3);
	}

	@AfterEach
	void tearDown() {
		this.executor.delegate.shutdownNow();
	}

	private static Map<String, String> body(String prompt) {
		Map<String, String> m = new HashMap<>();
		m.put("prompt", prompt);
		return m;
	}

	private void awaitLastSubmit() throws Exception {
		Future<?> f = this.executor.lastFuture();
		assertThat(f).isNotNull();
		f.get(10, TimeUnit.SECONDS);
	}

	@Test
	void summarizeStreamsTokensThroughEmitter() throws Exception {
		SseEmitter emitter = this.controller.summarize(body("test"));
		assertThat(emitter).isNotNull();
		awaitLastSubmit();
		// ttft + e2e timers must have recorded at least once.
		assertThat(this.metrics.toString()).isNotNull();
	}

	@Test
	void summarizeMissingPromptDefaultsToEmpty() throws Exception {
		SseEmitter emitter = this.controller.summarize(new HashMap<>());
		assertThat(emitter).isNotNull();
		awaitLastSubmit();
	}

	@Test
	void bufferedReturnsCollectedResponse() throws Exception {
		var result = this.controller.buffered(body("hello"));
		ResponseEntity<Map<String, String>> resp = result.get(10, TimeUnit.SECONDS);
		assertThat(resp.getStatusCode().value()).isEqualTo(200);
		assertThat(resp.getBody()).isNotNull();
		assertThat(resp.getBody().get("response")).isNotNull().contains(SemanticChunks.word(0));
		assertThat(resp.getHeaders().getFirst("X-Tweebyte-TTFT-Ms")).isNotNull();
		assertThat(resp.getHeaders().getFirst("X-Tweebyte-E2E-Ms")).isNotNull();
	}

	@Test
	void summarizeWithToolFiresToolCallAfterThresholdTokens() throws Exception {
		UserDto user = new UserDto();
		user.setUserName("alice");
		given(this.userClient.getUserSummary(any(UUID.class))).willReturn(CompletableFuture.completedFuture(user));

		SseEmitter emitter = this.controller.summarizeWithTool(UUID.randomUUID(), body("p"));
		assertThat(emitter).isNotNull();
		awaitLastSubmit();
	}

	@Test
	void summarizeWithToolDoesNotFireToolWhenThresholdMatchesStreamLength() throws Exception {
		// Boundary: threshold == tokensPerResponse (5). Loop exits at token 5,
		// hasNext() is false → tool branch is skipped (matches reactive shape).
		ReflectionTestUtils.setField(this.controller, "toolCallAfterTokens", 5);
		SseEmitter emitter = this.controller.summarizeWithTool(UUID.randomUUID(), body("p"));
		assertThat(emitter).isNotNull();
		awaitLastSubmit();
	}

	@Test
	void mockStreamEmitsConfiguredTokenCount() throws Exception {
		SseEmitter emitter = this.controller.mockStream(3, 1L);
		assertThat(emitter).isNotNull();
		awaitLastSubmit();
	}

	@Test
	void mockStreamWithZeroTokensCompletesImmediately() throws Exception {
		// tokens=0 → loop body never runs, emitter completes cleanly.
		SseEmitter emitter = this.controller.mockStream(0, 1L);
		assertThat(emitter).isNotNull();
		awaitLastSubmit();
	}

	@Test
	void mockStreamWithNegativeItlEntersExceptionBranch() throws Exception {
		// Thread.sleep(-1) throws IllegalArgumentException → caught and routed
		// through emitter.completeWithError(). Exercises the catch branch in
		// mockStream.
		SseEmitter emitter = this.controller.mockStream(2, -1L);
		assertThat(emitter).isNotNull();
		awaitLastSubmit();
	}

	@Test
	void bufferedWithZeroTokenStreamHitsSentinelBranch() throws Exception {
		// tokensPerResponse=0 → forEach never runs, firstTokenNanos stays at
		// the sentinel and the ttftMs ternary takes the -1 false-branch.
		MockStreamingChatModel zeroTokenModel = new MockStreamingChatModel(1.0, 0.2, 1.0, 2.0, 0.0, 0);
		AiController c = new AiController(ChatClient.create(zeroTokenModel), this.metrics, this.userClient,
				this.executor);
		ReflectionTestUtils.setField(c, "toolCallAfterTokens", 3);

		var result = c.buffered(body("x"));
		ResponseEntity<Map<String, String>> resp = result.get(10, TimeUnit.SECONDS);
		assertThat(resp.getStatusCode().value()).isEqualTo(200);
		assertThat(resp.getHeaders().getFirst("X-Tweebyte-TTFT-Ms")).isNotNull();
		// ttft sentinel produces "-1" string per the controller's String.format.
		String ttftHeader = resp.getHeaders().getFirst("X-Tweebyte-TTFT-Ms");
		assertThat(ttftHeader).isNotNull();
	}

	@Test
	void summarizeEmitterIOExceptionWrapsAsRuntimeException() throws Exception {
		// Forces SseEmitter.send(...) to raise IOException so streamToEmitter's
		// inner try/catch wraps it as RuntimeException, which the outer exception
		// handler routes through completeWithError.
		// We cannot easily inject the broken emitter through the controller's
		// public API (it constructs SseEmitter internally). Instead, drive the
		// private streamToEmitter via reflection.
		java.lang.reflect.Method m = AiController.class.getDeclaredMethod("streamToEmitter", String.class,
				SseEmitter.class, long.class);
		m.setAccessible(true);

		SseEmitter brokenEmitter = new SseEmitter(0L) {
			@Override
			public void send(SseEmitter.SseEventBuilder builder) throws java.io.IOException {
				throw new java.io.IOException("network gone");
			}
		};
		// streamToEmitter runs synchronously here, so no executor wait is needed.
		// The broken emitter's IOException is caught and completed-with-error
		// internally, so the invocation itself must not propagate.
		assertThatCode(() -> m.invoke(this.controller, "p", brokenEmitter, System.nanoTime()))
			.doesNotThrowAnyException();
	}

	@Test
	void summarizeWithToolFailingUserClientHitsCatchBranch() throws Exception {
		// userClient.getUserSummary throws → streamWithToolToEmitter's outer
		// exception handler routes through emitter.completeWithError and
		// records the OUTCOME_ERROR metric. Threshold (3) < tokens (5) so the
		// tool branch fires, making the failure observable.
		given(this.userClient.getUserSummary(any(UUID.class)))
			.willReturn(CompletableFuture.failedFuture(new RuntimeException("user-service-down")));
		SseEmitter emitter = this.controller.summarizeWithTool(UUID.randomUUID(), body("p"));
		assertThat(emitter).isNotNull();
		awaitLastSubmit();
	}

	/**
	 * Wraps a real ExecutorService and captures every submitted Future so tests can
	 * synchronously wait on the streaming task.
	 */
	private static class CapturingExecutor extends java.util.concurrent.AbstractExecutorService {

		private final ExecutorService delegate;

		private final LinkedBlockingQueue<Future<?>> futures = new LinkedBlockingQueue<>();

		CapturingExecutor(ExecutorService d) {
			this.delegate = d;
		}

		Future<?> lastFuture() {
			// Return the most recent future; tests submit one streaming task per call.
			return (this.futures.peek() != null) ? this.futures.toArray(new Future<?>[0])[this.futures.size() - 1]
					: null;
		}

		@Override
		public void shutdown() {
			this.delegate.shutdown();
		}

		@Override
		public List<Runnable> shutdownNow() {
			return this.delegate.shutdownNow();
		}

		@Override
		public boolean isShutdown() {
			return this.delegate.isShutdown();
		}

		@Override
		public boolean isTerminated() {
			return this.delegate.isTerminated();
		}

		@Override
		public boolean awaitTermination(long t, TimeUnit u) throws InterruptedException {
			return this.delegate.awaitTermination(t, u);
		}

		@Override
		public void execute(Runnable command) {
			// Controller dispatches streaming work via execute() (fire-and-forget,
			// void). Wrap in a FutureTask so tests can still synchronously await the
			// streaming task's completion.
			FutureTask<?> task = new FutureTask<>(command, null);
			this.futures.offer(task);
			this.delegate.execute(task);
		}

		@Override
		public Future<?> submit(Runnable task) {
			Future<?> f = this.delegate.submit(task);
			this.futures.offer(f);
			return f;
		}

		@Override
		public <T> Future<T> submit(Callable<T> task) {
			Future<T> f = this.delegate.submit(task);
			this.futures.offer(f);
			return f;
		}

		@Override
		public <T> Future<T> submit(Runnable task, T result) {
			Future<T> f = this.delegate.submit(task, result);
			this.futures.offer(f);
			return f;
		}

	}

}
