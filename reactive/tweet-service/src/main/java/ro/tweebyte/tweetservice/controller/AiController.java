package ro.tweebyte.tweetservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import ro.tweebyte.tweetservice.client.UserClient;
import ro.tweebyte.tweetservice.metrics.AiLatencyMetrics;
import ro.tweebyte.tweetservice.metrics.PoolOccupancyMetrics;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping(path = "/tweets/ai")
@RequiredArgsConstructor
public class AiController {

    private static final String TOOL_EVENT_PREFIX = "[tool:";

    private final ChatClient chatClient;
    private final AiLatencyMetrics aiMetrics;
    private final PoolOccupancyMetrics poolMetrics;
    private final UserClient userClient;

    @Value("${app.ai.tool-call-after-tokens:75}")
    private int toolCallAfterTokens;

    @PostMapping(path = "/summarize", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> summarize(@RequestBody Map<String, String> body) {
        String prompt = body.getOrDefault("prompt", "");
        long start = System.nanoTime();
        AtomicLong firstTokenNanos = new AtomicLong(-1);
        AtomicLong lastTokenNanos = new AtomicLong(-1);

        return chatClient.prompt().user(prompt).stream().content()
                .doOnSubscribe(s -> poolMetrics.onSubscribe())
                .doOnNext(token -> {
                    long now = System.nanoTime();
                    if (firstTokenNanos.get() == -1L) {
                        firstTokenNanos.set(now);
                        aiMetrics.recordTtft(Duration.ofNanos(now - start));
                    } else if (lastTokenNanos.get() != -1L) {
                        aiMetrics.recordItl(Duration.ofNanos(now - lastTokenNanos.get()));
                    }
                    lastTokenNanos.set(now);
                })
                .map(token -> {
                    long serStart = System.nanoTime();
                    ServerSentEvent<String> event = ServerSentEvent.builder(token).build();
                    aiMetrics.recordSerialize(Duration.ofNanos(System.nanoTime() - serStart));
                    return event;
                })
                .doFinally(signalType -> {
                    aiMetrics.recordEndToEnd(Duration.ofNanos(System.nanoTime() - start),
                            outcomeFor(signalType));
                    poolMetrics.onTerminate(signalType);
                });
    }

    @PostMapping(path = "/buffered", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, String>> buffered(@RequestBody Map<String, String> body) {
        String prompt = body.getOrDefault("prompt", "");
        long start = System.nanoTime();
        return chatClient.prompt().user(prompt).stream().content()
                .reduce(new StringBuilder(), StringBuilder::append)
                .map(sb -> Map.of("response", sb.toString()))
                .doFinally(signalType -> aiMetrics.recordEndToEnd(
                        Duration.ofNanos(System.nanoTime() - start), outcomeFor(signalType)));
    }

    /**
     * W2 workload: mid-stream non-blocking tool call composed as a Mono.
     * Uses a SINGLE chatClient stream subscription per request to avoid
     * duplicate model work and uncorrelated generations. Tool event is
     * injected after {@code toolCallAfterTokens} via concatMap, draining
     * from the same upstream iterator.
     */
    @PostMapping(path = "/summarize-with-tool", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> summarizeWithTool(@RequestParam(value = "userId") UUID userId,
                                                           @RequestBody Map<String, String> body) {
        String prompt = body.getOrDefault("prompt", "");
        long start = System.nanoTime();
        AtomicLong firstTokenNanos = new AtomicLong(-1);
        AtomicLong lastTokenNanos = new AtomicLong(-1);
        AtomicInteger idx = new AtomicInteger();
        final int injectAt = toolCallAfterTokens;

        return chatClient.prompt().user(prompt).stream().content()
                .concatMap(token -> {
                    int position = idx.getAndIncrement();
                    if (position == injectAt) {
                        long toolStart = System.nanoTime();
                        return userClient.getUserSummary(userId)
                                .doOnNext(user -> aiMetrics.recordToolCall(
                                        Duration.ofNanos(System.nanoTime() - toolStart)))
                                .map(user -> TOOL_EVENT_PREFIX + user.getUserName() + "]")
                                .concatWith(Mono.just(token));
                    }
                    return Mono.just(token);
                })
                .doOnSubscribe(s -> poolMetrics.onSubscribe())
                .doOnNext(token -> {
                    long now = System.nanoTime();
                    if (firstTokenNanos.get() == -1L) {
                        firstTokenNanos.set(now);
                        aiMetrics.recordTtft(Duration.ofNanos(now - start));
                    } else if (lastTokenNanos.get() != -1L && !token.startsWith(TOOL_EVENT_PREFIX)) {
                        // Skip ITL accounting on the tool event itself: the gap from
                        // the previous model token to the tool event spans the tool
                        // call latency, which is already captured in tweebyte.ai.tool.
                        // Letting it bleed into tweebyte.ai.itl would inflate every
                        // W2 request's ITL p99 by the tool latency. The next model
                        // token's ITL is anchored against the tool event's emit
                        // time — that's a small Reactor scheduling gap and a
                        // reasonable proxy for "model resumed".
                        aiMetrics.recordItl(Duration.ofNanos(now - lastTokenNanos.get()));
                    }
                    lastTokenNanos.set(now);
                })
                .map(token -> ServerSentEvent.builder(token).build())
                .doFinally(signalType -> {
                    aiMetrics.recordEndToEnd(Duration.ofNanos(System.nanoTime() - start),
                            outcomeFor(signalType));
                    poolMetrics.onTerminate(signalType);
                });
    }

    @GetMapping(path = "/mock-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> mockStream(@RequestParam(defaultValue = "150") int tokens,
                                                    @RequestParam(defaultValue = "40") long itlMs) {
        return Flux.range(0, tokens)
                .concatMap(i -> Mono.delay(Duration.ofMillis(itlMs)).thenReturn("token_" + i))
                .map(token -> ServerSentEvent.builder(token).build());
    }

    private static String outcomeFor(SignalType signalType) {
        return switch (signalType) {
            case CANCEL -> AiLatencyMetrics.OUTCOME_CANCEL;
            case ON_ERROR -> AiLatencyMetrics.OUTCOME_ERROR;
            default -> AiLatencyMetrics.OUTCOME_SUCCESS;
        };
    }
}
