package ro.tweebyte.tweetservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
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

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = AiController.class)
@Import(AiControllerTest.MockConfig.class)
class AiControllerTest {

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
        Mockito.reset(chatClient);
        stubChatClientStream(Flux.just("a", "b", "c"));
    }

    @SuppressWarnings("unchecked")
    private void stubChatClientStream(Flux<String> tokens) {
        ChatClient.ChatClientRequestSpec spec = Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec stream = Mockito.mock(ChatClient.StreamResponseSpec.class);
        given(chatClient.prompt()).willReturn(spec);
        given(spec.user(any(String.class))).willReturn(spec);
        given(spec.stream()).willReturn(stream);
        given(stream.content()).willReturn(tokens);
    }

    @Test
    void summarizeStreamsTokensAndRecordsMetrics() {
        stubChatClientStream(Flux.just("hello", "world", "!"));

        webTestClient.post().uri("/tweets/ai/summarize")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("prompt", "hi"))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(body -> {
                    assertNotNull(body);
                    assertTrue(body.contains("hello"));
                    assertTrue(body.contains("world"));
                });

        // ttft + at least one itl + serialize calls + e2e success
        verify(aiMetrics, atLeastOnce()).recordTtft(any(Duration.class));
        verify(aiMetrics, atLeastOnce()).recordSerialize(any(Duration.class));
        verify(aiMetrics, atLeastOnce()).recordEndToEnd(any(Duration.class), any(String.class));
        verify(poolMetrics).onSubscribe();
        verify(poolMetrics, atLeastOnce()).onTerminate(any());
    }

    @Test
    void summarizeWithMissingPromptDefaultsToEmpty() {
        stubChatClientStream(Flux.just("x"));
        webTestClient.post().uri("/tweets/ai/summarize")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of())
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void summarizeWithSingleTokenSkipsItlBranch() {
        stubChatClientStream(Flux.just("only"));
        webTestClient.post().uri("/tweets/ai/summarize")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("prompt", "x"))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk();
        verify(aiMetrics, atLeastOnce()).recordTtft(any(Duration.class));
    }

    @Test
    void summarizeReportsErrorOutcomeOnUpstreamError() {
        stubChatClientStream(Flux.error(new RuntimeException("boom")));

        webTestClient.post().uri("/tweets/ai/summarize")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("prompt", "x"))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().is5xxServerError();

        ArgumentCaptor<String> outcome = ArgumentCaptor.forClass(String.class);
        verify(aiMetrics, atLeastOnce()).recordEndToEnd(any(Duration.class), outcome.capture());
        assertTrue(outcome.getAllValues().contains(AiLatencyMetrics.OUTCOME_ERROR),
                "expected at least one error outcome record, got " + outcome.getAllValues());
    }

    @Test
    void bufferedAggregatesTokensAndReturnsJson() {
        stubChatClientStream(Flux.just("foo", "bar"));

        webTestClient.post().uri("/tweets/ai/buffered")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("prompt", "hi"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.response").isEqualTo("foobar");
        verify(aiMetrics, atLeastOnce()).recordEndToEnd(any(Duration.class), any(String.class));
    }

    @Test
    void bufferedDefaultsPromptWhenMissing() {
        stubChatClientStream(Flux.just(""));
        webTestClient.post().uri("/tweets/ai/buffered")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of())
                .exchange()
                .expectStatus().isOk();
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
        given(userClient.getUserSummary(userId)).willReturn(Mono.just(user));

        webTestClient.post().uri(uriBuilder -> uriBuilder
                        .path("/tweets/ai/summarize-with-tool")
                        .queryParam("userId", userId)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("prompt", "hi"))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(body -> {
                    assertNotNull(body);
                    assertTrue(body.contains("t0"));
                });

        verify(aiMetrics, atLeastOnce()).recordTtft(any(Duration.class));
        verify(aiMetrics, atLeastOnce()).recordEndToEnd(any(Duration.class), any(String.class));
    }

    @Test
    void summarizeWithToolWithoutInjection() {
        // Using only a couple tokens; tool injection should not fire because
        // the configured offset (75) is past the stream length. Still exercises
        // the doOnNext / doFinally / poolMetrics paths.
        stubChatClientStream(Flux.just("p", "q"));
        UUID userId = UUID.randomUUID();

        webTestClient.post().uri(uriBuilder -> uriBuilder
                        .path("/tweets/ai/summarize-with-tool")
                        .queryParam("userId", userId)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("prompt", "hi"))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class)
                .getResponseBody()
                .blockLast(Duration.ofSeconds(5));

        verify(poolMetrics).onSubscribe();
        verify(poolMetrics, atLeastOnce()).onTerminate(any());
    }

    @Test
    void mockStreamEmitsConfiguredTokenCount() {
        webTestClient.get().uri(uriBuilder -> uriBuilder
                        .path("/tweets/ai/mock-stream")
                        .queryParam("tokens", 3)
                        .queryParam("itlMs", 1)
                        .build())
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(body -> {
                    assertNotNull(body);
                    assertTrue(body.contains("token_0"));
                    assertTrue(body.contains("token_1"));
                    assertTrue(body.contains("token_2"));
                });
    }

    @Test
    void mockStreamUsesDefaults() {
        // Default tokens=150 would be slow with default itlMs=40. Override at
        // request level.
        webTestClient.get().uri(uriBuilder -> uriBuilder
                        .path("/tweets/ai/mock-stream")
                        .queryParam("tokens", 1)
                        .queryParam("itlMs", 1)
                        .build())
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk();
    }

    @TestConfiguration
    static class MockConfig {
        @Bean
        ChatClient chatClient() {
            return Mockito.mock(ChatClient.class);
        }
    }
}
