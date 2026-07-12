/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import ro.tweebyte.interactionservice.cache.CacheReads;
import ro.tweebyte.interactionservice.cache.CountCache;
import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.exception.FollowNotFoundException;
import ro.tweebyte.interactionservice.mapper.FollowMapper;
import ro.tweebyte.interactionservice.model.FollowCountsDto;
import ro.tweebyte.interactionservice.model.FollowDto;
import ro.tweebyte.interactionservice.model.FollowingEntryDto;
import ro.tweebyte.interactionservice.model.ProfileInteractionsDto;
import ro.tweebyte.interactionservice.model.Status;
import ro.tweebyte.interactionservice.model.TweetInteractionsEntryDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.FollowRepository;

@Service
public class FollowService {

	private static final String FOLLOWING_CACHE = "following_cache";

	// Must match RecommendationService's USER_RECOMMENDATIONS_KEY_PREFIX so the
	// eviction here targets the same Redis key the recommendations are written to.
	private static final String FOLLOW_RECOMMENDATIONS_KEY_PREFIX = "follow_recommendations::";

	// Must match UserService's users:: cache key prefix — getFollowing reads those entries
	// in bulk (one MGET) to resolve followed users' display names on the cache-miss fill.
	private static final String USER_SUMMARY_KEY_PREFIX = "users::";

	private final UserService userService;

	private final FollowRepository followRepository;

	private final FollowMapper followMapper;

	private final ReactiveRedisTemplate<String, byte[]> redisTemplate;

	private final CountCache countCache;

	private final TweetInteractionsService tweetInteractionsService;

	private final ObjectMapper objectMapper = JsonMapper.builder()
		.addModule(new JavaTimeModule())
		.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
		.build();

	// following_cache write bypasses Spring Cache, so spring.cache.redis.time-to-live
	// is not auto-applied — inject it so the TTL tracks the property (matches the
	// async stack's 60s and the other manual reactive caches).
	@Value("${spring.cache.redis.time-to-live}")
	private Duration cacheTtl;

	@Lazy
	@Resource(name = "followService")
	private FollowService self;

	public FollowService(UserService userService, FollowRepository followRepository, FollowMapper followMapper,
			ReactiveRedisTemplate<String, byte[]> redisTemplate, CountCache countCache,
			TweetInteractionsService tweetInteractionsService) {
		this.userService = userService;
		this.followRepository = followRepository;
		this.followMapper = followMapper;
		this.redisTemplate = redisTemplate;
		this.countCache = countCache;
		this.tweetInteractionsService = tweetInteractionsService;
	}

	public Flux<FollowDto> getFollowers(UUID userId) {
		return this.followRepository.findByFollowedIdAndStatusOrderByCreatedAtDesc(userId, Status.ACCEPTED.name())
			.flatMapSequential(followEntity -> this.userService.getUserSummary(followEntity.getFollowerId())
				.map(userSummary -> this.followMapper.mapEntityToDto(followEntity, userSummary.getUserName())));
	}

	public Mono<byte[]> getFollowing(UUID userId) {
		String key = FOLLOWING_CACHE + "::" + userId;

		return this.redisTemplate.opsForValue()
			.get(key)
			.publishOn(Schedulers.parallel())
			.filter(bytes -> bytes.length > 0)
			.switchIfEmpty(
					Mono.defer(() -> this.followRepository.findByFollowerIdAndStatus(userId, Status.ACCEPTED.name())
						.collectList()
						.flatMap(this::resolveFollowingEntries)
						.flatMap(entries -> {
							try {
								byte[] bytes = this.objectMapper.writeValueAsBytes(entries);
								return this.redisTemplate.opsForValue()
									.set(key, bytes, this.cacheTtl)
									.thenReturn(bytes);
							}
							catch (Exception ex) {
								return Mono.error(ex);
							}
						})));
	}

	// Resolve every followed user's display name with ONE Redis MGET across the page's
	// users:: keys, then collapse the cold users (the ids that missed) into ONE batched
	// user-service call (UserService.getUserSummaries) instead of one GET per cold user — so a
	// cache-miss fill makes at most a single downstream request regardless of how many of the
	// page's users are cold. Cached users deserialise from the MGET payload inline; a cold id
	// the batch didn't return falls back to the per-user read-through, preserving the original
	// resolve-every-edge contract. Entry ordering follows the entities list.
	private Mono<List<FollowingEntryDto>> resolveFollowingEntries(List<FollowEntity> entities) {
		if (entities.isEmpty()) {
			return Mono.just(List.of());
		}
		List<String> keys = entities.stream()
			.map(entity -> USER_SUMMARY_KEY_PREFIX + entity.getFollowedId())
			.toList();
		return CacheReads.offload(this.redisTemplate.opsForValue().multiGet(keys)).flatMap(cached -> {
			List<UUID> coldIds = new ArrayList<>();
			for (int index = 0; index < entities.size(); index++) {
				byte[] cachedUser = cached.get(index);
				if (cachedUser == null || cachedUser.length == 0) {
					coldIds.add(entities.get(index).getFollowedId());
				}
			}
			return this.userService.getUserSummaries(coldIds)
				.flatMap(coldUsers -> Flux.range(0, entities.size())
					.flatMapSequential(index -> resolveFollowingEntry(entities.get(index), cached.get(index), coldUsers))
					.collectList());
		});
	}

	private Mono<FollowingEntryDto> resolveFollowingEntry(FollowEntity entity, byte[] cachedUser,
			Map<UUID, UserDto> coldUsers) {
		if (cachedUser != null && cachedUser.length > 0) {
			return Mono.fromCallable(() -> this.objectMapper.readValue(cachedUser, UserDto.class).getUserName())
				.map(userName -> this.followMapper.mapEntityToEntry(entity, userName));
		}
		UserDto batched = coldUsers.get(entity.getFollowedId());
		if (batched != null) {
			return Mono.just(this.followMapper.mapEntityToEntry(entity, batched.getUserName()));
		}
		// Absent from the batch (user-service didn't return it): fall back to the per-user
		// read-through so a genuinely-missing user still surfaces its not-found signal.
		return this.userService.getUserSummary(entity.getFollowedId())
			.map(userSummary -> this.followMapper.mapEntityToEntry(entity, userSummary.getUserName()));
	}

	public Mono<Long> getFollowersCount(UUID userId) {
		// The loader Mono is obtained through the self proxy so the @CircuitBreaker reactor
		// operator wraps it; passing that proxied Mono to countCache.get makes the breaker (and
		// its getFollowersCountFromCache fallback) engage on the cache-miss subscription. A plain
		// this. call would build the Mono without the AOP advice and the breaker would never trip.
		return this.countCache.get(followersCountKey(userId), this.self.getFollowersCountFromRepo(userId));
	}

	@CircuitBreaker(name = "followersCountCircuitBreaker", fallbackMethod = "getFollowersCountFromCache")
	public Mono<Long> getFollowersCountFromRepo(UUID userId) {
		return this.followRepository.countByFollowedIdAndStatus(userId, Status.ACCEPTED.name());
	}

	public Mono<Long> getFollowersCountFromCache(UUID userId) {
		// No correct followers cache exists to read here: the following_cache holds who the user
		// FOLLOWS, not their followers. Degrade to 0L — a safe placeholder that self-corrects the
		// instant the breaker closes and the real COUNT query succeeds. (Returning the following-list
		// size here, as this once did, surfaced a plausible but wrong followers count.)
		return Mono.just(0L);
	}

	public Mono<Long> getFollowingCount(UUID userId) {
		return this.countCache.get(followingCountKey(userId),
				this.followRepository.countByFollowerIdAndStatus(userId, Status.ACCEPTED.name()));
	}

	// Consolidated follower+following counts for the user-profile read. Op-collapsed: ONE Redis
	// MGET for both per-type keys, and on a miss ONE combined COUNT query + ONE EVAL writeback —
	// 3 ops instead of the prior 2 GET + 2 COUNT + 2 SET. Keys stay separate (no combined cache
	// key), so the per-type single-count endpoints still serve the same values.
	public Mono<FollowCountsDto> getFollowCounts(UUID userId) {
		return this.countCache
			.getPair(followersCountKey(userId), followingCountKey(userId), this.self.getFollowCountsFromRepo(userId))
			.map(counts -> new FollowCountsDto(counts[0], counts[1]));
	}

	// Both counts in one round-trip on a cache miss. Same breaker as the per-type follower
	// count; the proxied this.self call makes the breaker engage on the cache-miss subscription
	// (see getFollowersCount). [0] = followers, [1] = following.
	@CircuitBreaker(name = "followersCountCircuitBreaker", fallbackMethod = "getFollowCountsFromCache")
	public Mono<long[]> getFollowCountsFromRepo(UUID userId) {
		return this.followRepository.countFollowersAndFollowing(userId, Status.ACCEPTED.name())
			.map(row -> new long[] { row.followers(), row.following() });
	}

	public Mono<long[]> getFollowCountsFromCache(UUID userId, Throwable throwable) {
		return getFollowersCountFromCache(userId).map(estimate -> new long[] { estimate, estimate });
	}

	// Combined user-profile read: the user's follow counts AND the per-tweet interactions for the
	// profile's tweet page in one inbound call, so the user-profile read makes one hop here instead
	// of a follow-counts call plus a nested tweet-interactions call. Zips the two existing reads —
	// getFollowCounts and TweetInteractionsService.getTweetInteractionsEntries (which assembles the
	// per-tweet wire rows directly) — and groups them. An empty tweetIds skips the interactions
	// sub-call entirely and returns an empty list, but still reads the real follow counts.
	public Mono<ProfileInteractionsDto> getProfileInteractions(UUID userId, List<UUID> tweetIds) {
		Mono<List<TweetInteractionsEntryDto>> entriesMono = tweetIds.isEmpty() ? Mono.just(List.of())
				: this.tweetInteractionsService.getTweetInteractionsEntries(tweetIds);
		return Mono.zip(getFollowCounts(userId), entriesMono)
			.map(tuple -> new ProfileInteractionsDto(tuple.getT1(), tuple.getT2()));
	}

	public Flux<FollowDto> getFollowRequests(UUID userId) {
		return this.followRepository.findByFollowedIdAndStatusOrderByCreatedAtDesc(userId, Status.PENDING.name())
			.map(this.followMapper::mapEntityToDto);
	}

	public Flux<UUID> getFollowedIdentifiers(UUID userId) {
		return this.followRepository.findByFollowerIdAndStatus(userId, Status.ACCEPTED.name())
			.map(FollowEntity::getFollowedId);
	}

	public Mono<Void> follow(UUID userId, UUID followedId) {
		// Controller declares @ResponseStatus(NO_CONTENT) so any returned body
		// is discarded by Spring. Skip the FollowDto mapping entirely — saves
		// one allocation per request on the write hot path. A public account
		// accepts immediately and the edge counts on both sides; a private
		// account starts PENDING and only counts once the request is accepted.
		return this.userService.fetchUserSummary(followedId).flatMap(userSummary -> {
			boolean accepted = !userSummary.getIsPrivate();
			FollowEntity entity = this.followMapper.mapRequestToEntity(userId, followedId,
					accepted ? Status.ACCEPTED.name() : Status.PENDING.name());
			return this.followRepository.save(entity)
				.flatMap(saved -> accepted ? incrementFollowCounts(userId, followedId).thenReturn(saved)
						: Mono.just(saved));
		})
			// Idempotent follow: the UNIQUE(follower_id, followed_id) edge already
			// exists, so a repeat follow is a no-op success. The count delta runs only
			// on a genuine insert (before the duplicate is swallowed), so a repeated
			// follow does not inflate the cached counts.
			.onErrorResume(DataIntegrityViolationException.class, e -> Mono.empty())
			.then(Mono.defer(() -> evictFollowerCaches(userId)));
	}

	public Mono<Void> updateFollowRequest(UUID userId, UUID followRequestId, Status status) {
		return this.followRepository.findById(followRequestId)
			.switchIfEmpty(
					Mono.error(new FollowNotFoundException("Follow request not found for id " + followRequestId)))
			.flatMap(followEntity -> {
				if (status == Status.PENDING
						|| followEntity.getFollowerId().equals(userId) && status == Status.ACCEPTED) {
					return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status update"));
				}
				// Only the request's recipient (the followed party) may accept or reject it — a third
				// party holding a valid request id must not be able to act on someone else's request.
				if (!followEntity.getFollowedId().equals(userId)) {
					return Mono.error(
							new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the follow request recipient can update it"));
				}
				// Accepting a pending request makes the edge count for the first time —
				// following on the requester, followers on the owner — identical to a
				// public follow. Guard on the prior PENDING status so a re-accept is inert.
				boolean nowAccepted = status == Status.ACCEPTED && Status.PENDING.name().equals(followEntity.getStatus());
				followEntity.setStatus(status.name());
				return this.followRepository.save(followEntity)
					.flatMap(saved -> nowAccepted
							? incrementFollowCounts(saved.getFollowerId(), saved.getFollowedId())
								// The requester (the edge's followerId) is the party whose
								// following set grows on accept, so it is their recommendations
								// that go stale — not the acceptor's (userId). Evict only on a
								// genuine accept; a no-op re-accept leaves the cache untouched.
								.then(Mono.defer(() -> evictUserRecommendations(saved.getFollowerId())))
								.thenReturn(saved)
							: Mono.just(saved));
			})
			.then();
	}

	public Mono<Void> unfollow(UUID followerId, UUID followedId) {
		// Resolve the edge first so the delta only fires for an edge that was actually
		// counted: a PENDING edge never incremented, so removing it must not decrement.
		// A missing edge (repeated unfollow) deletes nothing and skips the delta. The
		// conditional decrement floors at zero and the 60s TTL reseeds from the database,
		// so the cached counts cannot drift far.
		return this.followRepository.findByFollowerIdAndFollowedId(followerId, followedId)
			.flatMap(edge -> this.followRepository.deleteByFollowerIdAndFollowedId(followerId, followedId)
				.then(Status.ACCEPTED.name().equals(edge.getStatus()) ? decrementFollowCounts(followerId, followedId)
						: Mono.empty()))
			.then(Mono.defer(() -> evictFollowerCaches(followerId)));
	}

	private Mono<Void> evictUserRecommendations(UUID userId) {
		return this.redisTemplate.delete(FOLLOW_RECOMMENDATIONS_KEY_PREFIX + userId).then();
	}

	// A follow/unfollow changes the actor's own following set, which invalidates
	// both their cached following list and their follow recommendations. The
	// followed user has no cache that an incoming edge can stale (followers are
	// not cached, and their following/recommendations don't depend on who follows
	// them), so eviction is actor-scoped only.
	private Mono<Void> evictFollowerCaches(UUID userId) {
		return this.redisTemplate.delete(FOLLOWING_CACHE + "::" + userId, FOLLOW_RECOMMENDATIONS_KEY_PREFIX + userId)
			.then();
	}

	// An accepted edge grows the actor's following count and the target's followers
	// count by one; the conditional script only touches keys already cached, so a cold
	// counter is left for the next read to reseed from the database. Both deltas apply
	// in ONE round-trip instead of two serial INCRs.
	private Mono<Void> incrementFollowCounts(UUID followerId, UUID followedId) {
		return this.countCache.incrementBoth(followingCountKey(followerId), followersCountKey(followedId));
	}

	private Mono<Void> decrementFollowCounts(UUID followerId, UUID followedId) {
		return this.countCache.decrementBoth(followingCountKey(followerId), followersCountKey(followedId));
	}

	private static String followingCountKey(UUID userId) {
		return "following_count::" + userId;
	}

	private static String followersCountKey(UUID userId) {
		return "followers_count::" + userId;
	}

}
