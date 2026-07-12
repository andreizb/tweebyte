/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.exception.InteractionException;
import ro.tweebyte.interactionservice.model.Status;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.TweetSummaryDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.FollowRepository;

@Service
@RequiredArgsConstructor
public class RecommendationService {

	// Must match the keyspace the RedisCacheManager uses for the
	// "follow_recommendations" cache (CacheKeyPrefix.simple() = name + "::"),
	// because FollowService evicts via cacheManager.getCache("follow_recommendations")
	// .evict(userId). A single-colon prefix here wrote to a different key than the
	// evict targeted, so eviction was a no-op and recommendations went stale forever.
	private static final String USER_RECOMMENDATIONS_KEY_PREFIX = "follow_recommendations::";

	private static final String POPULAR_USERS_KEY = "popular_users::";

	private final UserService userService;

	private final TweetService tweetService;

	private final FollowRepository followRepository;

	private final LikeService likeService;

	private final RetweetService retweetService;

	private final ReactiveRedisTemplate<String, String> redisTemplate;

	// register JSR310 module (LocalDateTime support). See TweetService note.
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	// Manual caches bypass Spring Cache; inject the shared TTL so these writes
	// expire on the same 60s schedule as the async stack (see TweetService note).
	@Value("${spring.cache.redis.time-to-live}")
	private Duration cacheTtl;

	@Lazy
	@Resource(name = "recommendationService")
	private RecommendationService self;

	// Single-flight guard mirroring the async stack's @Cacheable(sync = true): concurrent
	// cache misses share one in-flight scoring fan-out instead of each stampeding the
	// recompute. cache(cacheTtl) replays the one result to all subscribers for the TTL
	// window, then expires so the next miss recomputes. The candidate-capping /
	// SQL-aggregate rewrite the review also flagged is deferred (too risky for
	// byte-identical async/reactive output).
	private Mono<Map<UUID, Double>> popularUsersSingleFlight;

	// Build the shared single-flight Mono once cacheTtl is injected (PostConstruct runs
	// single-threaded during bean init, before any request). The getter also wires it
	// lazily so a direct (non-Spring) construction in unit tests works without lifecycle.
	@PostConstruct
	void initPopularUsersSingleFlight() {
		this.popularUsersSingleFlight = computePopularUsers().cache(this.cacheTtl);
	}

	private Mono<Map<UUID, Double>> popularUsersSingleFlight() {
		if (this.popularUsersSingleFlight == null) {
			initPopularUsersSingleFlight();
		}
		return this.popularUsersSingleFlight;
	}

	public Flux<UserDto> recommendUsersToFollow(UUID userId) {
		return this.self.getUserRecommendations(userId);
	}

	public Flux<UserDto> getUserRecommendations(UUID userId) {
		String key = USER_RECOMMENDATIONS_KEY_PREFIX + userId;
		return this.redisTemplate.opsForValue().get(key).map(json -> {
			try {
				return this.objectMapper.readValue(json, new TypeReference<List<UserDto>>() {
				});
			}
			catch (JsonProcessingException ex) {
				throw new InteractionException(ex);
			}
		})
			.flatMapMany(Flux::fromIterable)
			.switchIfEmpty(fetchUserRecommendations(userId).collectList().flatMap(userDtoList -> {
				String json;
				try {
					json = this.objectMapper.writeValueAsString(userDtoList);
				}
				catch (JsonProcessingException ex) {
					return Mono.error(new InteractionException(ex));
				}
				return this.redisTemplate.opsForValue().set(key, json, this.cacheTtl).thenReturn(userDtoList);
			}).flatMapMany(Flux::fromIterable));
	}

	// Returns a Mono so the caller can compose it via flatMapMany — required
	// because WebFlux's Netty event loop forbids blocking calls and would
	// surface "block()/blockFirst()/blockLast() are blocking, which is not
	// supported in thread reactor-tcp-nio-N" if any subscription chain blocked here.
	public Mono<Map<UUID, Double>> fetchPopularUsers() {
		return this.redisTemplate.opsForValue().get(POPULAR_USERS_KEY).map(json -> {
			try {
				return this.objectMapper.readValue(json, new TypeReference<Map<UUID, Double>>() {
				});
			}
			catch (JsonProcessingException ex) {
				throw new InteractionException(ex);
			}
		}).switchIfEmpty(popularUsersSingleFlight().flatMap(popularUsers -> {
			String json;
			try {
				json = this.objectMapper.writeValueAsString(popularUsers);
			}
			catch (JsonProcessingException ex) {
				return Mono.error(new InteractionException(ex));
			}
			return this.redisTemplate.opsForValue().set(POPULAR_USERS_KEY, json, this.cacheTtl)
				.thenReturn(popularUsers);
		}));
	}

	public Flux<TweetDto.HashtagDto> fetchPopularHashtags() {
		return this.tweetService.getPopularHashtags();
	}

	private Flux<UserDto> fetchUserRecommendations(UUID userId) {
		return this.followRepository.findByFollowerIdAndStatus(userId, Status.ACCEPTED.name())
			.collectList()
			.flatMapMany(followedEntities -> {
				Set<UUID> followedIds = followedEntities.stream()
					.map(FollowEntity::getFollowedId)
					.collect(Collectors.toSet());

				// Friends-of-friends in one IN-list query instead of one lookup per
				// followed id (K+1 → 1). The candidate set is identical: the followed
				// users' accepted edges, minus anyone already followed and the user
				// themselves. An empty followed set short-circuits to an empty Flux.
				Flux<UUID> recommendationsFlux = followedIds.isEmpty() ? Flux.empty()
						: this.followRepository.findByFollowerIdInAndStatus(followedIds, Status.ACCEPTED.name())
							.filter(entity -> !followedIds.contains(entity.getFollowedId())
									&& !userId.equals(entity.getFollowedId()))
							.map(FollowEntity::getFollowedId)
							.distinct();

				// composed via flatMapMany so fetchPopularUsers stays non-blocking.
				Flux<UUID> popularUsersFlux = fetchPopularUsers()
					.flatMapMany(popularUsers -> Flux.fromIterable(popularUsers.keySet()))
					.filter(popularId -> !followedIds.contains(popularId) && !userId.equals(popularId));

				// Sort by user id so the recommendation list is deterministic
				// and byte-identical with the async stack (concat/flatMap order
				// is otherwise unspecified).
				return Flux.concat(recommendationsFlux, popularUsersFlux)
					.distinct()
					.flatMap(this.userService::getUserSummary)
					.sort(Comparator.comparing(UserDto::getId));
			});
	}

	private Mono<Map<UUID, Double>> computePopularUsers() {
		return this.followRepository.findAllFollowedIds()
			.flatMap(this::calculateUserScore)
			.collectSortedList(Map.Entry.<UUID, Double>comparingByValue(Comparator.reverseOrder())
				.thenComparing(Map.Entry.comparingByKey()))
			.map(sortedList -> sortedList.stream()
				.limit(1000)
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new)));
	}

	private Mono<Map.Entry<UUID, Double>> calculateUserScore(UUID userId) {
		Mono<List<UUID>> tweetIdsMono = this.tweetService.getUserTweetsSummary(userId)
			.map(TweetSummaryDto::getId)
			.collectList();

		Mono<Long> followersCountMono = this.followRepository.countByFollowedIdAndStatus(userId,
				Status.ACCEPTED.name());

		// Score over EVERY tweet the user posted (the summary is unpaged), with a
		// single GROUP BY aggregate per relation instead of one count query per
		// tweet. getTweetLikesCounts/getRetweetCountsForTweets short-circuit an
		// empty id list to an empty map, so a user with no tweets scores 0.
		return tweetIdsMono.flatMap(tweetIds -> {
			Mono<Long> likesCountMono = this.likeService.getTweetLikesCounts(tweetIds)
				.map(counts -> counts.values().stream().mapToLong(Long::longValue).sum());
			Mono<Long> retweetsCountMono = this.retweetService.getRetweetCountsForTweets(tweetIds)
				.map(counts -> counts.values().stream().mapToLong(Long::longValue).sum());
			return Mono.zip(followersCountMono, likesCountMono, retweetsCountMono).map(data -> {
				long sum = data.getT1() + data.getT2() + data.getT3();
				return new AbstractMap.SimpleEntry<>(userId, (double) sum);
			});
		});
	}

}
