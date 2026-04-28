package ro.tweebyte.interactionservice.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import ro.tweebyte.interactionservice.exception.ClientException;
import ro.tweebyte.interactionservice.exception.InteractionException;
import ro.tweebyte.interactionservice.exception.TweetNotFoundException;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.util.ClientUtil;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TweetClientTest {

    private TweetClient tweetClient;

    @Mock
    private ClientUtil clientUtil;

    @Mock
    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        tweetClient = new TweetClient(clientUtil);
        ReflectionTestUtils.setField(tweetClient, "client", httpClient);
        ReflectionTestUtils.setField(tweetClient, "BASE_URL", "http://localhost");
    }

    @Test
    void testGetTweetSummary() throws Exception {
        // Given
        UUID tweetId = UUID.randomUUID();
        TweetDto expectedDto = new TweetDto();
        HttpResponse<String> mockHttpResponse = Mockito.mock(HttpResponse.class);

        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenReturn(mockHttpResponse);

        when(clientUtil.parseResponse(any(), eq(TweetDto.class))).thenReturn(expectedDto);

        // When
        TweetDto result = tweetClient.getTweetSummary(tweetId);

        // Then
        assertNotNull(result);
        assertEquals(expectedDto, result);
    }

    @Test
    void testGetUserTweetsSummary() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        List<TweetDto> expectedList = List.of(new TweetDto());
        HttpResponse<String> mockHttpResponse = Mockito.mock(HttpResponse.class);

        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenReturn(mockHttpResponse);

        when(clientUtil.parseResponse(any(), any(Class.class))).thenReturn(expectedList);

        // When
        List<TweetDto> result = tweetClient.getUserTweetsSummary(userId);

        // Then
        assertNotNull(result);
        assertEquals(expectedList, result);
    }

    @Test
    void testGetPopularHashtags() throws Exception {
        // Given
        List<TweetDto.HashtagDto> expectedList = List.of(new TweetDto.HashtagDto());
        HttpResponse<String> mockHttpResponse = Mockito.mock(HttpResponse.class);

        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenReturn(mockHttpResponse);
        when(clientUtil.parseResponse(any(), any(Class.class))).thenReturn(expectedList);

        // When
        List<TweetDto.HashtagDto> result = tweetClient.getPopularHashtags();

        // Then
        assertNotNull(result);
        assertEquals(expectedList, result);
    }

    @Test
    void testGetTweetSummaryException() throws Exception {
        // Given
        UUID tweetId = UUID.randomUUID();
        HttpResponse<String> mockHttpResponse = Mockito.mock(HttpResponse.class);

        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenReturn(mockHttpResponse);

        when(clientUtil.parseResponse(any(), eq(TweetDto.class))).thenThrow(new InteractionException());

        // When and Then
        assertThrows(InteractionException.class, () -> tweetClient.getTweetSummary(tweetId));
    }

    @Test
    void testGetUserTweetsSummaryNotFound() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        HttpResponse<String> mockHttpResponse = Mockito.mock(HttpResponse.class);

        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenReturn(mockHttpResponse);

        when(clientUtil.parseResponse(any(), any(Class.class))).thenThrow(new TweetNotFoundException("Tweet not found"));

        // When and Then
        Exception e = assertThrows(InteractionException.class, () -> tweetClient.getUserTweetsSummary(userId));
        assertInstanceOf(TweetNotFoundException.class, e.getCause());
    }

    @Test
    void testGetPopularHashtagsException() throws Exception {
        // Given
        HttpResponse<String> mockHttpResponse = Mockito.mock(HttpResponse.class);

        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenReturn(mockHttpResponse);

        when(clientUtil.parseResponse(any(), any(Class.class))).thenThrow(new InteractionException());

        // When and Then
        assertThrows(InteractionException.class, () -> tweetClient.getPopularHashtags());
    }

}