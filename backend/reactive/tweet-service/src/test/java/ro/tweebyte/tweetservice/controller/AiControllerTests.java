/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.controller;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import ro.tweebyte.tweetservice.client.UserClient;
import ro.tweebyte.tweetservice.metrics.AiLatencyMetrics;
import ro.tweebyte.tweetservice.metrics.PoolOccupancyMetrics;
import ro.tweebyte.tweetservice.model.UserDto;
import ro.tweebyte.tweetservice.service.ai.SemanticChunks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = AiController.class,
		excludeAutoConfiguration = { ReactiveSecurityAutoConfiguration.class,
				ReactiveUserDetailsServiceAutoConfiguration.class })
@Import(AiControllerTests.MockConfig.class)
class AiControllerTests {

	private static final UUID USER_ID = UUID.randomUUID();

	private static final UUID CONVERSATION_ID = UUID.randomUUID();

	private static final String AI_BASE = "/tweets/ai/users/" + USER_ID + "/conversations/" + CONVERSATION_ID;

	@Autowired
	private WebTestClient webTestClient;

	@Autowired
	private ChatClient chatClient;

	@MockBean
	private AiLatencyMetrics aiMetrics;

	@MockBean
	private PoolOccupancyMetrics poolMetrics;

	@MockBean
	private UserClient userClient;

	@BeforeEach
	void resetChatClient() {
		Mockito.reset(this.chatClient);
		stubChatClientStream(Flux.just("a", "b", "c"));
	}

	@SuppressWarnings("unchecked")
	private void stubChatClientStream(Flux<String> tokens) {
		ChatClient.ChatClientRequestSpec spec = Mockito.mock(ChatClient.ChatClientRequestSpec.class);
		ChatClient.StreamResponseSpec stream = Mockito.mock(ChatClient.StreamResponseSpec.class);
		given(this.chatClient.prompt()).willReturn(spec);
		given(spec.user(any(String.class))).willReturn(spec);
		given(spec.stream()).willReturn(stream);
		given(stream.content()).willReturn(tokens);
	}

	@Test
	void summarizeStreamsTokensAndRecordsMetrics() {
		stubChatClientStream(Flux.just("hello", "world", "!"));

		this.webTestClient.post()
			.uri(AI_BASE + "/summarize")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(Map.of("prompt", "hi"))
			.accept(MediaType.TEXT_EVENT_STREAM)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.value(body -> {
				assertThat(body).isNotNull();
				assertThat(body).contains("hello");
				assertThat(body).contains("world");
			});

		// ttft + at least one itl + serialize calls + e2e success
		verify(this.aiMetrics, atLeastOnce()).recordTtft(any(Duration.class));
		verify(this.aiMetrics, atLeastOnce()).recordSerialize(any(Duration.class));
		verify(this.aiMetrics, atLeastOnce()).recordEndToEnd(any(Duration.class), any(String.class));
		verify(this.poolMetrics).onSubscribe();
		verify(this.poolMetrics, atLeastOnce()).onTerminate(any());
	}

	@Test
	void summarizeWithMissingPromptDefaultsToEmpty() {
		stubChatClientStream(Flux.just("x"));
		this.webTestClient.post()
			.uri(AI_BASE + "/summarize")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(Map.of())
			.accept(MediaType.TEXT_EVENT_STREAM)
			.exchange()
			.expectStatus()
			.isOk();
	}

	@Test
	void summarizeWithSingleTokenSkipsItlBranch() {
		stubChatClientStream(Flux.just("only"));
		this.webTestClient.post()
			.uri(AI_BASE + "/summarize")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(Map.of("prompt", "x"))
			.accept(MediaType.TEXT_EVENT_STREAM)
			.exchange()
			.expectStatus()
			.isOk();
		verify(this.aiMetrics, atLeastOnce()).recordTtft(any(Duration.class));
	}

	@Test
	void summarizeReportsErrorOutcomeOnUpstreamError() {
		stubChatClientStream(Flux.error(new RuntimeException("boom")));

		this.webTestClient.post()
			.uri(AI_BASE + "/summarize")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(Map.of("prompt", "x"))
			.accept(MediaType.TEXT_EVENT_STREAM)
			.exchange()
			.expectStatus()
			.is5xxServerError();

		ArgumentCaptor<String> outcome = ArgumentCaptor.forClass(String.class);
		verify(this.aiMetrics, atLeastOnce()).recordEndToEnd(any(Duration.class), outcome.capture());
		assertThat(outcome.getAllValues())
			.as("expected at least one error outcome record, got " + outcome.getAllValues())
			.contains(AiLatencyMetrics.OUTCOME_ERROR);
	}

	@Test
	void bufferedAggregatesTokensAndReturnsJson() {
		stubChatClientStream(Flux.just("foo", "bar"));

		this.webTestClient.post()
			.uri(AI_BASE + "/buffered")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(Map.of("prompt", "hi"))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.response")
			.isEqualTo("foobar");
		verify(this.aiMetrics, atLeastOnce()).recordEndToEnd(any(Duration.class), any(String.class));
	}

	@Test
	void bufferedDefaultsPromptWhenMissing() {
		stubChatClientStream(Flux.just(""));
		this.webTestClient.post()
			.uri(AI_BASE + "/buffered")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(Map.of())
			.exchange()
			.expectStatus()
			.isOk();
	}

	@Test
	void summarizeWithToolInjectsToolEventAtConfiguredOffset() {
		// Default tool-call-after-tokens is 75 in app config; index 75 means we'd
		// need >75 model tokens before the tool fires. We set the prop in
		// application-test.properties below to a small value so we hit the branch.
		stubChatClientStream(Flux.just("t0", "t1", "t2", "t3", "t4"));
		UUID userId = UUID.randomUUID();
		UserDto user = new UserDto();
		user.setUserName("alice");
		given(this.userClient.getUserSummary(userId)).willReturn(Mono.just(user));

		this.webTestClient.post()
			.uri(uriBuilder -> uriBuilder
				.path("/tweets/ai/users/" + userId + "/conversations/" + CONVERSATION_ID + "/summarize-with-tool")
				.build())
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(Map.of("prompt", "hi"))
			.accept(MediaType.TEXT_EVENT_STREAM)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.value(body -> {
				assertThat(body).isNotNull();
				assertThat(body).contains("t0");
			});

		verify(this.aiMetrics, atLeastOnce()).recordTtft(any(Duration.class));
		verify(this.aiMetrics, atLeastOnce()).recordEndToEnd(any(Duration.class), any(String.class));
	}

	@Test
	void summarizeWithToolWithoutInjection() {
		// Using only a couple tokens; tool injection should not fire because
		// the configured offset (75) is past the stream length. Still exercises
		// the doOnNext / doFinally / poolMetrics paths.
		stubChatClientStream(Flux.just("p", "q"));
		UUID userId = UUID.randomUUID();

		this.webTestClient.post()
			.uri(uriBuilder -> uriBuilder
				.path("/tweets/ai/users/" + userId + "/conversations/" + CONVERSATION_ID + "/summarize-with-tool")
				.build())
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(Map.of("prompt", "hi"))
			.accept(MediaType.TEXT_EVENT_STREAM)
			.exchange()
			.expectStatus()
			.isOk()
			.returnResult(String.class)
			.getResponseBody()
			.blockLast(Duration.ofSeconds(5));

		verify(this.poolMetrics).onSubscribe();
		verify(this.poolMetrics, atLeastOnce()).onTerminate(any());
	}

	@Test
	void mockStreamEmitsConfiguredTokenCount() {
		List<String> events = this.webTestClient.get()
			.uri(uriBuilder -> uriBuilder.path(AI_BASE + "/mock-stream")
				.queryParam("tokens", 3)
				.queryParam("itlMs", 1)
				.build())
			.accept(MediaType.TEXT_EVENT_STREAM)
			.exchange()
			.expectStatus()
			.isOk()
			.returnResult(String.class)
			.getResponseBody()
			.collectList()
			.block(Duration.ofSeconds(5));
		// W0 emits exactly `tokens` deterministic semantic chunks in order — the
		// scripted non-AI baseline, no token_N placeholders.
		assertThat(events).isNotNull().hasSize(3);
		assertThat(events.get(0)).isEqualTo(SemanticChunks.word(0));
		assertThat(events.get(1)).isEqualTo(SemanticChunks.word(1));
		assertThat(events.get(2)).isEqualTo(SemanticChunks.word(2));
	}

	@Test
	void mockStreamUsesDefaults() {
		// Default tokens=150 would be slow with default itlMs=40. Override at
		// request level.
		this.webTestClient.get()
			.uri(uriBuilder -> uriBuilder.path(AI_BASE + "/mock-stream")
				.queryParam("tokens", 1)
				.queryParam("itlMs", 1)
				.build())
			.accept(MediaType.TEXT_EVENT_STREAM)
			.exchange()
			.expectStatus()
			.isOk();
	}

	@TestConfiguration
	static class MockConfig {

		@Bean
		ChatClient chatClient() {
			return Mockito.mock(ChatClient.class);
		}

	}

}
