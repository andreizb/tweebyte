/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

/**
 * Branch coverage for TweetService — exercises the cache-HIT deserialization arms (which the
 * cache-MISS suite leaves open) and the malformed-cache parse-error arms for the single
 * tweet summary, the user-tweets list, and the popular-hashtags list.
 */
class TweetServiceBranchTests {

	private ReactiveRedisTemplate<String, byte[]> redisTemplate;

	private TweetClient tweetClient;

	private TweetService tweetService;

	private ReactiveValueOperations<String, byte[]> valueOperations;

	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		this.redisTemplate = mock(ReactiveRedisTemplate.class);
		this.tweetClient = mock(TweetClient.class);
		this.tweetService = new TweetService(this.tweetClient, this.redisTemplate);
		ReflectionTestUtils.setField(this.tweetService, "cacheTtl", Duration.ofSeconds(60));
		this.valueOperations = mock(ReactiveValueOperations.class);
		given(this.redisTemplate.opsForValue()).willReturn(this.valueOperations);
		// switchIfEmpty(...) eagerly assembles its inner pipeline (the TweetClient call)
		// even on a cache hit, so the client must return a non-null publisher at assembly
		// time. It is never subscribed on the hit/parse-error paths under test.
		given(this.tweetClient.getTweetSummary(any())).willReturn(Mono.empty());
		given(this.tweetClient.getUserTweetsSummary(any())).willReturn(Flux.empty());
		given(this.tweetClient.getPopularHashtags()).willReturn(Flux.empty());
	}

	@Test
	void getTweetSummary_CacheHit_DeserializesWithoutClient() throws Exception {
		UUID tweetId = UUID.randomUUID();
		String key = "tweets::" + tweetId;
		TweetDto cached = new TweetDto();
		cached.setId(tweetId);
		given(this.valueOperations.get(key)).willReturn(Mono.just(this.objectMapper.writeValueAsBytes(cached)));

		StepVerifier.create(this.tweetService.getTweetSummary(tweetId))
			.expectNextMatches(dto -> tweetId.equals(dto.getId()))
			.verifyComplete();
	}

	@Test
	void getTweetSummary_CacheHitMalformed_PropagatesParseError() {
		UUID tweetId = UUID.randomUUID();
		String key = "tweets::" + tweetId;
		given(this.valueOperations.get(key)).willReturn(Mono.just("not-json".getBytes()));

		StepVerifier.create(this.tweetService.getTweetSummary(tweetId)).expectError().verify();
	}

	@Test
	void getUserTweetsSummary_CacheHit_DeserializesWithoutClient() throws Exception {
		UUID userId = UUID.randomUUID();
		String key = "user_tweets::" + userId;
		TweetSummaryDto cached = new TweetSummaryDto();
		cached.setId(UUID.randomUUID());
		// Cache stores the whole list as a JSON array in a single String key.
		given(this.valueOperations.get(key))
			.willReturn(Mono.just(this.objectMapper.writeValueAsBytes(List.of(cached))));

		StepVerifier.create(this.tweetService.getUserTweetsSummary(userId))
			.expectNextMatches(dto -> cached.getId().equals(dto.getId()))
			.verifyComplete();
	}

	@Test
	void getUserTweetsSummary_CacheHitMalformed_PropagatesParseError() {
		UUID userId = UUID.randomUUID();
		String key = "user_tweets::" + userId;
		given(this.valueOperations.get(key)).willReturn(Mono.just("not-json".getBytes()));

		StepVerifier.create(this.tweetService.getUserTweetsSummary(userId)).expectError().verify();
	}

	@Test
	void getPopularHashtags_CacheHit_DeserializesWithoutClient() throws Exception {
		String key = "popular_hashtags::";
		TweetDto.HashtagDto cached = new TweetDto.HashtagDto();
		// Cache stores the whole list as a JSON array in a single String key.
		given(this.valueOperations.get(key))
			.willReturn(Mono.just(this.objectMapper.writeValueAsBytes(List.of(cached))));

		StepVerifier.create(this.tweetService.getPopularHashtags()).expectNextCount(1).verifyComplete();
	}

	@Test
	void getPopularHashtags_CacheHitMalformed_PropagatesParseError() {
		String key = "popular_hashtags::";
		given(this.valueOperations.get(key)).willReturn(Mono.just("not-json".getBytes()));

		StepVerifier.create(this.tweetService.getPopularHashtags()).expectError().verify();
	}

	@Test
	void getTweetSummary_CacheMissWriteBackSerializationFails_PropagatesError() throws Exception {
		// Cache misses, the client resolves the tweet, but serialising it for the SET write-back
		// throws — the catch arm in the switchIfEmpty branch must surface the error.
		installThrowingMapper();
		UUID tweetId = UUID.randomUUID();
		String key = "tweets::" + tweetId;
		given(this.valueOperations.get(key)).willReturn(Mono.empty());
		given(this.tweetClient.getTweetSummary(tweetId)).willReturn(Mono.just(new TweetDto()));

		StepVerifier.create(this.tweetService.getTweetSummary(tweetId)).expectError(JsonProcessingException.class).verify();
	}

	@Test
	void getUserTweetsSummary_CacheMissWriteBackSerializationFails_PropagatesError() throws Exception {
		installThrowingMapper();
		UUID userId = UUID.randomUUID();
		String key = "user_tweets::" + userId;
		given(this.valueOperations.get(key)).willReturn(Mono.empty());
		given(this.tweetClient.getUserTweetsSummary(userId)).willReturn(Flux.just(new TweetSummaryDto()));

		StepVerifier.create(this.tweetService.getUserTweetsSummary(userId))
			.expectError(JsonProcessingException.class)
			.verify();
	}

	@Test
	void getPopularHashtags_CacheMissWriteBackSerializationFails_PropagatesError() throws Exception {
		installThrowingMapper();
		String key = "popular_hashtags::";
		given(this.valueOperations.get(key)).willReturn(Mono.empty());
		given(this.tweetClient.getPopularHashtags()).willReturn(Flux.just(new TweetDto.HashtagDto()));

		StepVerifier.create(this.tweetService.getPopularHashtags()).expectError(JsonProcessingException.class).verify();
	}

	// Swap in an ObjectMapper whose writeValueAsBytes always throws, reaching the write-back catch
	// arms. JsonProcessingException's constructor is protected, hence the anonymous subclass.
	private void installThrowingMapper() throws JsonProcessingException {
		JsonProcessingException failure = new JsonProcessingException("boom") {
		};
		ObjectMapper throwingMapper = spy(new ObjectMapper().findAndRegisterModules());
		willThrow(failure).given(throwingMapper).writeValueAsBytes(any());
		ReflectionTestUtils.setField(this.tweetService, "objectMapper", throwingMapper);
	}

}
