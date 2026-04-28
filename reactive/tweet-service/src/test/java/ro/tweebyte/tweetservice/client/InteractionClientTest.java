package ro.tweebyte.tweetservice.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ro.tweebyte.tweetservice.exception.FollowRetrievingException;
import ro.tweebyte.tweetservice.exception.TweetException;
import ro.tweebyte.tweetservice.model.ReplyDto;
import ro.tweebyte.tweetservice.model.TweetSummaryDto;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InteractionClientTest {

    @InjectMocks
    private InteractionClient interactionClient;

    @Mock
    private WebClient webClientMock;

    @Mock
    private WebClient.Builder webClientBuilderMock;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpecMock;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpecMock;

    @Mock
    private WebClient.RequestBodySpec requestBodySpecMock;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpecMock;

    @Mock
    private WebClient.ResponseSpec responseSpecMock;

    @Mock
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, Object> reactiveValueOperations;

    @Test
    public void testGetRepliesCount() {
        when(webClientMock.get()).thenReturn(requestHeadersUriSpecMock);
        when(requestHeadersUriSpecMock.uri(any(Function.class))).thenReturn(requestHeadersSpecMock);
        when(requestHeadersSpecMock.header(anyString(), anyString())).thenReturn(requestHeadersSpecMock);
        when(requestHeadersSpecMock.retrieve()).thenReturn(responseSpecMock);

        UUID tweetId = UUID.randomUUID();
        Long expectedCount = 10L;

        when(responseSpecMock.bodyToMono(Long.class)).thenReturn(Mono.just(expectedCount));

        StepVerifier.create(interactionClient.getRepliesCount(tweetId, "AUTH_TOKEN"))
            .expectNext(expectedCount)
            .verifyComplete();

        verify(requestHeadersSpecMock).header("Authorization", "AUTH_TOKEN");
    }

    @Test
    public void testGetLikesCount() {
        when(webClientMock.get()).thenReturn(requestHeadersUriSpecMock);
        when(requestHeadersUriSpecMock.uri(any(Function.class))).thenReturn(requestHeadersSpecMock);
        when(requestHeadersSpecMock.header(anyString(), anyString())).thenReturn(requestHeadersSpecMock);
        when(requestHeadersSpecMock.retrieve()).thenReturn(responseSpecMock);

        UUID tweetId = UUID.randomUUID();
        Long expectedCount = 15L;

        when(responseSpecMock.bodyToMono(Long.class)).thenReturn(Mono.just(expectedCount));

        StepVerifier.create(interactionClient.getLikesCount(tweetId, "AUTH_TOKEN"))
            .expectNext(expectedCount)
            .verifyComplete();

        verify(requestHeadersSpecMock).header("Authorization", "AUTH_TOKEN");
    }

    @Test
    public void testGetRetweetsCount() {
        when(webClientMock.get()).thenReturn(requestHeadersUriSpecMock);
        when(requestHeadersUriSpecMock.uri(any(Function.class))).thenReturn(requestHeadersSpecMock);
        when(requestHeadersSpecMock.header(anyString(), anyString())).thenReturn(requestHeadersSpecMock);
        when(requestHeadersSpecMock.retrieve()).thenReturn(responseSpecMock);

        UUID tweetId = UUID.randomUUID();
        Long expectedCount = 20L;

        when(responseSpecMock.bodyToMono(Long.class)).thenReturn(Mono.just(expectedCount));

        StepVerifier.create(interactionClient.getRetweetsCount(tweetId, "AUTH_TOKEN"))
            .expectNext(expectedCount)
            .verifyComplete();

        verify(requestHeadersSpecMock).header("Authorization", "AUTH_TOKEN");
    }

    @Test
    public void testGetTopReply() {
        when(webClientMock.get()).thenReturn(requestHeadersUriSpecMock);
        when(requestHeadersUriSpecMock.uri(any(Function.class))).thenReturn(requestHeadersSpecMock);
        when(requestHeadersSpecMock.header(anyString(), anyString())).thenReturn(requestHeadersSpecMock);
        when(requestHeadersSpecMock.retrieve()).thenReturn(responseSpecMock);

        UUID tweetId = UUID.randomUUID();
        ReplyDto expectedReply = new ReplyDto();

        when(responseSpecMock.bodyToMono(ReplyDto.class)).thenReturn(Mono.just(expectedReply));

        StepVerifier.create(interactionClient.getTopReply(tweetId, "AUTH_TOKEN"))
            .expectNext(expectedReply)
            .verifyComplete();

        verify(requestHeadersSpecMock).header("Authorization", "AUTH_TOKEN");
    }

    @Test
    public void testGetRepliesForTweet() {
        when(webClientMock.get()).thenReturn(requestHeadersUriSpecMock);
        when(requestHeadersUriSpecMock.uri(any(Function.class))).thenReturn(requestHeadersSpecMock);
        when(requestHeadersSpecMock.header(anyString(), anyString())).thenReturn(requestHeadersSpecMock);
        when(requestHeadersSpecMock.retrieve()).thenReturn(responseSpecMock);

        UUID tweetId = UUID.randomUUID();
        ReplyDto reply1 = new ReplyDto();
        ReplyDto reply2 = new ReplyDto();

        when(responseSpecMock.bodyToFlux(ReplyDto.class)).thenReturn(Flux.just(reply1, reply2));

        StepVerifier.create(interactionClient.getRepliesForTweet(tweetId, "AUTH_TOKEN"))
            .expectNext(reply1)
            .expectNext(reply2)
            .verifyComplete();

        verify(requestHeadersSpecMock).header("Authorization", "AUTH_TOKEN");
    }

    @Test
    public void testGetFollowedIds() {
        when(webClientMock.get()).thenReturn(requestHeadersUriSpecMock);
        when(requestHeadersUriSpecMock.uri(any(Function.class))).thenReturn(requestHeadersSpecMock);
        when(requestHeadersSpecMock.header(anyString(), anyString())).thenReturn(requestHeadersSpecMock);
        when(requestHeadersSpecMock.retrieve()).thenReturn(responseSpecMock);

        UUID userId = UUID.randomUUID();
        List<UUID> expectedFollowers = List.of(UUID.randomUUID(), UUID.randomUUID());

        when(responseSpecMock.bodyToMono(new ParameterizedTypeReference<List<UUID>>() {})).thenReturn(Mono.just(expectedFollowers));
        when(redisTemplate.opsForValue()).thenReturn(reactiveValueOperations);
        when(reactiveValueOperations.set(anyString(), eq(expectedFollowers)))
            .thenReturn(Mono.just(true));

        StepVerifier.create(interactionClient.getFollowedIds(userId, "AUTH_TOKEN"))
            .expectNextSequence(expectedFollowers)
            .verifyComplete();

        verify(reactiveValueOperations).set(eq("followed_cache::" + userId), eq(expectedFollowers));
    }

    @Test
    public void testGetTweetSummaries() {
        when(webClientMock.post()).thenReturn(requestBodyUriSpecMock);
        when(requestBodyUriSpecMock.uri(any(Function.class))).thenReturn(requestBodySpecMock);
        when(requestBodySpecMock.header(anyString(), anyString())).thenReturn(requestBodySpecMock);
//        when(requestBodySpecMock.retrieve()).thenReturn(responseSpecMock);
        when(requestBodySpecMock.bodyValue(any())).thenReturn(requestHeadersSpecMock);
//        when(requestBodySpecMock.body(any())).thenReturn(requestHeadersSpecMock);
        when(requestHeadersSpecMock.retrieve()).thenReturn(responseSpecMock);

        List<UUID> tweetIds = List.of(UUID.randomUUID());
        TweetSummaryDto summary1 = new TweetSummaryDto();
        TweetSummaryDto summary2 = new TweetSummaryDto();

        when(responseSpecMock.bodyToFlux(TweetSummaryDto.class)).thenReturn(Flux.just(summary1, summary2));

        StepVerifier.create(interactionClient.getTweetSummaries(tweetIds, "AUTH_TOKEN"))
            .expectNext(summary1)
            .expectNext(summary2)
            .verifyComplete();

        verify(requestBodySpecMock).header("Authorization", "AUTH_TOKEN");
    }

}
