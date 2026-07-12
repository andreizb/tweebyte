/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.service.ai;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

class TimeoutChatModelTests {

	private static ChatResponse response(String text) {
		return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
	}

	private static Prompt prompt() {
		return new Prompt(new UserMessage("hi"));
	}

	@Test
	void streamPassesThroughWhenBackendRespondsInTime() {
		ChatModel delegate = Mockito.mock(ChatModel.class);
		given(delegate.stream(Mockito.any(Prompt.class)))
			.willReturn(Flux.just(response("token_0"), response("token_1")));

		TimeoutChatModel model = new TimeoutChatModel(delegate, Duration.ofSeconds(10));

		List<ChatResponse> chunks = model.stream(prompt()).collectList().block(Duration.ofSeconds(5));
		assertThat(chunks).hasSize(2);
	}

	@Test
	void streamFailsFastWhenBackendStalls() {
		ChatModel delegate = Mockito.mock(ChatModel.class);
		// Never emits, never completes: simulates a hung live backend.
		given(delegate.stream(Mockito.any(Prompt.class))).willReturn(Flux.never());

		TimeoutChatModel model = new TimeoutChatModel(delegate, Duration.ofMillis(50));

		assertThatThrownBy(() -> model.stream(prompt()).collectList().block(Duration.ofSeconds(5)))
			.getRootCause()
			.isInstanceOf(TimeoutException.class);
	}

	@Test
	void streamFailsFastWhenBackendStallsMidStream() {
		ChatModel delegate = Mockito.mock(ChatModel.class);
		given(delegate.stream(Mockito.any(Prompt.class)))
			.willReturn(Flux.just(response("token_0")).concatWith(Flux.never()));

		TimeoutChatModel model = new TimeoutChatModel(delegate, Duration.ofMillis(50));

		assertThatThrownBy(() -> model.stream(prompt()).collectList().block(Duration.ofSeconds(5)))
			.getRootCause()
			.isInstanceOf(TimeoutException.class);
	}

	@Test
	void callDelegatesUnchanged() {
		ChatModel delegate = Mockito.mock(ChatModel.class);
		ChatResponse expected = response("buffered");
		Prompt prompt = prompt();
		given(delegate.call(prompt)).willReturn(expected);

		TimeoutChatModel model = new TimeoutChatModel(delegate, Duration.ofSeconds(10));

		assertThat(model.call(prompt)).isSameAs(expected);
	}

	@Test
	void getDefaultOptionsDelegatesUnchanged() {
		ChatModel delegate = Mockito.mock(ChatModel.class);
		ChatOptions expected = ChatOptions.builder().build();
		given(delegate.getDefaultOptions()).willReturn(expected);

		TimeoutChatModel model = new TimeoutChatModel(delegate, Duration.ofSeconds(10));

		assertThat(model.getDefaultOptions()).isSameAs(expected);
	}

}
