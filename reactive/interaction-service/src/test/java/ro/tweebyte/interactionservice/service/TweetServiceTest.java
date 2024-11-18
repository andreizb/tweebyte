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

	private ReactiveRedisTemplate<String, Object> redisTemplate;
	private TweetClient tweetClient;
	private TweetService tweetService;
	private ReactiveValueOperations<String, Object> valueOperations;
	private ReactiveListOperations<String, Object> listOperations;

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
		when(valueOperations.set(eq(key), any(TweetDto.class))).thenReturn(Mono.just(true));

		Mono<TweetDto> result = tweetService.getTweetSummary(tweetId);

		StepVerifier.create(result)
			.expectNext(tweetDto)
			.verifyComplete();
		verify(tweetClient).getTweetSummary(tweetId);
		verify(valueOperations).set(eq(key), eq(tweetDto));
	}

}
