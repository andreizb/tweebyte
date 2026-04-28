package ro.tweebyte.tweetservice.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ro.tweebyte.tweetservice.exception.FollowRetrievingException;
import ro.tweebyte.tweetservice.exception.TweetException;
import ro.tweebyte.tweetservice.model.ReplyDto;
import ro.tweebyte.tweetservice.model.TweetSummaryDto;
import ro.tweebyte.tweetservice.util.ClientUtil;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InteractionClientTest {

    @Mock
    private ClientUtil clientUtil;

    @Mock
    private HttpClient httpClient;

    private InteractionClient interactionClient;

    @BeforeEach
    void setUp() {
        interactionClient = new InteractionClient(clientUtil, null);
        ReflectionTestUtils.setField(interactionClient, "client", httpClient);
        ReflectionTestUtils.setField(interactionClient, "BASE_URL", "http://localhost");
    }

    @Test
    void getRepliesCount() throws ExecutionException, InterruptedException {
        UUID tweetId = UUID.randomUUID();
        HttpResponse<String> mockHttpResponse = mock(HttpResponse.class);
        when(httpClient.sendAsync(any(), eq(HttpResponse.BodyHandlers.ofString()))).thenReturn(CompletableFuture.completedFuture(mockHttpResponse));
        when(clientUtil.parseResponse(any(), eq(Long.class))).thenReturn(42L);

        Long result = interactionClient.getRepliesCount(tweetId, "AUTH_TOKEN").get();

        assertEquals(42L, result);
        verify(httpClient).sendAsync(any(), any());
        verify(clientUtil).parseResponse(mockHttpResponse, Long.class);
    }

    @Test
    void getLikesCount() throws ExecutionException, InterruptedException {
        UUID tweetId = UUID.randomUUID();
        HttpResponse<String> mockHttpResponse = mock(HttpResponse.class);
        CompletableFuture<Long> expectedResult = CompletableFuture.completedFuture(42L);
        when(httpClient.sendAsync(any(), eq(HttpResponse.BodyHandlers.ofString()))).thenReturn(CompletableFuture.completedFuture(mockHttpResponse));
        when(clientUtil.parseResponse(any(), eq(Long.class))).thenReturn(42L);

        Long result = interactionClient.getLikesCount(tweetId, "AUTH_TOKEN").get();

        assertEquals(expectedResult.get(), result);
        verify(httpClient).sendAsync(any(), any());
        verify(clientUtil).parseResponse(mockHttpResponse, Long.class);
    }

    @Test
    void getRetweetsCount() throws ExecutionException, InterruptedException {
        UUID tweetId = UUID.randomUUID();
        HttpResponse<String> mockHttpResponse = mock(HttpResponse.class);
        CompletableFuture<Long> expectedResult = CompletableFuture.completedFuture(42L);
        when(httpClient.sendAsync(any(), eq(HttpResponse.BodyHandlers.ofString()))).thenReturn(CompletableFuture.completedFuture(mockHttpResponse));
        when(clientUtil.parseResponse(any(), eq(Long.class))).thenReturn(42L);

        Long result = interactionClient.getLikesCount(tweetId, "AUTH_TOKEN").get();

        assertEquals(expectedResult.get(), result);
        verify(httpClient).sendAsync(any(), any());
        verify(clientUtil).parseResponse(mockHttpResponse, Long.class);
    }

    @Test
    void getTopReply() throws ExecutionException, InterruptedException {
        UUID tweetId = UUID.randomUUID();
        HttpResponse<String> mockHttpResponse = mock(HttpResponse.class);
        ReplyDto expectedResult = new ReplyDto();
        when(httpClient.sendAsync(any(), eq(HttpResponse.BodyHandlers.ofString()))).thenReturn(CompletableFuture.completedFuture(mockHttpResponse));
        when(clientUtil.parseResponse(any(), eq(ReplyDto.class))).thenReturn(expectedResult);

        ReplyDto result = interactionClient.getTopReply(tweetId).get();

        assertEquals(expectedResult, result);
    }

    @Test
    void getRepliesForTweet() throws ExecutionException, InterruptedException {
        UUID tweetId = UUID.randomUUID();
        List<ReplyDto> expectedResult = new ArrayList<>();
        HttpResponse<String> mockHttpResponse = mock(HttpResponse.class);

        when(httpClient.sendAsync(any(), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(CompletableFuture.completedFuture(mockHttpResponse));

        doReturn(expectedResult).when(clientUtil).parseResponse(any(HttpResponse.class), any(TypeReference.class));

        List<ReplyDto> result = interactionClient.getRepliesForTweet(tweetId, "AUTH_TOKEN").get();

        assertEquals(expectedResult, result);
        verify(httpClient).sendAsync(any(), any());
        verify(clientUtil).parseResponse(any(HttpResponse.class), any(TypeReference.class));
    }

    @Test
    void getFollowerIds() throws ExecutionException, InterruptedException {
        UUID userId = UUID.randomUUID();
        List<UUID> expectedResult = new ArrayList<>();
        HttpResponse<String> mockHttpResponse = mock(HttpResponse.class);
        when(httpClient.sendAsync(any(), eq(HttpResponse.BodyHandlers.ofString()))).thenReturn(CompletableFuture.completedFuture(mockHttpResponse));
        when(clientUtil.parseResponse(any(), any(TypeReference.class))).thenReturn(expectedResult);

        List<UUID> result = interactionClient.getFollowedIds(userId).get();

        assertEquals(expectedResult, result);
    }

    @Test
    void getTweetSummaries() throws ExecutionException, InterruptedException, JsonProcessingException {
        List<UUID> tweetIds = List.of(UUID.randomUUID());
        List<TweetSummaryDto> expectedResult = new ArrayList<>();
        HttpResponse<String> mockHttpResponse = mock(HttpResponse.class);
        when(httpClient.sendAsync(any(), eq(HttpResponse.BodyHandlers.ofString()))).thenReturn(CompletableFuture.completedFuture(mockHttpResponse));
        when(clientUtil.parseResponse(any(), any(TypeReference.class))).thenReturn(expectedResult);
        when(clientUtil.generateBody(any())).thenReturn(new ObjectMapper().writeValueAsString(tweetIds));

        List<TweetSummaryDto> result = interactionClient.getTweetSummaries(tweetIds, "AUTH_TOKEN").get();

        assertEquals(expectedResult, result);
        verify(httpClient).sendAsync(any(), any());
        verify(clientUtil).parseResponse(any(HttpResponse.class), any(TypeReference.class));
    }

    @Test
    void getRepliesCount_exceptionThrown() {
        UUID tweetId = UUID.randomUUID();
        lenient().when(clientUtil.parseResponse(any(), eq(Long.class))).thenThrow(new TweetException(new Exception()));

        assertThrows(TweetException.class, () -> interactionClient.getRepliesCount(tweetId, "AUTH_TOKEN").get());
    }

    @Test
    void getLikesCount_exceptionThrown() {
        UUID tweetId = UUID.randomUUID();
        lenient().when(clientUtil.parseResponse(any(), eq(Long.class))).thenThrow(new TweetException(new Exception()));

        assertThrows(TweetException.class, () -> interactionClient.getLikesCount(tweetId, "AUTH_TOKEN").get());
    }

    @Test
    void getRetweetsCount_exceptionThrown() {
        UUID tweetId = UUID.randomUUID();
        lenient().when(clientUtil.parseResponse(any(), eq(Long.class))).thenThrow(new TweetException(new Exception()));

        assertThrows(TweetException.class, () -> interactionClient.getRetweetsCount(tweetId, "AUTH_TOKEN").get());
    }

    @Test
    void getFollowedIds_exceptionThrown() {
        UUID userId = UUID.randomUUID();
        lenient().when(clientUtil.parseResponse(any(), any(TypeReference.class))).thenThrow(new FollowRetrievingException());

        assertThrows(FollowRetrievingException.class, () -> interactionClient.getFollowedIds(userId).get());
    }

    @Test
    void getTopReply_exceptionThrown() {
        UUID tweetId = UUID.randomUUID();
        lenient().when(clientUtil.parseResponse(any(), eq(ReplyDto.class))).thenThrow(new TweetException(new Exception()));

        assertThrows(TweetException.class, () -> interactionClient.getTopReply(tweetId).get());
    }

    @Test
    void getRepliesForTweet_exceptionThrown() {
        UUID tweetId = UUID.randomUUID();
        lenient().when(clientUtil.parseResponse(any(), any(TypeReference.class))).thenThrow(new TweetException(new Exception()));

        assertThrows(TweetException.class, () -> interactionClient.getRepliesForTweet(tweetId, "AUTH_TOKEN").get());
    }

}