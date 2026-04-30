package ro.tweebyte.interactionservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.data.redis.core.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ro.tweebyte.interactionservice.client.TweetClient;
import ro.tweebyte.interactionservice.model.TweetDto;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TweetServiceTest {

	private ReactiveRedisTemplate<String, byte[]> redisTemplate;
	private TweetClient tweetClient;
	private TweetService tweetService;
	private ReactiveValueOperations<String, byte[]> valueOperations;
	private ReactiveListOperations<String, byte[]> listOperations;

	@BeforeEach
	void setUp() {
		redisTemplate = mock(ReactiveRedisTemplate.class);
		tweetClient = mock(TweetClient.class);
		tweetService = new TweetService(tweetClient, redisTemplate);
		valueOperations = mock(ReactiveValueOperations.class);
		listOperations = mock(ReactiveListOperations.class);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(redisTemplate.opsForList()).thenReturn(listOperations);
	}

	@Test
	void getTweetSummary_CacheMiss() {
		UUID tweetId = UUID.randomUUID();
		String key = "tweets:" + tweetId;
		TweetDto tweetDto = new TweetDto();
		when(valueOperations.get(key)).thenReturn(Mono.empty());
		when(tweetClient.getTweetSummary(tweetId)).thenReturn(Mono.just(tweetDto));
		when(valueOperations.set(eq(key), any(byte[].class))).thenReturn(Mono.just(true));

		Mono<TweetDto> result = tweetService.getTweetSummary(tweetId);

		StepVerifier.create(result)
				.expectNext(tweetDto)
				.verifyComplete();
		verify(tweetClient).getTweetSummary(tweetId);
		verify(valueOperations).set(eq(key), any(byte[].class));
	}

	@Test
	void getUserTweetsSummary_CacheMiss() {
		// When no cached
		// list bytes, fetches via TweetClient and pushes each tweet onto the list.
		UUID userId = UUID.randomUUID();
		String key = "user_tweets:" + userId;
		TweetDto tweetDto = new TweetDto();
		when(listOperations.range(eq(key), eq(0L), eq(-1L))).thenReturn(Flux.empty());
		when(tweetClient.getUserTweetsSummary(userId)).thenReturn(Flux.just(tweetDto));
		when(listOperations.rightPush(eq(key), any(byte[].class))).thenReturn(Mono.just(1L));

		Flux<TweetDto> result = tweetService.getUserTweetsSummary(userId);

		StepVerifier.create(result)
				.expectNext(tweetDto)
				.verifyComplete();
		verify(tweetClient).getUserTweetsSummary(userId);
		verify(listOperations).rightPush(eq(key), any(byte[].class));
	}

	@Test
	void getPopularHashtags_CacheMiss() {
		// When the popular
		// hashtags list is empty, fetches via TweetClient and pushes each hashtag.
		String key = "popular_hashtags:";
		TweetDto.HashtagDto hashtag = new TweetDto.HashtagDto();
		when(listOperations.range(eq(key), eq(0L), eq(-1L))).thenReturn(Flux.empty());
		when(tweetClient.getPopularHashtags()).thenReturn(Flux.just(hashtag));
		when(listOperations.rightPush(eq(key), any(byte[].class))).thenReturn(Mono.just(1L));

		Flux<TweetDto.HashtagDto> result = tweetService.getPopularHashtags();

		StepVerifier.create(result)
				.expectNext(hashtag)
				.verifyComplete();
		verify(tweetClient).getPopularHashtags();
		verify(listOperations).rightPush(eq(key), any(byte[].class));
	}

}
