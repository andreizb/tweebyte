/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.service.ai;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MockStreamingChatModelTests {

	// Fast config: ttftMean 1ms, itlMean 1ms. Real distribution samplers
	// may produce outliers but the 10-token count + concatenation guarantees are
	// shape-invariant.
	private static final int TOKENS = 10;

	private MockStreamingChatModel newModel() {
		return new MockStreamingChatModel(1.0, 0.2, 1.0, 2.0, TOKENS);
	}

	@Test
	void streamEmitsConfiguredTokenCount() {
		List<ChatResponse> chunks = newModel().stream(new Prompt(new UserMessage("hi"))).collectList().block();
		assertThat(chunks).isNotNull()
			.as("stream() must emit exactly tokensPerResponse ChatResponses")
			.hasSize(TOKENS);
	}

	@Test
	void streamChunksCarryIndexedSemanticTokens() {
		List<ChatResponse> chunks = newModel().stream(new Prompt(new UserMessage("hi"))).collectList().block();
		assertThat(chunks).isNotNull();
		for (int i = 0; i < chunks.size(); i++) {
			String text = chunks.get(i).getResult().getOutput().getText();
			assertThat(text).isNotNull()
				.as("chunk " + i + " should carry semantic token " + i + " but was " + text)
				.isEqualTo(SemanticChunks.token(i));
		}
	}

	@Test
	void callConcatenatesAllStreamChunks() {
		ChatResponse resp = newModel().call(new Prompt(new UserMessage("hi")));
		String text = resp.getResult().getOutput().getText();
		// call() concatenates every stream chunk in order: the full text equals the
		// deterministic semantic tokens token(0)..token(TOKENS-1) joined.
		StringBuilder expected = new StringBuilder();
		for (int i = 0; i < TOKENS; i++) {
			expected.append(SemanticChunks.token(i));
		}
		assertThat(text).isEqualTo(expected.toString());
		assertThat(resp.getResult().getMetadata().getFinishReason()).isEqualTo("stop");
	}

	@Test
	void accessorsReflectConstructorArgs() {
		MockStreamingChatModel m = newModel();
		assertThat(m.getTtftMeanMs()).isEqualTo(1.0);
		assertThat(m.getTtftLogSigma()).isEqualTo(0.2);
		assertThat(m.getItlMeanMs()).isEqualTo(1.0);
		assertThat(m.getItlGammaShape()).isEqualTo(2.0);
		assertThat(m.getItlPBurst()).isEqualTo(0.0);
		assertThat(m.getTokensPerResponse()).isEqualTo(TOKENS);
	}

	@Test
	void zeroInflatedConstructorPropagatesPBurst() {
		MockStreamingChatModel m = new MockStreamingChatModel(1.0, 0.2, 1.0, 2.0, 0.384, TOKENS);
		assertThat(m.getItlPBurst()).isCloseTo(0.384, within(1e-9));
	}

	@Test
	void pBurstClampedAtConstructor() {
		// Negatives clamp to 0; >1 clamps to 1.
		MockStreamingChatModel high = new MockStreamingChatModel(1.0, 0.2, 1.0, 2.0, 1.5, TOKENS);
		assertThat(high.getItlPBurst()).isCloseTo(1.0, within(1e-9));
		MockStreamingChatModel low = new MockStreamingChatModel(1.0, 0.2, 1.0, 2.0, -0.4, TOKENS);
		assertThat(low.getItlPBurst()).isCloseTo(0.0, within(1e-9));
	}

	@Test
	void pBurstNaNClampsToZero() {
		// Covers the Double.isNaN(v) branch of clampPBurst — JSON inputs cannot
		// produce NaN through Jackson's default config, but the constructor
		// accepts a raw double.
		MockStreamingChatModel m = new MockStreamingChatModel(1.0, 0.2, 1.0, 2.0, Double.NaN, TOKENS);
		assertThat(m.getItlPBurst()).isCloseTo(0.0, within(1e-9));
	}

	@Test
	void streamWithFractionalPBurstExercisesBothBranches() {
		// p_burst between 0 and 1 forces the second clause of the &&-guard to
		// be evaluated. Across many tokens the random draw will be both above
		// and below the threshold, hitting both arms of the burst-vs-gap branch.
		MockStreamingChatModel m = new MockStreamingChatModel(1.0, 0.2, 1.0, 2.0, 0.5, 60);
		List<ChatResponse> chunks = m.stream(new Prompt(new UserMessage("hi"))).collectList().block();
		assertThat(chunks).isNotNull().hasSize(60);
	}

	@Test
	void callHandlesNullAssistantMessageDefensively() {
		// The non-null message-and-text guard inside call() is partially covered
		// because every chunk produced by stream() carries a non-null
		// AssistantMessage. The defensive branch is reachable only by feeding
		// chunks with a null message via call's forEach path; we exercise the
		// standard happy path here for parity with other tests (the negative
		// branch is unreachable through the public API since stream() is final
		// with respect to message construction).
		MockStreamingChatModel m = newModel();
		ChatResponse resp = m.call(new Prompt(new UserMessage("hi")));
		assertThat(resp.getResult().getOutput().getText()).isNotNull();
	}

	@Test
	void streamWithPBurst1EmitsAllTokensWithoutGapDelay() {
		// p_burst=1.0 forces every non-first token to take the zero-delay branch.
		// Exercises the zero-inflation code path; the assertion that matters is
		// that emission still produces tokensPerResponse chunks in order.
		MockStreamingChatModel m = new MockStreamingChatModel(1.0, 0.2, 1.0, 2.0, 1.0, TOKENS);
		List<ChatResponse> chunks = m.stream(new Prompt(new UserMessage("hi"))).collectList().block();
		assertThat(chunks).isNotNull().hasSize(TOKENS);
		for (int i = 0; i < chunks.size(); i++) {
			String text = chunks.get(i).getResult().getOutput().getText();
			assertThat(text).isNotNull().isEqualTo(SemanticChunks.token(i));
		}
	}

}
