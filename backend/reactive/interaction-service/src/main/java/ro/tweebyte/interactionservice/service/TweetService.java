/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import ro.tweebyte.interactionservice.client.TweetClient;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.TweetSummaryDto;

@Service
@RequiredArgsConstructor
public class TweetService {

	private static final String TWEET_SUMMARY_KEY_PREFIX = "tweets::";

	private static final String USER_TWEETS_SUMMARY_KEY_PREFIX = "user_tweets::";

	private static final String POPULAR_HASHTAGS_KEY = "popular_hashtags::";

	private final TweetClient tweetClient;

	private final ReactiveRedisTemplate<String, byte[]> redisTemplate;

	// findAndRegisterModules() picks up jackson-datatype-jsr310 (LocalDateTime
	// / LocalDate / Instant). Without it Jackson raises
	// "Java 8 date/time type `java.time.LocalDateTime` not supported by default"
	// on every UserDto / TweetDto serialization.
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	// Manual caches here bypass Spring Cache, so spring.cache.redis.time-to-live
	// is not auto-applied — inject it and set expiry explicitly so changing the
	// property propagates to these writes (matches the async stack's 60s TTL).
	@Value("${spring.cache.redis.time-to-live}")
	private Duration cacheTtl;

	public Mono<TweetDto> getTweetSummary(UUID tweetId) {
		String key = TWEET_SUMMARY_KEY_PREFIX + tweetId;
		return this.redisTemplate.opsForValue().get(key).flatMap(bytes -> {
			try {
				return Mono.just(this.objectMapper.readValue(bytes, TweetDto.class));
			}
			catch (Exception ex) {
				return Mono.error(ex);
			}
		}).switchIfEmpty(this.tweetClient.getTweetSummary(tweetId).flatMap(tweetDto -> {
			try {
				return this.redisTemplate.opsForValue()
					.set(key, this.objectMapper.writeValueAsBytes(tweetDto), this.cacheTtl)
					.thenReturn(tweetDto);
			}
			catch (Exception ex) {
				return Mono.error(ex);
			}
		}));
	}

	public Flux<TweetSummaryDto> getUserTweetsSummary(UUID userId) {
		String key = USER_TWEETS_SUMMARY_KEY_PREFIX + userId;
		return this.redisTemplate.opsForValue().get(key).flatMapMany(bytes -> {
			try {
				return Flux.fromIterable(
						this.objectMapper.readValue(bytes, new TypeReference<List<TweetSummaryDto>>() {
						}));
			}
			catch (Exception ex) {
				return Flux.error(ex);
			}
		}).switchIfEmpty(this.tweetClient.getUserTweetsSummary(userId).collectList().flatMap(list -> {
			try {
				return this.redisTemplate.opsForValue()
					.set(key, this.objectMapper.writeValueAsBytes(list), this.cacheTtl)
					.thenReturn(list);
			}
			catch (Exception ex) {
				return Mono.error(ex);
			}
		}).flatMapMany(Flux::fromIterable));
	}

	public Flux<TweetDto.HashtagDto> getPopularHashtags() {
		return this.redisTemplate.opsForValue().get(POPULAR_HASHTAGS_KEY).flatMapMany(bytes -> {
			try {
				return Flux.fromIterable(
						this.objectMapper.readValue(bytes, new TypeReference<List<TweetDto.HashtagDto>>() {
						}));
			}
			catch (Exception ex) {
				return Flux.error(ex);
			}
		}).switchIfEmpty(this.tweetClient.getPopularHashtags().collectList().flatMap(list -> {
			try {
				return this.redisTemplate.opsForValue()
					.set(POPULAR_HASHTAGS_KEY, this.objectMapper.writeValueAsBytes(list), this.cacheTtl)
					.thenReturn(list);
			}
			catch (Exception ex) {
				return Mono.error(ex);
			}
		}).flatMapMany(Flux::fromIterable));
	}

}
