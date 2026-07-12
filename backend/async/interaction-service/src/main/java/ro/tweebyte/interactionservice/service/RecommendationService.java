/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.TweetSummaryDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.FollowRepository;

@Service
@RequiredArgsConstructor
public class RecommendationService {

	private final CacheManager cacheManager;

	private final UserService userService;

	private final TweetService tweetService;

	private final FollowRepository followRepository;

	private final LikeService likeService;

	private final RetweetService retweetService;

	private final ExecutorService executorService;

	@Lazy
	@Resource(name = "recommendationService")
	private RecommendationService self;

	/*
	 * Both `computePopularUsers` and `fetchPopularHashtags` are also called lazily on
	 * cache miss (see `getUserRecommendations` and the `@Cacheable("popular_hashtags")`
	 * flow). The reactive RecommendationService has no scheduled pre-compute at all — it
	 * relies entirely on the lazy cache-miss path. To keep FE-equivalent behaviour across
	 * stacks, the async stack relies on lazy cache-miss only as well; any future pre-warm
	 * scheduler must be enabled on BOTH stacks together with the same 24-hour initial
	 * delay.
	 */

	public CompletableFuture<List<UserDto>> recommendUsersToFollow(UUID userId) {
		return CompletableFuture.supplyAsync(() -> this.self.getUserRecommendations(userId), this.executorService);
	}

	@Cacheable(value = "follow_recommendations", key = "#userId", unless = "#result.isEmpty()")
	public List<UserDto> getUserRecommendations(UUID userId) {
		Set<UUID> recommendations = new HashSet<>();

		List<FollowEntity> followedEntities = this.followRepository.findByFollowerIdAndStatus(userId,
				FollowEntity.Status.ACCEPTED);

		Set<UUID> followedIds = followedEntities.stream().map(FollowEntity::getFollowedId).collect(Collectors.toSet());

		// Friends-of-friends in one IN-list query instead of one lookup per followed id
		// (K+1 → 1). The candidate set is identical: the followed users' accepted edges,
		// minus anyone already followed and the user themselves.
		if (!followedIds.isEmpty()) {
			this.followRepository.findByFollowerIdInAndStatus(followedIds, FollowEntity.Status.ACCEPTED)
				.forEach(entity -> {
					if (!followedIds.contains(entity.getFollowedId()) && !userId.equals(entity.getFollowedId())) {
						recommendations.add(entity.getFollowedId());
					}
				});
		}

		fetchPopularUsers().forEach(popularId -> {
			if (!followedIds.contains(popularId) && !userId.equals(popularId)) {
				recommendations.add(popularId);
			}
		});

		List<CompletableFuture<UserDto>> userFutures = recommendations.stream()
			.map(this.userService::getUserSummary)
			.toList();

		// Sort by user id so the recommendation list is deterministic and
		// byte-identical with the reactive stack (HashSet/Flux iteration order
		// is otherwise unspecified).
		return userFutures.stream()
			.map(CompletableFuture::join)
			.sorted(Comparator.comparing(UserDto::getId))
			.toList();
	}

	// sync = true is the single-flight guard: on concurrent cache misses only one thread
	// runs the population (the whole-population scoring fan-out below), and the rest wait
	// for its result instead of all stampeding the recompute. The candidate-capping /
	// SQL-aggregate rewrite the review also flagged is deferred (too risky for
	// byte-identical async/reactive output).
	// NB: sync=true (the single-flight guard) is mutually exclusive with `unless` in Spring
	// Cache, so an empty popular_users map is cached for the TTL window — harmless (it just
	// re-scores after expiry) and the stampede guard is the point of this cache.
	@Cacheable(value = "popular_users", key = "'p0'", sync = true)
	@SneakyThrows
	public Map<UUID, Double> computePopularUsers() {
		return CompletableFuture.supplyAsync(this.followRepository::findAllFollowedIds, this.executorService).thenCompose(userIds -> {
			List<CompletableFuture<AbstractMap.SimpleEntry<UUID, Double>>> scoreFutures = userIds.stream()
				.map(userId -> calculateUserScore(userId)
					.thenApply(score -> new AbstractMap.SimpleEntry<>(userId, score)))
				.toList();

			return CompletableFuture.allOf(scoreFutures.toArray(new CompletableFuture[0]))
				.thenApply(v -> scoreFutures.stream()
					.map(CompletableFuture::join)
					.sorted(Map.Entry.<UUID, Double>comparingByValue(Comparator.reverseOrder())
						.thenComparing(Map.Entry.comparingByKey()))
					.limit(1000L)
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1,
							LinkedHashMap::new)));
		}).get();
	}

	private CompletableFuture<Double> calculateUserScore(UUID userId) {
		CompletableFuture<List<UUID>> tweetIdsFuture = this.tweetService.getUserTweetsSummary(userId)
			.thenApplyAsync(tweetSummaries -> tweetSummaries.stream()
				.map(TweetSummaryDto::getId)
				.toList(), this.executorService);

		CompletableFuture<Long> followersCountFuture = CompletableFuture
			.supplyAsync(() -> this.followRepository.countByFollowedIdAndStatus(userId, FollowEntity.Status.ACCEPTED),
					this.executorService);

		// Score over EVERY tweet the user posted (the summary is unpaged), with a
		// single GROUP BY aggregate per relation instead of one count query per
		// tweet. getTweetLikesCounts/getRetweetCountsForTweets short-circuit an
		// empty id list to an empty map, so a user with no tweets scores 0.
		CompletableFuture<Long> likesCountFuture = tweetIdsFuture
			.thenComposeAsync(tweetIds -> this.likeService.getTweetLikesCounts(tweetIds)
				.thenApply(counts -> counts.values().stream().mapToLong(Long::longValue).sum()),
					this.executorService);

		CompletableFuture<Long> retweetsCountFuture = tweetIdsFuture
			.thenComposeAsync(tweetIds -> this.retweetService.getRetweetCountsForTweets(tweetIds)
				.thenApply(counts -> counts.values().stream().mapToLong(Long::longValue).sum()),
					this.executorService);

		return CompletableFuture.allOf(followersCountFuture, likesCountFuture, retweetsCountFuture).thenApply(v -> {
			double followersCount = followersCountFuture.join();
			double likesCount = likesCountFuture.join();
			double retweetsCount = retweetsCountFuture.join();
			return followersCount + likesCount + retweetsCount;
		});
	}

	@SuppressWarnings("unchecked")
	private Collection<UUID> fetchPopularUsers() {
		Cache cache = this.cacheManager.getCache("popular_users");
		if (cache != null) {
			Map<UUID, Double> popularUsersMap = cache.get("p0", Map.class);
			if (popularUsersMap != null) {
				return popularUsersMap.keySet();
			}
		}

		return this.self.computePopularUsers().keySet();
	}

	@Async
	public CompletableFuture<List<TweetDto.HashtagDto>> fetchPopularHashtags() {
		return this.tweetService.getPopularHashtags();
	}

}
