package ro.tweebyte.userservice.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ro.tweebyte.userservice.model.TweetDto;
import ro.tweebyte.userservice.util.ClientUtil;
import ro.tweebyte.userservice.exception.UserException;

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

@ExtendWith(MockitoExtension.class)
public class TweetClientTest {

	@Mock
	private ClientUtil clientUtil;

	@Mock
	private HttpClient httpClient;

	private TweetClient tweetClient;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		tweetClient = new TweetClient(clientUtil);

		ReflectionTestUtils.setField(tweetClient, "client", httpClient);
		ReflectionTestUtils.setField(tweetClient, "BASE_URL", "http://localhost");
	}

	@Test
	void testGetUserTweets() throws ExecutionException, InterruptedException {
		UUID userId = UUID.randomUUID();
		String authToken = "AUTH_TOKEN";
		HttpResponse<String> mockHttpResponse = mock(HttpResponse.class);
		when(httpClient.sendAsync(any(), eq(HttpResponse.BodyHandlers.ofString()))).thenReturn(CompletableFuture.completedFuture(mockHttpResponse));

		List<TweetDto> mockTweets = new ArrayList<>();
		when(clientUtil.parseResponse(mockHttpResponse, ArrayList.class)).thenReturn(((ArrayList) mockTweets));

		CompletableFuture<List<TweetDto>> resultFuture = tweetClient.getUserTweets(userId, authToken);
		List<TweetDto> result = resultFuture.get();

		assertEquals(mockTweets, result);
		verify(httpClient).sendAsync(any(), any());
		verify(clientUtil).parseResponse(mockHttpResponse, ArrayList.class);
	}

	@Test
	void testGetUserTweetsThrowsUserException() {
		UUID userId = UUID.randomUUID();
		String authToken = "AUTH_TOKEN";

		when(httpClient.sendAsync(any(), eq(HttpResponse.BodyHandlers.ofString()))).thenThrow(new RuntimeException("HTTP Error"));

		UserException exception = assertThrows(UserException.class, () -> {
			tweetClient.getUserTweets(userId, authToken).join();
		});

		assertEquals("java.lang.RuntimeException: HTTP Error", exception.getCause().toString());
	}
}