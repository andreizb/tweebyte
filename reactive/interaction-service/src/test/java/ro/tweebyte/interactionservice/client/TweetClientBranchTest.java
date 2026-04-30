package ro.tweebyte.interactionservice.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ro.tweebyte.interactionservice.exception.InteractionException;
import ro.tweebyte.interactionservice.model.TweetDto;

import java.util.UUID;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Branch coverage — exercises the FALSE branch of the NOT_FOUND check
 * (lines 38 and 51 of TweetClient) where a non-404 WebClientResponseException
 * is wrapped in an InteractionException instead of TweetNotFoundException.
 */
@ExtendWith(MockitoExtension.class)
class TweetClientBranchTest {

    @InjectMocks
    private TweetClient tweetClient;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    }

    @Test
    void getTweetSummary_NonNotFound_WrapsAsInteractionException() {
        UUID tweetId = UUID.randomUUID();
        WebClientResponseException server = WebClientResponseException.create(
            HttpStatus.INTERNAL_SERVER_ERROR.value(), "boom", null, null, null);
        when(responseSpec.bodyToMono(TweetDto.class)).thenReturn(Mono.error(server));

        StepVerifier.create(tweetClient.getTweetSummary(tweetId))
            .expectErrorMatches(e -> e instanceof InteractionException && e.getCause() == server)
            .verify();
    }

    @Test
    void getUserTweetsSummary_NonNotFound_WrapsAsInteractionException() {
        UUID userId = UUID.randomUUID();
        WebClientResponseException server = WebClientResponseException.create(
            HttpStatus.SERVICE_UNAVAILABLE.value(), "down", null, null, null);
        when(responseSpec.bodyToFlux(TweetDto.class)).thenReturn(Flux.error(server));

        StepVerifier.create(tweetClient.getUserTweetsSummary(userId))
            .expectErrorMatches(e -> e instanceof InteractionException && e.getCause() == server)
            .verify();
    }
}
