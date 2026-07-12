/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.service.ai;

import java.time.Duration;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * Decorates a live {@link ChatModel} (Spring AI's {@code OpenAiChatModel}) with a
 * per-signal stream deadline so a stalled AI backend fails fast instead of hanging the
 * request forever.
 *
 * <p>
 * The {@link #stream(Prompt)} deadline is a wall-clock idle timeout: it fires if the
 * first {@link ChatResponse} (or completion / error) does not arrive within the window
 * — covering a hung TCP connect and a stalled first token — and again between any two
 * tokens, covering a backend that stops emitting mid-stream. {@link #call(Prompt)} is
 * the blocking aggregation Spring AI builds on the same stream, so it inherits the same
 * deadline transparently.
 *
 * <p>
 * This decorator is wired only when {@code app.ai.backend=live}; the benchmarked
 * {@link MockStreamingChatModel} path never sees it, so the measured mock stream stays
 * byte-for-byte unchanged. Mirrors the async-stack {@code TimeoutChatModel}.
 *
 * @author Andrei Zbarcea
 */
public class TimeoutChatModel implements ChatModel {

	private final ChatModel delegate;

	private final Duration streamTimeout;

	public TimeoutChatModel(ChatModel delegate, Duration streamTimeout) {
		this.delegate = delegate;
		this.streamTimeout = streamTimeout;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		return this.delegate.call(prompt);
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		return this.delegate.stream(prompt).timeout(this.streamTimeout);
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return this.delegate.getDefaultOptions();
	}

}
