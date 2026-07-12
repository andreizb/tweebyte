/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.interactionservice.client.TweetClient;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.TweetSummaryDto;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TweetServiceTests {

	private ReactiveRedisTemplate<String, byte[]> redisTemplate;

	private TweetClient tweetClient;

	private TweetService tweetService;

	private ReactiveValueOperations<String, byte[]> valueOperations;

	@BeforeEach
	void setUp() {
		this.redisTemplate = mock(ReactiveRedisTemplate.class);
		this.tweetClient = mock(TweetClient.class);
		this.tweetService = new TweetService(this.tweetClient, this.redisTemplate);
		// @Value-injected TTL is null without a Spring context; set it so the
		// set(...) writes pass a real Duration (matches production).
		ReflectionTestUtils.setField(this.tweetService, "cacheTtl", Duration.ofSeconds(60));
		this.valueOperations = mock(ReactiveValueOperations.class);
		given(this.redisTemplate.opsForValue()).willReturn(this.valueOperations);
	}

	@Test
	void getTweetSummary_CacheMiss() {
		UUID tweetId = UUID.randomUUID();
		String key = "tweets::" + tweetId;
		TweetDto tweetDto = new TweetDto();
		given(this.valueOperations.get(key)).willReturn(Mono.empty());
		given(this.tweetClient.getTweetSummary(tweetId)).willReturn(Mono.just(tweetDto));
		given(this.valueOperations.set(eq(key), any(byte[].class), any(Duration.class))).willReturn(Mono.just(true));

		Mono<TweetDto> result = this.tweetService.getTweetSummary(tweetId);

		StepVerifier.create(result).expectNext(tweetDto).verifyComplete();
		verify(this.tweetClient).getTweetSummary(tweetId);
		verify(this.valueOperations).set(eq(key), any(byte[].class), any(Duration.class));
	}

	@Test
	void getUserTweetsSummary_CacheMiss() {
		// When no cached bytes, fetches via TweetClient and writes the full list
		// atomically via opsForValue().set() — no duplicate entries on concurrent misses.
		UUID userId = UUID.randomUUID();
		String key = "user_tweets::" + userId;
		TweetSummaryDto summary = new TweetSummaryDto();
		given(this.valueOperations.get(key)).willReturn(Mono.empty());
		given(this.tweetClient.getUserTweetsSummary(userId)).willReturn(Flux.just(summary));
		given(this.valueOperations.set(eq(key), any(byte[].class), any(Duration.class))).willReturn(Mono.just(true));

		Flux<TweetSummaryDto> result = this.tweetService.getUserTweetsSummary(userId);

		StepVerifier.create(result).expectNext(summary).verifyComplete();
		verify(this.tweetClient).getUserTweetsSummary(userId);
		verify(this.valueOperations).set(eq(key), any(byte[].class), any(Duration.class));
	}

	@Test
	void getPopularHashtags_CacheMiss() {
		// When the popular hashtags key is absent, fetches via TweetClient and writes
		// the full list atomically via opsForValue().set() — no duplicate entries on concurrent misses.
		String key = "popular_hashtags::";
		TweetDto.HashtagDto hashtag = new TweetDto.HashtagDto();
		given(this.valueOperations.get(key)).willReturn(Mono.empty());
		given(this.tweetClient.getPopularHashtags()).willReturn(Flux.just(hashtag));
		given(this.valueOperations.set(eq(key), any(byte[].class), any(Duration.class))).willReturn(Mono.just(true));

		Flux<TweetDto.HashtagDto> result = this.tweetService.getPopularHashtags();

		StepVerifier.create(result).expectNext(hashtag).verifyComplete();
		verify(this.tweetClient).getPopularHashtags();
		verify(this.valueOperations).set(eq(key), any(byte[].class), any(Duration.class));
	}

}
