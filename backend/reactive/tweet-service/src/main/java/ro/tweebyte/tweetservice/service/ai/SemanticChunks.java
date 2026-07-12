/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.service.ai;

/**
 * Deterministic semantic answer chunks for the AI-streaming benchmark payload.
 *
 * <p>
 * Replaces the prior {@code token_N} placeholder output so the benchmark is presentable
 * when inspected manually: the W0 scripted non-AI SSE baseline
 * ({@code /tweets/ai/.../mock-stream}) and the W1/W2 {@link MockStreamingChatModel} both
 * stream the words of a realistic microblogging activity-summary recap. The chunk at
 * stream position {@code idx} is {@code WORDS[idx mod WORDS.length]}, so any configured
 * token/chunk count ({@code tokens} for W0, {@code tokensPerResponse} for W1/W2) is
 * honoured exactly by deterministic cycling — the residency-control surface is unchanged,
 * only the payload text becomes report/demo-realistic.
 *
 * <p>
 * The async and reactive tweet-service stacks carry byte-for-byte identical copies so the
 * two stacks emit an identical chunk sequence for the same position.
 *
 * @author Andrei Zbarcea
 */
public final class SemanticChunks {

	private static final String[] WORDS = ("Here's a quick recap of your week on the timeline. "
			+ "Distributed systems and reactive programming dominated your posts, and the threads you opened "
			+ "drew thoughtful replies from the people you follow. You put real energy into the database "
			+ "connection pooling debate, traded ideas about Kubernetes operators, and shared an article on "
			+ "event-loop concurrency that clearly resonated with your audience. The weekend hiking trip added a "
			+ "warmer, more personal note to an otherwise technical stretch. You interacted most with the "
			+ "engineers arguing pool sizing and a couple of long-time mutuals. For what to post next, consider a "
			+ "short write-up on lessons from your reactive experiments, a few photos from the trail, or a "
			+ "practical comparison of operator patterns. Keep the momentum going.").split(" ");

	private SemanticChunks() {
	}

	/**
	 * Word chunk for stream position {@code idx}, cycling deterministically through the
	 * canonical recap vocabulary so any configured chunk count is reachable.
	 * @param idx the zero-based stream position
	 * @return the recap word at {@code idx mod WORDS.length}, with no trailing space
	 */
	public static String word(int idx) {
		return WORDS[Math.floorMod(idx, WORDS.length)];
	}

	/**
	 * {@link #word(int)} followed by a single trailing space, for chat-style
	 * concatenation where the streamed chunks are appended into one response body.
	 * @param idx the zero-based stream position
	 * @return the recap word at {@code idx} with a trailing space
	 */
	public static String token(int idx) {
		return word(idx) + " ";
	}

}
