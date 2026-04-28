package ro.tweebyte.userservice.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import ro.tweebyte.userservice.model.TweetDto;

import java.net.URI;
import java.util.UUID;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TweetClientTest {

    @InjectMocks
    private TweetClient tweetClient;

    @Mock
    private WebClient webClientMock;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpecMock;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpecMock;

    @Mock
    private WebClient.ResponseSpec responseSpecMock;

    @BeforeEach
    public void setUp() {
        when(webClientMock.get()).thenReturn(requestHeadersUriSpecMock);
        when(requestHeadersUriSpecMock.uri((Function<UriBuilder, URI>) any())).thenReturn(requestHeadersSpecMock);
        when(requestHeadersSpecMock.header(any(), any())).thenReturn(requestHeadersSpecMock);
        when(requestHeadersSpecMock.retrieve()).thenReturn(responseSpecMock);
    }

    @Test
    public void testGetUserTweetsSuccess() {
        UUID userId = UUID.randomUUID();
        String authToken = "Bearer AUTH_TOKEN";
        TweetDto tweetDto = new TweetDto();
        when(responseSpecMock.bodyToFlux(TweetDto.class)).thenReturn(Flux.just(tweetDto));

        Flux<TweetDto> result = tweetClient.getUserTweets(userId, authToken);

        StepVerifier.create(result)
                .expectNext(tweetDto)
                .verifyComplete();
    }

    @Test
    public void testGetUserTweetsThrowsException() {
        UUID userId = UUID.randomUUID();
        String authToken = "Bearer AUTH_TOKEN";

        when(responseSpecMock.bodyToFlux(TweetDto.class)).thenReturn(Flux.error(new RuntimeException("HTTP Error")));

        Flux<TweetDto> result = tweetClient.getUserTweets(userId, authToken);

        StepVerifier.create(result)
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException && throwable.getMessage().equals("HTTP Error"))
                .verify();
    }
}