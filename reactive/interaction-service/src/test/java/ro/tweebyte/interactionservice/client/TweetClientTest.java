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
import ro.tweebyte.interactionservice.exception.TweetNotFoundException;
import ro.tweebyte.interactionservice.model.TweetDto;

import java.util.UUID;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TweetClientTest {

	@InjectMocks
	private TweetClient tweetClient;

	@Mock
	private WebClient.Builder webClientBuilder;

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
		when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
	}

	@Test
	void getTweetSummary_Success() {
		UUID tweetId = UUID.randomUUID();
		TweetDto expectedTweet = new TweetDto();
		expectedTweet.setId(tweetId);

		when(webClient.get()).thenReturn(requestHeadersUriSpec);
		when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
		when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
		when(responseSpec.bodyToMono(TweetDto.class)).thenReturn(Mono.just(expectedTweet));

		StepVerifier.create(tweetClient.getTweetSummary(tweetId))
			.expectNext(expectedTweet)
			.verifyComplete();

		verify(webClient).get();
		verify(requestHeadersUriSpec).uri(any(Function.class));
		verify(responseSpec).bodyToMono(TweetDto.class);
	}

	@Test
	void getTweetSummary_NotFound() {
		UUID tweetId = UUID.randomUUID();
		when(webClient.get()).thenReturn(requestHeadersUriSpec);
		when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
		when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
		when(responseSpec.bodyToMono(TweetDto.class))
			.thenReturn(Mono.error(new WebClientResponseException(HttpStatus.NOT_FOUND.value(), "Not Found", null, null, null)));

		StepVerifier.create(tweetClient.getTweetSummary(tweetId))
			.expectError(TweetNotFoundException.class)
			.verify();

		verify(webClient).get();
		verify(requestHeadersUriSpec).uri(any(Function.class));
	}

	@Test
	void getUserTweetsSummary_Success() {
		UUID userId = UUID.randomUUID();
		TweetDto tweet1 = new TweetDto();
		tweet1.setId(UUID.randomUUID());
		TweetDto tweet2 = new TweetDto();
		tweet2.setId(UUID.randomUUID());

		when(webClient.get()).thenReturn(requestHeadersUriSpec);
		when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
		when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
		when(responseSpec.bodyToFlux(TweetDto.class)).thenReturn(Flux.just(tweet1, tweet2));

		StepVerifier.create(tweetClient.getUserTweetsSummary(userId))
			.expectNext(tweet1, tweet2)
			.verifyComplete();

		verify(webClient).get();
		verify(requestHeadersUriSpec).uri(any(Function.class));
		verify(responseSpec).bodyToFlux(TweetDto.class);
	}

	@Test
	void getUserTweetsSummary_NotFound() {
		UUID userId = UUID.randomUUID();

		when(webClient.get()).thenReturn(requestHeadersUriSpec);
		when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
		when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
		when(responseSpec.bodyToFlux(TweetDto.class))
			.thenReturn(Flux.error(new WebClientResponseException(HttpStatus.NOT_FOUND.value(), "Not Found", null, null, null)));

		StepVerifier.create(tweetClient.getUserTweetsSummary(userId))
			.expectError(TweetNotFoundException.class)
			.verify();

		verify(webClient).get();
		verify(requestHeadersUriSpec).uri(any(Function.class));
	}

	@Test
	void getPopularHashtags_Success() {
		TweetDto.HashtagDto hashtag1 = new TweetDto.HashtagDto();
		hashtag1.setText("#test1");
		TweetDto.HashtagDto hashtag2 = new TweetDto.HashtagDto();
		hashtag2.setText("#test2");

		when(webClient.get()).thenReturn(requestHeadersUriSpec);
		when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
		when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
		when(responseSpec.bodyToFlux(TweetDto.HashtagDto.class)).thenReturn(Flux.just(hashtag1, hashtag2));

		StepVerifier.create(tweetClient.getPopularHashtags())
			.expectNext(hashtag1, hashtag2)
			.verifyComplete();

		verify(webClient).get();
		verify(requestHeadersUriSpec).uri(any(Function.class));
		verify(responseSpec).bodyToFlux(TweetDto.HashtagDto.class);
	}

	@Test
	void getPopularHashtags_Failure() {
		when(webClient.get()).thenReturn(requestHeadersUriSpec);
		when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
		when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
		when(responseSpec.bodyToFlux(TweetDto.HashtagDto.class))
			.thenReturn(Flux.error(new RuntimeException("Service unavailable")));

		StepVerifier.create(tweetClient.getPopularHashtags())
			.expectError(InteractionException.class)
			.verify();

		verify(webClient).get();
		verify(requestHeadersUriSpec).uri(any(Function.class));
	}
}
