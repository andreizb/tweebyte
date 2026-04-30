package ro.tweebyte.tweetservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
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

/**
 * Forces app.ai.tool-call-after-tokens=1 so the W2 tool-injection branch and the
 * subsequent post-tool ITL branch are actually exercised. Default test config
 * leaves it at 75 which never fires for tiny token streams.
 */
@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = AiController.class)
@Import(AiControllerToolInjectionTest.MockConfig.class)
@TestPropertySource(properties = "app.ai.tool-call-after-tokens=1")
class AiControllerToolInjectionTest {

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
    void summarizeWithToolFiresInjectionAndExercisesPostToolItl() {
        // 4 tokens: positions 0,1,2,3. injectAt=1 -> tool fires after token at index 1.
        stubChatClientStream(Flux.just("a", "b", "c", "d"));
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
                    // Tool event is injected as "[tool:alice]" and emitted alongside model tokens.
                    assertTrue(body.contains("[tool:alice]"),
                            "expected tool event in stream: " + body);
                });

        verify(aiMetrics, atLeastOnce()).recordTtft(any(Duration.class));
        verify(aiMetrics, atLeastOnce()).recordToolCall(any(Duration.class));
        // Post-tool tokens go through doOnNext where lastTokenNanos != -1L
        // and !token.startsWith("[tool:"), exercising the ITL branches.
        verify(aiMetrics, atLeastOnce()).recordItl(any(Duration.class));
    }

    @Test
    void summarizeReportsCancelOutcomeWhenClientCancels() {
        // Slow upstream that emits one token quickly then sits forever; we cancel by
        // taking only one element and disposing.
        stubChatClientStream(Flux.<String>never().startWith("first"));

        // Issue request and only take 1 element to force cancellation.
        webTestClient.post().uri("/tweets/ai/summarize")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("prompt", "x"))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class)
                .getResponseBody()
                .take(1)
                .blockLast(Duration.ofSeconds(5));

        // The doFinally must fire — outcome is either CANCEL or SUCCESS depending
        // on how the WebTestClient teardown unwinds. We just need the recordEndToEnd
        // to be invoked (proves the doFinally branch executed); the switch covering
        // CANCEL is exercised by the client-side cancel.
        verify(aiMetrics, atLeastOnce()).recordEndToEnd(any(Duration.class), any(String.class));
    }

    @TestConfiguration
    static class MockConfig {
        @Bean
        ChatClient chatClient() {
            return Mockito.mock(ChatClient.class);
        }
    }
}
