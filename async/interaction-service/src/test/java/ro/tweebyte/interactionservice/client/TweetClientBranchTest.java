package ro.tweebyte.interactionservice.client;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Branch-coverage tests for TweetClient — covers the 404 vs other-error arms
 * of the ClientException catch block, exercising the negative paths missing
 * from TweetClientTest.
 */
@ExtendWith(MockitoExtension.class)
class TweetClientBranchTest {

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
        ReflectionTestUtils.setField(tweetClient, "BASE_URL", "http://localhost/");
    }

    @Test
    void getTweetSummary_404_throwsTweetNotFound() throws Exception {
        UUID tweetId = UUID.randomUUID();
        HttpResponse<String> http = Mockito.mock(HttpResponse.class);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenReturn(http);

        HttpResponse<String> err = Mockito.mock(HttpResponse.class);
        when(err.statusCode()).thenReturn(HttpStatus.NOT_FOUND.value());
        when(clientUtil.parseResponse(any(), eq(TweetDto.class)))
            .thenThrow(new ClientException(err));

        assertThrows(TweetNotFoundException.class, () -> tweetClient.getTweetSummary(tweetId));
    }

    @Test
    void getTweetSummary_500_wrapsAsInteractionException() throws Exception {
        UUID tweetId = UUID.randomUUID();
        HttpResponse<String> http = Mockito.mock(HttpResponse.class);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenReturn(http);

        HttpResponse<String> err = Mockito.mock(HttpResponse.class);
        when(err.statusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR.value());
        when(clientUtil.parseResponse(any(), eq(TweetDto.class)))
            .thenThrow(new ClientException(err));

        assertThrows(InteractionException.class, () -> tweetClient.getTweetSummary(tweetId));
    }

    @Test
    void getUserTweetsSummary_404_throwsTweetNotFound() throws Exception {
        UUID userId = UUID.randomUUID();
        HttpResponse<String> http = Mockito.mock(HttpResponse.class);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenReturn(http);

        HttpResponse<String> err = Mockito.mock(HttpResponse.class);
        when(err.statusCode()).thenReturn(HttpStatus.NOT_FOUND.value());
        when(clientUtil.parseResponse(any(), any(TypeReference.class)))
            .thenThrow(new ClientException(err));

        assertThrows(TweetNotFoundException.class, () -> tweetClient.getUserTweetsSummary(userId));
    }

    @Test
    void getUserTweetsSummary_500_wrapsAsInteractionException() throws Exception {
        UUID userId = UUID.randomUUID();
        HttpResponse<String> http = Mockito.mock(HttpResponse.class);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenReturn(http);

        HttpResponse<String> err = Mockito.mock(HttpResponse.class);
        when(err.statusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR.value());
        when(clientUtil.parseResponse(any(), any(TypeReference.class)))
            .thenThrow(new ClientException(err));

        assertThrows(InteractionException.class, () -> tweetClient.getUserTweetsSummary(userId));
    }

    @Test
    void getTweetSummary_genericException_wrapped() throws Exception {
        UUID tweetId = UUID.randomUUID();
        // httpClient.send throws — generic catch arm.
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenThrow(new java.io.IOException("net"));
        assertThrows(InteractionException.class, () -> tweetClient.getTweetSummary(tweetId));
    }

    @Test
    void getUserTweetsSummary_genericException_wrapped() throws Exception {
        UUID userId = UUID.randomUUID();
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenThrow(new java.io.IOException("net"));
        assertThrows(InteractionException.class, () -> tweetClient.getUserTweetsSummary(userId));
    }

    @Test
    void getPopularHashtags_genericException_wrapped() throws Exception {
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenThrow(new java.io.IOException("net"));
        assertThrows(InteractionException.class, () -> tweetClient.getPopularHashtags());
    }
}
