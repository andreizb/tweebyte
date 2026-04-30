package ro.tweebyte.tweetservice.service.ai;

import org.apache.commons.math3.distribution.GammaDistribution;
import org.apache.commons.math3.distribution.LogNormalDistribution;
import org.apache.commons.math3.random.Well19937c;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spring AI {@link ChatModel} backed by calibrated synthetic distributions.
 * Log-normal TTFT + gamma ITL via Apache Commons Math.
 *
 * <p>Selected when {@code app.ai.backend=mock}. Otherwise Spring AI auto-config
 * provides the OpenAI-compatible {@code OpenAiChatModel} pointed at LM Studio.
 */
public class MockStreamingChatModel implements ChatModel {

    private final double ttftMeanMs;
    private final double ttftLogSigma;
    private final double itlMeanMs;
    private final double itlGammaShape;
    private final double itlPBurst;
    private final int tokensPerResponse;
    // ThreadLocal distributions to avoid serialising every sample on the
    // RandomGenerator's lock under H1 load — see async stack for the rationale.
    private final ThreadLocal<LogNormalDistribution> ttftDistTL;
    private final ThreadLocal<GammaDistribution> itlDistTL;

    public MockStreamingChatModel(double ttftMeanMs, double ttftLogSigma,
                                  double itlMeanMs, double itlGammaShape,
                                  int tokensPerResponse) {
        this(ttftMeanMs, ttftLogSigma, itlMeanMs, itlGammaShape, 0.0, tokensPerResponse);
    }

    public MockStreamingChatModel(double ttftMeanMs, double ttftLogSigma,
                                  double itlMeanMs, double itlGammaShape,
                                  double itlPBurst,
                                  int tokensPerResponse) {
        this.ttftMeanMs = ttftMeanMs;
        this.ttftLogSigma = ttftLogSigma;
        this.itlMeanMs = itlMeanMs;
        this.itlGammaShape = itlGammaShape;
        this.itlPBurst = clampPBurst(itlPBurst);
        this.tokensPerResponse = tokensPerResponse;
        double mu = Math.log(ttftMeanMs) - 0.5 * ttftLogSigma * ttftLogSigma;
        double sigma = Math.max(1e-6, ttftLogSigma);
        // Gamma over the gap-mode subset (ITL ≥ 0.1 ms) when itlPBurst > 0;
        // zero-inflation in stream() restores burst-mode mass so E[response]
        // matches the empirical bimodal observation.
        double scale = itlMeanMs / Math.max(1e-6, itlGammaShape);
        this.ttftDistTL = ThreadLocal.withInitial(() ->
                new LogNormalDistribution(new Well19937c(ThreadLocalRandom.current().nextLong()), mu, sigma));
        this.itlDistTL = ThreadLocal.withInitial(() ->
                new GammaDistribution(new Well19937c(ThreadLocalRandom.current().nextLong()), itlGammaShape, scale));
    }

    private static double clampPBurst(double v) {
        if (Double.isNaN(v) || v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        StringBuilder full = new StringBuilder();
        stream(prompt).toStream().forEach(chunk -> {
            AssistantMessage msg = chunk.getResult().getOutput();
            if (msg != null && msg.getText() != null) full.append(msg.getText());
        });
        Generation g = new Generation(new AssistantMessage(full.toString()),
                ChatGenerationMetadata.builder().finishReason("stop").build());
        return new ChatResponse(List.of(g));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        long ttft = Math.max(1L, (long) ttftDistTL.get().sample());
        return Mono.delay(Duration.ofMillis(ttft))
                .thenMany(Flux.range(0, tokensPerResponse))
                .concatMap(i -> {
                    if (i == 0) {
                        return Mono.just(asDeltaResponse("token_" + i + " ", i, tokensPerResponse));
                    }
                    // Zero-inflated: with prob itlPBurst, intra-burst zero-delay token;
                    // otherwise sample from the gap-mode gamma fit. itlPBurst=0 (the
                    // default for calibration files without `p_burst`) collapses
                    // this back to pure gamma — preserving prior behaviour.
                    if (itlPBurst > 0.0 && ThreadLocalRandom.current().nextDouble() < itlPBurst) {
                        return Mono.just(asDeltaResponse("token_" + i + " ", i, tokensPerResponse));
                    }
                    long itl = Math.max(1L, (long) itlDistTL.get().sample());
                    return Mono.delay(Duration.ofMillis(itl))
                            .thenReturn(asDeltaResponse("token_" + i + " ", i, tokensPerResponse));
                });
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return ChatOptions.builder().build();
    }

    private ChatResponse asDeltaResponse(String content, int idx, int total) {
        String finishReason = (idx == total - 1) ? "stop" : null;
        Generation g = new Generation(new AssistantMessage(content),
                ChatGenerationMetadata.builder().finishReason(finishReason).build());
        return new ChatResponse(List.of(g));
    }

    public double getTtftMeanMs() { return ttftMeanMs; }
    public double getTtftLogSigma() { return ttftLogSigma; }
    public double getItlMeanMs() { return itlMeanMs; }
    public double getItlGammaShape() { return itlGammaShape; }
    public double getItlPBurst() { return itlPBurst; }
    public int getTokensPerResponse() { return tokensPerResponse; }
}
