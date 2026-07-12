/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import ro.tweebyte.tweetservice.client.UserClient;
import ro.tweebyte.tweetservice.metrics.AiLatencyMetrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;

/**
 * Covers the {@link AiController} error branches {@link AiControllerTests} does not
 * reach: {@code buffered}'s {@code catch (RuntimeException)} on a failing stream and
 * {@code mockStream}'s {@code catch (InterruptedException)} when its worker thread is
 * interrupted mid-sleep.
 */
class AiControllerBranchTests {

	private AiLatencyMetrics metrics;

	private UserClient userClient;

	@BeforeEach
	void setUp() {
		this.metrics = new AiLatencyMetrics(new SimpleMeterRegistry());
		this.userClient = mock(UserClient.class);
	}

	@AfterEach
	void tearDown() {
		// no-op; per-test executors are shut down in their own tests.
	}

	@Test
	void buffered_streamErrorHitsRuntimeExceptionCatchBranch() {
		// A model whose stream errors makes the buffered collector's forEach throw,
		// exercising the catch(RuntimeException) outcome=ERROR branch + the e2e
		// error-outcome metric, and surfacing exceptionally on the future.
		ChatModel failingModel = prompt -> {
			throw new IllegalStateException("model exploded");
		};
		ExecutorService exec = Executors.newSingleThreadExecutor();
		AiController controller = new AiController(ChatClient.create(failingModel), this.metrics, this.userClient, exec);
		ReflectionTestUtils.setField(controller, "toolCallAfterTokens", 3);

		Throwable ex = catchThrowable(() -> controller.buffered(body("hi")).get(10, TimeUnit.SECONDS));
		assertThat(ex).isNotNull();
		assertThat(ex.getCause()).isInstanceOf(RuntimeException.class);

		exec.shutdownNow();
	}

	@Test
	void buffered_streamFluxErrorHitsRuntimeExceptionCatchBranch() throws Exception {
		// Same branch reached via a deferred Flux.error rather than an eager throw.
		ChatModel fluxErrorModel = new ChatModel() {
			@Override
			public ChatResponse call(Prompt prompt) {
				return new ChatResponse(List.of(new Generation(new org.springframework.ai.chat.messages.AssistantMessage(""),
						ChatGenerationMetadata.NULL)));
			}

			@Override
			public Flux<ChatResponse> stream(Prompt prompt) {
				return Flux.error(new IllegalStateException("stream boom"));
			}
		};
		ExecutorService exec = Executors.newSingleThreadExecutor();
		AiController controller = new AiController(ChatClient.create(fluxErrorModel), this.metrics, this.userClient,
				exec);
		ReflectionTestUtils.setField(controller, "toolCallAfterTokens", 3);

		Throwable ex = catchThrowable(() -> controller.buffered(body("hi")).get(10, TimeUnit.SECONDS));
		assertThat(ex).isNotNull();

		exec.shutdownNow();
		assertThat(exec.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
	}

	@Test
	void mockStream_interruptedDuringSleepHitsInterruptedBranch() throws Exception {
		// A long per-token delay parks the worker in Thread.sleep; interrupting the
		// executor (shutdownNow) makes the sleep throw InterruptedException, routing
		// through the catch(InterruptedException) branch (interrupt + completeWithError).
		CountDownLatch started = new CountDownLatch(1);
		ExecutorService exec = new java.util.concurrent.ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
				new java.util.concurrent.LinkedBlockingQueue<>()) {
			@Override
			public void execute(Runnable command) {
				super.execute(() -> {
					started.countDown();
					command.run();
				});
			}
		};
		AiController controller = new AiController(mock(ChatClient.class), this.metrics, this.userClient, exec);

		SseEmitter emitter = controller.mockStream(5, 60_000L);
		assertThat(emitter).isNotNull();
		assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
		// Give the worker a moment to enter Thread.sleep before interrupting.
		Thread.sleep(100);
		exec.shutdownNow();
		assertThat(exec.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
	}

	private static Map<String, String> body(String prompt) {
		Map<String, String> m = new HashMap<>();
		m.put("prompt", prompt);
		return m;
	}

}
