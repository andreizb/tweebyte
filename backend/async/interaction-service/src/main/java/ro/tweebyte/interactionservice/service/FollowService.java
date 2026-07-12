/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import ro.tweebyte.interactionservice.cache.CountCache;
import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.exception.FollowNotFoundException;
import ro.tweebyte.interactionservice.exception.InteractionException;
import ro.tweebyte.interactionservice.mapper.FollowMapper;
import ro.tweebyte.interactionservice.model.FollowCountsDto;
import ro.tweebyte.interactionservice.model.FollowDto;
import ro.tweebyte.interactionservice.model.FollowingEntryDto;
import ro.tweebyte.interactionservice.model.ProfileInteractionsDto;
import ro.tweebyte.interactionservice.model.TweetInteractionsEntryDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.FollowCountsRow;
import ro.tweebyte.interactionservice.repository.FollowRepository;

@Service
@RequiredArgsConstructor
public class FollowService {

	private static final String FOLLOWING_CACHE = "following_cache";

	// Spring's RedisCache keys this cache as cacheName::key, so the manual evict
	// below must use the same prefix to hit the entry the @Cacheable populate
	// path in RecommendationService writes.
	private static final String FOLLOW_RECOMMENDATIONS_KEY_PREFIX = "follow_recommendations::";

	// Must match UserService's users:: cache key prefix — getFollowing reads those entries
	// in bulk (one MGET) to resolve followed users' display names on the cache-miss fill.
	private static final String USER_SUMMARY_KEY_PREFIX = "users::";

	@Value("${spring.cache.redis.time-to-live}")
	private Duration cacheTtl;

	private final UserService userService;

	private final TweetInteractionsService tweetInteractionsService;

	private final FollowRepository followRepository;

	private final FollowMapper followMapper;

	private final ObjectMapper objectMapper = JsonMapper.builder()
		.addModule(new JavaTimeModule())
		.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
		.build();

	private final ExecutorService executorService;

	private final RedisTemplate<String, byte[]> redisTemplate;

	private final CountCache countCache;

	public CompletableFuture<List<FollowDto>> getFollowers(UUID userId) {
		return CompletableFuture
			.supplyAsync(() -> this.followRepository.findByFollowedIdAndStatusOrderByCreatedAtDesc(userId,
					FollowEntity.Status.ACCEPTED), this.executorService)
			.thenCompose(followEntities -> {
				List<CompletableFuture<FollowDto>> futures = followEntities.stream()
					.map(followEntity -> this.userService.getUserSummary(followEntity.getFollowerId())
						.thenApply(
								userSummary -> this.followMapper.mapEntityToDto(followEntity, userSummary.getUserName())))
					.toList();
				return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
					.thenApply(v -> futures.stream().map(CompletableFuture::join).toList());
			});
	}

	public CompletableFuture<byte[]> getFollowing(UUID userId) {
		String key = FOLLOWING_CACHE + "::" + userId;

		return CompletableFuture.supplyAsync(() -> this.redisTemplate.opsForValue().get(key), this.executorService)
			.thenCompose(cached -> {
				if (cached != null && cached.length > 0) {
					return CompletableFuture.completedFuture(cached);
				}
				return CompletableFuture
					.supplyAsync(() -> this.followRepository.findByFollowerIdAndStatus(userId, FollowEntity.Status.ACCEPTED),
							this.executorService)
					.thenCompose(this::resolveFollowingEntries)
					.thenApply(entries -> {
						try {
							byte[] bytes = this.objectMapper.writeValueAsBytes(entries);
							this.redisTemplate.opsForValue().set(key, bytes, this.cacheTtl);
							return bytes;
						}
						catch (Exception ex) {
							throw new InteractionException(ex);
						}
					});
			});
	}

	// Resolve every followed user's display name with ONE Redis MGET across the page's
	// users:: keys, then collapse the cold users (the ids that missed) into ONE batched
	// user-service call (UserService.getUserSummaries) instead of one GET per cold user — so a
	// cache-miss fill makes at most a single downstream request regardless of how many of the
	// page's users are cold. Cached users deserialise from the MGET payload inline; a cold id
	// the batch didn't return falls back to the per-user read-through, preserving the original
	// resolve-every-edge contract. Entry ordering follows the entities list.
	private CompletableFuture<List<FollowingEntryDto>> resolveFollowingEntries(List<FollowEntity> entities) {
		if (entities.isEmpty()) {
			return CompletableFuture.completedFuture(List.of());
		}
		List<String> keys = entities.stream()
			.map(entity -> USER_SUMMARY_KEY_PREFIX + entity.getFollowedId())
			.toList();
		List<byte[]> cached = this.redisTemplate.opsForValue().multiGet(keys);
		List<UUID> coldIds = new ArrayList<>();
		for (int index = 0; index < entities.size(); index++) {
			byte[] cachedUser = (cached != null) ? cached.get(index) : null;
			if (cachedUser == null || cachedUser.length == 0) {
				coldIds.add(entities.get(index).getFollowedId());
			}
		}
		return this.userService.getUserSummaries(coldIds).thenCompose(coldUsers -> {
			List<CompletableFuture<FollowingEntryDto>> futures = new ArrayList<>(entities.size());
			for (int index = 0; index < entities.size(); index++) {
				byte[] cachedUser = (cached != null) ? cached.get(index) : null;
				futures.add(resolveFollowingEntry(entities.get(index), cachedUser, coldUsers));
			}
			return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
				.thenApply(v -> futures.stream().map(CompletableFuture::join).toList());
		});
	}

	private CompletableFuture<FollowingEntryDto> resolveFollowingEntry(FollowEntity entity, byte[] cachedUser,
			Map<UUID, UserDto> coldUsers) {
		if (cachedUser != null && cachedUser.length > 0) {
			try {
				String userName = this.objectMapper.readValue(cachedUser, UserDto.class).getUserName();
				return CompletableFuture.completedFuture(this.followMapper.mapEntityToEntry(entity, userName));
			}
			catch (IOException ex) {
				return CompletableFuture.failedFuture(new InteractionException(ex));
			}
		}
		UserDto batched = coldUsers.get(entity.getFollowedId());
		if (batched != null) {
			return CompletableFuture.completedFuture(this.followMapper.mapEntityToEntry(entity, batched.getUserName()));
		}
		// Absent from the batch (user-service didn't return it): fall back to the per-user
		// read-through so a genuinely-missing user still surfaces its not-found signal.
		return this.userService.getUserSummary(entity.getFollowedId())
			.thenApply(userSummary -> this.followMapper.mapEntityToEntry(entity, userSummary.getUserName()));
	}

	public CompletableFuture<Long> getFollowersCount(UUID userId) {
		// CountCache is the PRIMARY read (shared followers_count key, kept in step by the
		// follow-accept / unfollow deltas); a miss seeds it directly from the repository —
		// same pattern as getFollowingCount. The miss-loader runs inline on the same
		// ioExecutor task as the cache read, so it never submits a nested task to block on
		// (which at high concurrency would park every pooled thread on work that cannot run).
		return CompletableFuture
			.supplyAsync(() -> this.countCache.get(followersCountKey(userId),
				() -> this.followRepository.countByFollowedIdAndStatus(userId, FollowEntity.Status.ACCEPTED)),
				this.executorService);
	}

	public CompletableFuture<Long> getFollowingCount(UUID userId) {
		// Read-through the per-user following_count key (kept in step by the follow-accept /
		// unfollow deltas); a miss seeds it from one COUNT query.
		return CompletableFuture.supplyAsync(() -> this.countCache.get(followingCountKey(userId),
				() -> this.followRepository.countByFollowerIdAndStatus(userId, FollowEntity.Status.ACCEPTED)),
				this.executorService);
	}

	// Consolidated follower+following counts for the user-profile read. Op-collapsed: ONE Redis
	// MGET for both per-type keys, and on a miss ONE combined COUNT query + ONE EVAL writeback —
	// 3 ops instead of the prior 2 GET + 2 COUNT + 2 SET. Keys stay separate (no combined cache
	// key), so the per-type single-count endpoints still serve the same values.
	public CompletableFuture<FollowCountsDto> getFollowCounts(UUID userId) {
		return CompletableFuture.supplyAsync(() -> {
			long[] counts = this.countCache.getPair(followersCountKey(userId), followingCountKey(userId), () -> {
				FollowCountsRow row = this.followRepository.countFollowersAndFollowing(userId,
						FollowEntity.Status.ACCEPTED.name());
				return new long[] { row.getFollowers(), row.getFollowing() };
			});
			return new FollowCountsDto(counts[0], counts[1]);
		}, this.executorService);
	}

	// Combined user-profile read: the user's follow counts AND the per-tweet interactions for the
	// profile's tweet page in one inbound call, so the user-profile read makes one hop here instead
	// of a follow-counts call plus a nested tweet-interactions call. Combines the two existing reads
	// — getFollowCounts and TweetInteractionsService.getTweetInteractionsEntries (which assembles
	// the per-tweet wire rows directly) — and groups them. An empty tweetIds skips the interactions
	// sub-call entirely and returns an empty list, but still reads the real follow counts.
	public CompletableFuture<ProfileInteractionsDto> getProfileInteractions(UUID userId, List<UUID> tweetIds) {
		CompletableFuture<List<TweetInteractionsEntryDto>> entriesFuture = tweetIds.isEmpty()
				? CompletableFuture.completedFuture(List.of())
				: this.tweetInteractionsService.getTweetInteractionsEntries(tweetIds);
		return getFollowCounts(userId)
			.thenCombine(entriesFuture, (counts, entries) -> new ProfileInteractionsDto(counts, entries));
	}

	public CompletableFuture<List<FollowDto>> getFollowRequests(UUID userId) {
		return CompletableFuture
			.supplyAsync(() -> this.followRepository.findByFollowedIdAndStatusOrderByCreatedAtDesc(userId,
					FollowEntity.Status.PENDING), this.executorService)
			.thenApply(followEntities -> followEntities.stream()
				.map(this.followMapper::mapEntityToDto)
				.toList());
	}

	public CompletableFuture<Void> follow(UUID userId, UUID followedId) {
		// Controller declares @ResponseStatus(NO_CONTENT) so any returned body
		// is discarded by Spring. Skip the FollowDto mapping entirely — saves
		// one allocation per request on the write hot path. A public account
		// accepts immediately and the edge counts on both sides; a private
		// account starts PENDING and only counts once the request is accepted.
		return this.userService.fetchUserSummary(followedId).thenAcceptAsync(userDto -> {
			boolean accepted = !userDto.getIsPrivate();
			FollowEntity entity = this.followMapper.mapRequestToEntity(userId, followedId,
					accepted ? FollowEntity.Status.ACCEPTED : FollowEntity.Status.PENDING);
			try {
				this.followRepository.save(entity);
				// Delta runs only on a genuine accepted insert (before the duplicate is
				// swallowed), so a repeated follow does not inflate the cached counts.
				if (accepted) {
					incrementFollowCounts(userId, followedId);
				}
			}
			catch (DataIntegrityViolationException ignored) {
				// Idempotent follow: the UNIQUE(follower_id, followed_id) edge
				// already exists, so a repeat follow is a no-op success.
			}
			evictFollowerCaches(userId);
		}, this.executorService);
	}

	public CompletableFuture<Void> updateFollowRequest(UUID userId, UUID followRequestId, FollowEntity.Status status) {
		return CompletableFuture
			.supplyAsync(() -> this.followRepository.findById(followRequestId)
				.orElseThrow(() -> new FollowNotFoundException("Follow request not found for id " + followRequestId)),
					this.executorService)
			.thenAccept(followEntity -> {
				if (status == FollowEntity.Status.PENDING
						|| followEntity.getFollowerId().equals(userId) && status == FollowEntity.Status.ACCEPTED) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status update");
				}
				// Only the request's recipient (the followed party) may accept or reject it — a third
				// party holding a valid request id must not be able to act on someone else's request.
				if (!followEntity.getFollowedId().equals(userId)) {
					throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the follow request recipient can update it");
				}
				// Accepting a pending request grows the requester's following set, so it is
				// their recommendations that go stale — not the acceptor's (userId). Evict
				// the requester (the edge's followerId) only on a genuine accept; a no-op
				// re-accept leaves the cache untouched.
				boolean nowAccepted = status == FollowEntity.Status.ACCEPTED
						&& followEntity.getStatus() == FollowEntity.Status.PENDING;
				followEntity.setStatus(status);
				this.followRepository.save(followEntity);
				if (nowAccepted) {
					// Accepting a pending request makes the edge count for the first time —
					// following on the requester, followers on the owner — identical to a
					// public follow. Guarded on the prior PENDING status so a re-accept is inert.
					incrementFollowCounts(followEntity.getFollowerId(), followEntity.getFollowedId());
					evictUserRecommendations(followEntity.getFollowerId());
				}
			});
	}

	public CompletableFuture<List<UUID>> getFollowedIdentifiers(UUID userId) {
		return CompletableFuture
			.supplyAsync(() -> this.followRepository.findByFollowerIdAndStatus(userId, FollowEntity.Status.ACCEPTED),
					this.executorService)
			.thenApply(followEntities -> followEntities.stream()
				.map(FollowEntity::getFollowedId)
				.toList());
	}

	@Async("ioExecutor")
	public CompletableFuture<Void> unfollow(UUID followerId, UUID followedId) {
		// Resolve the edge first so the delta only fires for an edge that was actually
		// counted: a PENDING edge never incremented, so removing it must not decrement. A
		// missing edge (repeated unfollow) deletes nothing and skips the delta. The
		// conditional decrement floors at zero and the 60s TTL reseeds from the database, so
		// the cached counts cannot drift far.
		this.followRepository.findByFollowerIdAndFollowedId(followerId, followedId).ifPresent(edge -> {
			this.followRepository.deleteByFollowerIdAndFollowedId(followerId, followedId);
			if (edge.getStatus() == FollowEntity.Status.ACCEPTED) {
				decrementFollowCounts(followerId, followedId);
			}
		});
		evictFollowerCaches(followerId);
		return CompletableFuture.completedFuture(null);
	}

	private void evictUserRecommendations(UUID userId) {
		this.redisTemplate.delete(FOLLOW_RECOMMENDATIONS_KEY_PREFIX + userId);
	}

	// A follow/unfollow changes the actor's own following set, which invalidates
	// both their cached following list and their follow recommendations. The
	// followed user has no cache that an incoming edge can stale (followers are
	// not cached, and their following/recommendations don't depend on who follows
	// them), so eviction is actor-scoped only.
	private void evictFollowerCaches(UUID userId) {
		this.redisTemplate.delete(List.of(FOLLOWING_CACHE + "::" + userId, FOLLOW_RECOMMENDATIONS_KEY_PREFIX + userId));
	}

	// An accepted edge grows the actor's following count and the target's followers count by
	// one; the conditional script only touches keys already cached, so a cold counter is
	// left for the next read to reseed from the database. Both deltas apply in ONE round-trip.
	private void incrementFollowCounts(UUID followerId, UUID followedId) {
		this.countCache.incrementBoth(followingCountKey(followerId), followersCountKey(followedId));
	}

	private void decrementFollowCounts(UUID followerId, UUID followedId) {
		this.countCache.decrementBoth(followingCountKey(followerId), followersCountKey(followedId));
	}

	private static String followingCountKey(UUID userId) {
		return "following_count::" + userId;
	}

	private static String followersCountKey(UUID userId) {
		return "followers_count::" + userId;
	}

}
