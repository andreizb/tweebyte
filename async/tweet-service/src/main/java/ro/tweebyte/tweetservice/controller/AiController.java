package ro.tweebyte.tweetservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ro.tweebyte.tweetservice.client.UserClient;
import ro.tweebyte.tweetservice.metrics.AiLatencyMetrics;
import ro.tweebyte.tweetservice.model.UserDto;

import java.io.IOException;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping(path = "/tweets/ai")
@RequiredArgsConstructor
public class AiController {

    private static final long NANO_SENTINEL = -1L;

    private final ChatClient chatClient;
    private final AiLatencyMetrics aiMetrics;
    private final UserClient userClient;
    @Qualifier(value = "executorService")
    private final ExecutorService executorService;

    @Value("${app.ai.tool-call-after-tokens:75}")
    private int toolCallAfterTokens;

    @PostMapping(path = "/summarize", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter summarize(@RequestBody Map<String, String> body) {
        String prompt = body.getOrDefault("prompt", "");
        SseEmitter emitter = new SseEmitter(0L);
        // Capture submit time on the request thread so the e2e timer includes
        // queue-wait inside the bounded executor — that wait IS the H1 failure
        // mode, and starting the timer inside the worker would understate it.
        long submitNanos = System.nanoTime();
        executorService.submit(() -> streamToEmitter(prompt, emitter, submitNanos));
        return emitter;
    }

    @PostMapping(path = "/buffered", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ResponseEntity<Map<String, String>>> buffered(@RequestBody Map<String, String> body) {
        String prompt = body.getOrDefault("prompt", "");
        long submitNanos = System.nanoTime();
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder collected = new StringBuilder();
            AtomicLong firstTokenNanos = new AtomicLong(NANO_SENTINEL);
            long end;
            String outcome = AiLatencyMetrics.OUTCOME_SUCCESS;
            try {
                chatClient.prompt().user(prompt).stream().content()
                        .toStream()
                        .forEach(token -> {
                            if (firstTokenNanos.get() == NANO_SENTINEL) {
                                firstTokenNanos.set(System.nanoTime());
                            }
                            collected.append(token);
                        });
            } catch (RuntimeException re) {
                outcome = AiLatencyMetrics.OUTCOME_ERROR;
                throw re;
            } finally {
                end = System.nanoTime();
                if (firstTokenNanos.get() != NANO_SENTINEL) {
                    aiMetrics.recordTtft(Duration.ofNanos(firstTokenNanos.get() - submitNanos));
                }
                aiMetrics.recordEndToEnd(Duration.ofNanos(end - submitNanos), outcome);
            }
            double ttftMs = firstTokenNanos.get() != NANO_SENTINEL
                    ? (firstTokenNanos.get() - submitNanos) / 1_000_000.0 : -1;
            return ResponseEntity.ok()
                    .header("X-Tweebyte-TTFT-Ms", String.format("%.3f", ttftMs))
                    .header("X-Tweebyte-E2E-Ms", String.format("%.3f", (end - submitNanos) / 1_000_000.0))
                    .body(Map.of("response", collected.toString()));
        }, executorService);
    }

    /**
     * W2 workload: mid-stream blocking tool call against user-service.
     * This is the H1 measurement surface: handler thread (custom executor) blocks
     * on UserClient.getUserSummary (HttpClient.send, blocking). Intentionally does
     * NOT use Spring AI's ChatClient.tools() orchestration — the manual pattern
     * isolates stack behavior from Spring AI's function-calling overhead.
     */
    @PostMapping(path = "/summarize-with-tool", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter summarizeWithTool(@RequestParam(value = "userId") UUID userId,
                                        @RequestBody Map<String, String> body) {
        String prompt = body.getOrDefault("prompt", "");
        SseEmitter emitter = new SseEmitter(0L);
        long submitNanos = System.nanoTime();
        executorService.submit(() -> streamWithToolToEmitter(prompt, userId, emitter, submitNanos));
        return emitter;
    }

    @GetMapping(path = "/mock-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter mockStream(@RequestParam(defaultValue = "150") int tokens,
                                 @RequestParam(defaultValue = "40") long itlMs) {
        SseEmitter emitter = new SseEmitter(0L);
        executorService.submit(() -> {
            try {
                // Delay BEFORE each token (including the first) so the W0 baseline
                // measures TTFT ≈ itlMs symmetrically with the reactive stack's
                // Flux.range(...).concatMap(i -> Mono.delay(itlMs)) shape. Without
                // this leading delay, async would emit token_0 immediately and
                // produce a TTFT measurement bias relative to reactive on the W0
                // baseline. E2E is unchanged either way (same total residency =
                // tokens × itlMs); the alignment only affects TTFT.
                for (int i = 0; i < tokens; i++) {
                    Thread.sleep(itlMs);
                    emitter.send(SseEmitter.event().data("token_" + i));
                }
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private void streamToEmitter(String prompt, SseEmitter emitter, long submitNanos) {
        AtomicLong lastTokenNanos = new AtomicLong(NANO_SENTINEL);
        AtomicLong firstTokenNanos = new AtomicLong(NANO_SENTINEL);
        String outcome = AiLatencyMetrics.OUTCOME_SUCCESS;
        try {
            chatClient.prompt().user(prompt).stream().content()
                    .toStream()
                    .forEach(token -> {
                        long now = System.nanoTime();
                        if (firstTokenNanos.get() == NANO_SENTINEL) {
                            firstTokenNanos.set(now);
                            aiMetrics.recordTtft(Duration.ofNanos(now - submitNanos));
                        } else if (lastTokenNanos.get() != NANO_SENTINEL) {
                            aiMetrics.recordItl(Duration.ofNanos(now - lastTokenNanos.get()));
                        }
                        lastTokenNanos.set(now);
                        long serStart = System.nanoTime();
                        try {
                            emitter.send(SseEmitter.event().data(token));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        aiMetrics.recordSerialize(Duration.ofNanos(System.nanoTime() - serStart));
                    });
            emitter.complete();
        } catch (Exception e) {
            outcome = AiLatencyMetrics.OUTCOME_ERROR;
            emitter.completeWithError(e);
        } finally {
            aiMetrics.recordEndToEnd(Duration.ofNanos(System.nanoTime() - submitNanos), outcome);
        }
    }

    private void streamWithToolToEmitter(String prompt, UUID userId, SseEmitter emitter,
                                         long submitNanos) {
        AtomicLong firstTokenNanos = new AtomicLong(NANO_SENTINEL);
        AtomicLong lastTokenNanos = new AtomicLong(NANO_SENTINEL);
        String outcome = AiLatencyMetrics.OUTCOME_SUCCESS;
        try {
            Iterator<String> stream = chatClient.prompt().user(prompt).stream().content()
                    .toStream().iterator();
            int emitted = 0;
            while (stream.hasNext() && emitted < toolCallAfterTokens) {
                String token = stream.next();
                long now = System.nanoTime();
                if (firstTokenNanos.get() == NANO_SENTINEL) {
                    firstTokenNanos.set(now);
                    aiMetrics.recordTtft(Duration.ofNanos(now - submitNanos));
                } else if (lastTokenNanos.get() != NANO_SENTINEL) {
                    aiMetrics.recordItl(Duration.ofNanos(now - lastTokenNanos.get()));
                }
                lastTokenNanos.set(now);
                long serStart = System.nanoTime();
                emitter.send(SseEmitter.event().data(token));
                aiMetrics.recordSerialize(Duration.ofNanos(System.nanoTime() - serStart));
                emitted++;
            }
            // Only fire the mid-stream tool call if the stream actually has a token
            // AT or AFTER the trigger position. Reactive's concatMap injects when
            // it observes the token at `position == injectAt`, so a stream of
            // exactly `toolCallAfterTokens` tokens (positions 0..injectAt-1) never
            // triggers the tool there. The `stream.hasNext()` guard keeps async on
            // the same boundary: otherwise async would emit a boundary-case tool
            // call while reactive would not.
            if (emitted >= toolCallAfterTokens && stream.hasNext()) {
                long toolStart = System.nanoTime();
                UserDto user = userClient.getUserSummary(userId);
                aiMetrics.recordToolCall(Duration.ofNanos(System.nanoTime() - toolStart));
                long serStart = System.nanoTime();
                emitter.send(SseEmitter.event().name("tool").data("[tool:" + user.getUserName() + "]"));
                aiMetrics.recordSerialize(Duration.ofNanos(System.nanoTime() - serStart));
                // Reset the ITL anchor so the FIRST post-tool token's inter-token
                // latency doesn't smuggle (toolCall + tool-event-serialize) into
                // the tweebyte.ai.itl distribution. The tool latency is already
                // captured in tweebyte.ai.tool; ITL must measure model-only gaps.
                lastTokenNanos.set(System.nanoTime());
            }
            while (stream.hasNext()) {
                String token = stream.next();
                long now = System.nanoTime();
                if (lastTokenNanos.get() != NANO_SENTINEL) {
                    aiMetrics.recordItl(Duration.ofNanos(now - lastTokenNanos.get()));
                }
                lastTokenNanos.set(now);
                long serStart = System.nanoTime();
                emitter.send(SseEmitter.event().data(token));
                aiMetrics.recordSerialize(Duration.ofNanos(System.nanoTime() - serStart));
            }
            emitter.complete();
        } catch (Exception e) {
            outcome = AiLatencyMetrics.OUTCOME_ERROR;
            emitter.completeWithError(e);
        } finally {
            aiMetrics.recordEndToEnd(Duration.ofNanos(System.nanoTime() - submitNanos), outcome);
        }
    }
}
