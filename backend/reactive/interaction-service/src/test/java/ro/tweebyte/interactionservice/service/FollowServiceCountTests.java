/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.interactionservice.cache.CountCache;
import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.mapper.FollowMapper;
import ro.tweebyte.interactionservice.model.Status;
import ro.tweebyte.interactionservice.repository.FollowRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Covers the FollowService follower-count source-of-truth read (getFollowersCountFromRepo),
 * the getFollowersCountFromCache empty/missing-key arms, and the unfollow branches where the
 * removed edge was PENDING (no decrement) or absent (no-op).
 */
@ExtendWith(SpringExtension.class)
class FollowServiceCountTests {

	private FollowService followService;

	@Mock
	private UserService userService;

	@Mock
	private FollowRepository followRepository;

	@Mock
	private FollowMapper followMapper;

	@Mock
	private ReactiveRedisTemplate<String, byte[]> redisTemplate;

	@Mock
	private ReactiveValueOperations<String, byte[]> valueOperations;

	@Mock
	private CountCache countCache;

	@Mock
	private TweetInteractionsService tweetInteractionsService;

	private final UUID userId = UUID.randomUUID();

	private final UUID followedId = UUID.randomUUID();

	@BeforeEach
	void init() {
		this.followService = new FollowService(this.userService, this.followRepository, this.followMapper,
				this.redisTemplate, this.countCache, this.tweetInteractionsService);
		ReflectionTestUtils.setField(this.followService, "self", this.followService);
		ReflectionTestUtils.setField(this.followService, "cacheTtl", Duration.ofSeconds(60));
	}

	@Test
	void getFollowersCountFromRepo_CountsAcceptedEdges() {
		given(this.followRepository.countByFollowedIdAndStatus(this.userId, Status.ACCEPTED.name()))
			.willReturn(Mono.just(12L));

		StepVerifier.create(this.followService.getFollowersCountFromRepo(this.userId)).expectNext(12L).verifyComplete();
	}

	@Test
	void getFollowersCountFromCache_EmptyBytes_ReturnsZero() {
		given(this.redisTemplate.opsForValue()).willReturn(this.valueOperations);
		given(this.valueOperations.get(anyString())).willReturn(Mono.just(new byte[0]));

		StepVerifier.create(this.followService.getFollowersCountFromCache(this.userId)).expectNext(0L).verifyComplete();
	}

	@Test
	void getFollowersCountFromCache_MissingKey_DefaultsZero() {
		given(this.redisTemplate.opsForValue()).willReturn(this.valueOperations);
		given(this.valueOperations.get(anyString())).willReturn(Mono.empty());

		StepVerifier.create(this.followService.getFollowersCountFromCache(this.userId)).expectNext(0L).verifyComplete();
	}

	@Test
	void getFollowersCountFromCache_AnyInput_ReturnsZero() {
		// getFollowersCountFromCache now returns Mono.just(0L) unconditionally — the
		// following_cache holds who the user follows, not their followers, so any attempt
		// to derive a followers count from it was wrong. The fallback degrades to 0L and
		// input bytes are never read; corrupt bytes no longer raise InteractionException.
		StepVerifier.create(this.followService.getFollowersCountFromCache(this.userId)).expectNext(0L).verifyComplete();
	}

	@Test
	void unfollow_PendingEdge_DeletesWithoutDecrement() {
		// A PENDING edge never incremented the counters, so removing it must not decrement.
		FollowEntity pending = new FollowEntity();
		pending.setStatus(Status.PENDING.name());
		given(this.followRepository.findByFollowerIdAndFollowedId(this.userId, this.followedId))
			.willReturn(Mono.just(pending));
		given(this.followRepository.deleteByFollowerIdAndFollowedId(this.userId, this.followedId))
			.willReturn(Mono.empty());
		given(this.redisTemplate.delete(anyString(), anyString())).willReturn(Mono.just(2L));

		StepVerifier.create(this.followService.unfollow(this.userId, this.followedId)).verifyComplete();

		verify(this.countCache, never()).decrementBoth(anyString(), anyString());
		verify(this.followRepository).deleteByFollowerIdAndFollowedId(this.userId, this.followedId);
	}

	@Test
	void unfollow_MissingEdge_NoOpButStillEvicts() {
		// A repeated unfollow finds no edge: nothing is deleted or decremented, but the
		// actor's caches are still evicted via the trailing then(defer(...)).
		given(this.followRepository.findByFollowerIdAndFollowedId(this.userId, this.followedId))
			.willReturn(Mono.empty());
		given(this.redisTemplate.delete(anyString(), anyString())).willReturn(Mono.just(0L));

		StepVerifier.create(this.followService.unfollow(this.userId, this.followedId)).verifyComplete();

		verify(this.followRepository, never()).deleteByFollowerIdAndFollowedId(any(), any());
		verify(this.countCache, never()).decrementBoth(anyString(), anyString());
		verify(this.redisTemplate).delete(anyString(), anyString());
	}

	@Test
	void updateFollowRequest_AcceptPending_IncrementsCounts() {
		// The accept-of-pending arm: nowAccepted is true so both counters increment.
		UUID followRequestId = UUID.randomUUID();
		UUID requesterId = UUID.randomUUID();
		FollowEntity entity = new FollowEntity();
		entity.setId(followRequestId);
		entity.setFollowerId(requesterId);
		entity.setFollowedId(this.userId);
		entity.setStatus(Status.PENDING.name());

		given(this.followRepository.findById(followRequestId)).willReturn(Mono.just(entity));
		given(this.followRepository.save(any(FollowEntity.class))).willReturn(Mono.just(entity));
		given(this.countCache.incrementBoth(anyString(), anyString())).willReturn(Mono.empty());
		given(this.redisTemplate.delete(anyString())).willReturn(Mono.just(1L));

		StepVerifier.create(this.followService.updateFollowRequest(this.userId, followRequestId, Status.ACCEPTED))
			.verifyComplete();

		// Both counters apply in ONE batched round-trip (following on the requester, followers on the owner).
		verify(this.countCache).incrementBoth("following_count::" + requesterId, "followers_count::" + this.userId);
		// On a genuine accept the requester's recommendations go stale, so the eviction
		// must target the requester (the edge's followerId) — not the acceptor.
		verify(this.redisTemplate).delete("follow_recommendations::" + requesterId);
	}

	@Test
	void getFollowingCount_DelegatesToCache() {
		given(this.countCache.get(anyString(), any())).willAnswer(invocation -> invocation.getArgument(1));
		given(this.followRepository.countByFollowerIdAndStatus(eq(this.userId), eq(Status.ACCEPTED.name())))
			.willReturn(Mono.just(8L));

		StepVerifier.create(this.followService.getFollowingCount(this.userId)).expectNext(8L).verifyComplete();
	}

}
