package ro.tweebyte.userservice.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ro.tweebyte.userservice.exception.FollowRetrievingException;

import java.net.URI;
import java.util.UUID;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class InteractionClientTest {

    @InjectMocks
    private InteractionClient interactionClient;

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
    public void testGetFollowersCountSuccess() {
        UUID userId = UUID.randomUUID();
        String authToken = "Bearer AUTH_TOKEN";
        Long expectedCount = 100L;
        when(responseSpecMock.bodyToMono(Long.class)).thenReturn(Mono.just(expectedCount));

        StepVerifier.create(interactionClient.getFollowersCount(userId, authToken))
                .expectNext(expectedCount)
                .verifyComplete();
    }

    @Test
    public void testGetFollowingCountSuccess() {
        UUID userId = UUID.randomUUID();
        String authToken = "Bearer AUTH_TOKEN";
        Long expectedCount = 50L;
        when(responseSpecMock.bodyToMono(Long.class)).thenReturn(Mono.just(expectedCount));

        StepVerifier.create(interactionClient.getFollowingCount(userId, authToken))
                .expectNext(expectedCount)
                .verifyComplete();
    }

    @Test
    public void testGetFollowersCountError() {
        UUID userId = UUID.randomUUID();
        String authToken = "Bearer AUTH_TOKEN";
        when(responseSpecMock.bodyToMono(Long.class)).thenReturn(Mono.error(new RuntimeException()));

        StepVerifier.create(interactionClient.getFollowersCount(userId, authToken))
                .expectError(FollowRetrievingException.class)
                .verify();
    }

    @Test
    public void testGetFollowingCountError() {
        UUID userId = UUID.randomUUID();
        String authToken = "Bearer AUTH_TOKEN";
        when(responseSpecMock.bodyToMono(Long.class)).thenReturn(Mono.error(new RuntimeException()));

        StepVerifier.create(interactionClient.getFollowingCount(userId, authToken))
                .expectError(FollowRetrievingException.class)
                .verify();
    }
}