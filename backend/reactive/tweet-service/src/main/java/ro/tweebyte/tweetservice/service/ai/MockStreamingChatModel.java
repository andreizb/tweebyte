/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.service.ai;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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

/**
 * Spring AI {@link ChatModel} backed by calibrated synthetic distributions. Log-normal
 * TTFT + gamma ITL via Apache Commons Math.
 *
 * <p>
 * Selected when {@code app.ai.backend=mock}. Otherwise Spring AI auto-config provides the
 * OpenAI-compatible {@code OpenAiChatModel} pointed at LM Studio.
 *
 * @author Andrei Zbarcea
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

	public MockStreamingChatModel(double ttftMeanMs, double ttftLogSigma, double itlMeanMs, double itlGammaShape,
			int tokensPerResponse) {
		this(ttftMeanMs, ttftLogSigma, itlMeanMs, itlGammaShape, 0.0, tokensPerResponse);
	}

	public MockStreamingChatModel(double ttftMeanMs, double ttftLogSigma, double itlMeanMs, double itlGammaShape,
			double itlPBurst, int tokensPerResponse) {
		this.ttftMeanMs = ttftMeanMs;
		this.ttftLogSigma = ttftLogSigma;
		this.itlMeanMs = itlMeanMs;
		this.itlGammaShape = itlGammaShape;
		this.itlPBurst = clampPBurst(itlPBurst);
		this.tokensPerResponse = tokensPerResponse;
		double mu = Math.log(ttftMeanMs) - 0.5 * ttftLogSigma * ttftLogSigma;
		double sigma = Math.max(1e-6, ttftLogSigma);
		// When the burst probability is positive, the gamma fit spans only the
		// gap-mode subset of inter-token latencies. Zero inflation in the streaming
		// path then restores the burst-mode mass, so the mean response time matches
		// the empirical bimodal observation.
		double scale = itlMeanMs / Math.max(1e-6, itlGammaShape);
		this.ttftDistTL = ThreadLocal.withInitial(
				() -> new LogNormalDistribution(new Well19937c(ThreadLocalRandom.current().nextLong()), mu, sigma));
		this.itlDistTL = ThreadLocal
			.withInitial(() -> new GammaDistribution(new Well19937c(ThreadLocalRandom.current().nextLong()),
					itlGammaShape, scale));
	}

	private static double clampPBurst(double v) {
		if (Double.isNaN(v) || v < 0.0) {
			return 0.0;
		}
		if (v > 1.0) {
			return 1.0;
		}
		return v;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		StringBuilder full = new StringBuilder();
		stream(prompt).toStream().forEach(chunk -> {
			AssistantMessage msg = chunk.getResult().getOutput();
			if (msg.getText() != null) {
				full.append(msg.getText());
			}
		});
		Generation g = new Generation(new AssistantMessage(full.toString()),
				ChatGenerationMetadata.builder().finishReason("stop").build());
		return new ChatResponse(List.of(g));
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		long ttft = Math.max(1L, (long) this.ttftDistTL.get().sample());
		return Mono.delay(Duration.ofMillis(ttft)).thenMany(Flux.range(0, this.tokensPerResponse)).concatMap(i -> {
			if (i == 0) {
				return Mono.just(asDeltaResponse(SemanticChunks.token(i), i, this.tokensPerResponse));
			}
			// With probability equal to the burst factor, emit an intra-burst token
			// that has no inter-token delay. Otherwise sample the gap-mode gamma fit.
			// A burst factor of zero — the default when a calibration file omits the
			// burst probability — collapses this back to pure gamma and preserves the
			// prior behaviour.
			if (this.itlPBurst > 0.0 && ThreadLocalRandom.current().nextDouble() < this.itlPBurst) {
				return Mono.just(asDeltaResponse(SemanticChunks.token(i), i, this.tokensPerResponse));
			}
			long itl = Math.max(1L, (long) this.itlDistTL.get().sample());
			return Mono.delay(Duration.ofMillis(itl))
				.thenReturn(asDeltaResponse(SemanticChunks.token(i), i, this.tokensPerResponse));
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

	public double getTtftMeanMs() {
		return this.ttftMeanMs;
	}

	public double getTtftLogSigma() {
		return this.ttftLogSigma;
	}

	public double getItlMeanMs() {
		return this.itlMeanMs;
	}

	public double getItlGammaShape() {
		return this.itlGammaShape;
	}

	public double getItlPBurst() {
		return this.itlPBurst;
	}

	public int getTokensPerResponse() {
		return this.tokensPerResponse;
	}

}
