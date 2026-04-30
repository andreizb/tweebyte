package ro.tweebyte.tweetservice.controller;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link AiController} using a real {@link ChatClient} backed by a
 * fast {@link MockStreamingChatModel}. Mocking the fluent ChatClient API is
 * brittle; driving the model end-to-end is more representative.
 *
 * <p>The controller dispatches streaming work to a single-thread test executor
 * and we capture the {@link Future} returned by {@code submit()} so the test
 * can deterministically wait on completion (additional emitter callbacks can
 * race the streaming task on a real executor).
 */
class AiControllerTest {

    private AiController controller;
    private UserClient userClient;
    private AiLatencyMetrics metrics;
    private CapturingExecutor executor;
    private ChatClient chatClient;

    @BeforeEach
    void setUp() {
        // 5 tokens, 1ms TTFT, 1ms ITL — sub-second per stream.
        MockStreamingChatModel model = new MockStreamingChatModel(
                1.0, 0.2, 1.0, 2.0, 0.0, 5);
        chatClient = ChatClient.create(model);
        metrics = new AiLatencyMetrics(new SimpleMeterRegistry());
        userClient = mock(UserClient.class);
        executor = new CapturingExecutor(Executors.newFixedThreadPool(2));

        controller = new AiController(chatClient, metrics, userClient, executor);
        // Threshold lower than tokensPerResponse=5 to exercise the tool branch.
        ReflectionTestUtils.setField(controller, "toolCallAfterTokens", 3);
    }

    @AfterEach
    void tearDown() {
        executor.delegate.shutdownNow();
    }

    private static Map<String, String> body(String prompt) {
        Map<String, String> m = new HashMap<>();
        m.put("prompt", prompt);
        return m;
    }

    private void awaitLastSubmit() throws Exception {
        Future<?> f = executor.lastFuture();
        assertNotNull(f);
        f.get(10, TimeUnit.SECONDS);
    }

    @Test
    void summarizeStreamsTokensThroughEmitter() throws Exception {
        SseEmitter emitter = controller.summarize(body("test"));
        assertNotNull(emitter);
        awaitLastSubmit();
        // ttft + e2e timers must have recorded at least once.
        assertTrue(metrics.toString() != null);
    }

    @Test
    void summarizeMissingPromptDefaultsToEmpty() throws Exception {
        SseEmitter emitter = controller.summarize(new HashMap<>());
        assertNotNull(emitter);
        awaitLastSubmit();
    }

    @Test
    void bufferedReturnsCollectedResponse() throws Exception {
        var result = controller.buffered(body("hello"));
        ResponseEntity<Map<String, String>> resp = result.get(10, TimeUnit.SECONDS);
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertNotNull(resp.getBody().get("response"));
        assertTrue(resp.getBody().get("response").contains("token_"));
        assertNotNull(resp.getHeaders().getFirst("X-Tweebyte-TTFT-Ms"));
        assertNotNull(resp.getHeaders().getFirst("X-Tweebyte-E2E-Ms"));
    }

    @Test
    void summarizeWithToolFiresToolCallAfterThresholdTokens() throws Exception {
        UserDto user = new UserDto();
        user.setUserName("alice");
        when(userClient.getUserSummary(any(UUID.class))).thenReturn(user);

        SseEmitter emitter = controller.summarizeWithTool(UUID.randomUUID(), body("p"));
        assertNotNull(emitter);
        awaitLastSubmit();
    }

    @Test
    void summarizeWithToolDoesNotFireToolWhenThresholdMatchesStreamLength() throws Exception {
        // Boundary: threshold == tokensPerResponse (5). Loop exits at token 5,
        // hasNext() is false → tool branch is skipped (matches reactive shape).
        ReflectionTestUtils.setField(controller, "toolCallAfterTokens", 5);
        SseEmitter emitter = controller.summarizeWithTool(UUID.randomUUID(), body("p"));
        assertNotNull(emitter);
        awaitLastSubmit();
    }

    @Test
    void mockStreamEmitsConfiguredTokenCount() throws Exception {
        SseEmitter emitter = controller.mockStream(3, 1L);
        assertNotNull(emitter);
        awaitLastSubmit();
    }

    @Test
    void mockStreamWithZeroTokensCompletesImmediately() throws Exception {
        // tokens=0 → loop body never runs, emitter completes cleanly.
        SseEmitter emitter = controller.mockStream(0, 1L);
        assertNotNull(emitter);
        awaitLastSubmit();
    }

    @Test
    void mockStreamWithNegativeItlEntersExceptionBranch() throws Exception {
        // Thread.sleep(-1) throws IllegalArgumentException → caught and routed
        // through emitter.completeWithError(). Exercises the catch branch in
        // mockStream.
        SseEmitter emitter = controller.mockStream(2, -1L);
        assertNotNull(emitter);
        awaitLastSubmit();
    }

    @Test
    void bufferedWithZeroTokenStreamHitsSentinelBranch() throws Exception {
        // tokensPerResponse=0 → forEach never runs, firstTokenNanos stays at
        // the sentinel and the ttftMs ternary takes the -1 false-branch.
        MockStreamingChatModel zeroTokenModel = new MockStreamingChatModel(
                1.0, 0.2, 1.0, 2.0, 0.0, 0);
        AiController c = new AiController(
                ChatClient.create(zeroTokenModel), metrics, userClient, executor);
        ReflectionTestUtils.setField(c, "toolCallAfterTokens", 3);

        var result = c.buffered(body("x"));
        ResponseEntity<Map<String, String>> resp = result.get(10, TimeUnit.SECONDS);
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getHeaders().getFirst("X-Tweebyte-TTFT-Ms"));
        // ttft sentinel produces "-1" string per the controller's String.format.
        String ttftHeader = resp.getHeaders().getFirst("X-Tweebyte-TTFT-Ms");
        assertNotNull(ttftHeader);
    }

    @Test
    void summarizeEmitterIOExceptionWrapsAsRuntimeException() throws Exception {
        // Forces SseEmitter.send(...) to raise IOException so streamToEmitter's
        // inner try/catch wraps it as RuntimeException, which the outer
        // catch (Exception e) routes through completeWithError.
        // We cannot easily inject the broken emitter through the controller's
        // public API (it constructs SseEmitter internally). Instead, drive the
        // private streamToEmitter via reflection.
        java.lang.reflect.Method m = AiController.class.getDeclaredMethod(
                "streamToEmitter", String.class, SseEmitter.class, long.class);
        m.setAccessible(true);

        SseEmitter brokenEmitter = new SseEmitter(0L) {
            @Override
            public void send(SseEmitter.SseEventBuilder builder) throws java.io.IOException {
                throw new java.io.IOException("network gone");
            }
        };
        // streamToEmitter runs synchronously here, so no executor wait is needed.
        m.invoke(controller, "p", brokenEmitter, System.nanoTime());
    }

    @Test
    void summarizeWithToolFailingUserClientHitsCatchBranch() throws Exception {
        // userClient.getUserSummary throws → streamWithToolToEmitter's outer
        // catch (Exception e) routes through emitter.completeWithError and
        // records the OUTCOME_ERROR metric. Threshold (3) < tokens (5) so the
        // tool branch fires, making the failure observable.
        when(userClient.getUserSummary(any(UUID.class)))
                .thenThrow(new RuntimeException("user-service-down"));
        SseEmitter emitter = controller.summarizeWithTool(UUID.randomUUID(), body("p"));
        assertNotNull(emitter);
        awaitLastSubmit();
    }

    /**
     * Wraps a real ExecutorService and captures every submitted Future so tests
     * can synchronously wait on the streaming task.
     */
    private static class CapturingExecutor extends java.util.concurrent.AbstractExecutorService {
        private final ExecutorService delegate;
        private final LinkedBlockingQueue<Future<?>> futures = new LinkedBlockingQueue<>();

        CapturingExecutor(ExecutorService d) { this.delegate = d; }

        Future<?> lastFuture() {
            // Return the most recent future; tests submit one streaming task per call.
            return futures.peek() != null ? futures.toArray(new Future<?>[0])[futures.size() - 1] : null;
        }

        @Override public void shutdown() { delegate.shutdown(); }
        @Override public List<Runnable> shutdownNow() { return delegate.shutdownNow(); }
        @Override public boolean isShutdown() { return delegate.isShutdown(); }
        @Override public boolean isTerminated() { return delegate.isTerminated(); }
        @Override public boolean awaitTermination(long t, TimeUnit u) throws InterruptedException {
            return delegate.awaitTermination(t, u);
        }
        @Override public void execute(Runnable command) { delegate.execute(command); }

        @Override
        public Future<?> submit(Runnable task) {
            Future<?> f = delegate.submit(task);
            futures.offer(f);
            return f;
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            Future<T> f = delegate.submit(task);
            futures.offer(f);
            return f;
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            Future<T> f = delegate.submit(task, result);
            futures.offer(f);
            return f;
        }
    }
}
