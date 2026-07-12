/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.interactionservice.cache.CountCache;
import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.mapper.FollowMapper;
import ro.tweebyte.interactionservice.model.FollowingEntryDto;
import ro.tweebyte.interactionservice.model.Status;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.FollowRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Branch coverage for FollowService: - getFollowing cache-miss path including the Redis
 * write, - getFollowing cache-hit "empty bytes" filter, - follow() against a private user
 * (Status.PENDING branch), - updateFollowRequest invalid-status branches (PENDING update,
 * follower-self ACCEPT).
 */
@ExtendWith(SpringExtension.class)
class FollowServiceBranchTests {

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
		given(this.redisTemplate.opsForValue()).willReturn(this.valueOperations);
		this.followService = new FollowService(this.userService, this.followRepository, this.followMapper,
				this.redisTemplate, this.countCache, this.tweetInteractionsService);
		ReflectionTestUtils.setField(this.followService, "self", this.followService);
		// @Value-injected TTL is null without a Spring context; set it so the
		// getFollowing cache-miss set(...) write passes a real Duration.
		ReflectionTestUtils.setField(this.followService, "cacheTtl", Duration.ofSeconds(60));
	}

	@Test
	void getFollowing_CacheMiss_QueriesRepoAndStoresInRedis() {
		// Covers the .switchIfEmpty deferred path of getFollowing — empty
		// cache leads to repository fetch, JSON serialisation, and Redis SET.
		FollowEntity entity = new FollowEntity();
		entity.setId(UUID.randomUUID());
		entity.setFollowerId(this.userId);
		entity.setFollowedId(this.followedId);
		FollowingEntryDto entry = new FollowingEntryDto(this.followedId, "testUser", null);
		UserDto userSummary = new UserDto();
		userSummary.setUserName("testUser");

		given(this.valueOperations.get(anyString())).willReturn(Mono.empty());
		given(this.followRepository.findByFollowerIdAndStatus(this.userId, Status.ACCEPTED.name()))
			.willReturn(Flux.just(entity));
		// One MGET resolves the page's users:: names; the cold ids (here the single null entry)
		// are collapsed into ONE batched getUserSummaries call rather than a per-user read.
		given(this.valueOperations.multiGet(any())).willReturn(Mono.just(Collections.singletonList((byte[]) null)));
		given(this.userService.getUserSummaries(List.of(this.followedId)))
			.willReturn(Mono.just(Map.of(this.followedId, userSummary)));
		given(this.followMapper.mapEntityToEntry(eq(entity), anyString())).willReturn(entry);
		given(this.valueOperations.set(anyString(), any(byte[].class), any(Duration.class)))
			.willReturn(Mono.just(true));

		StepVerifier.create(this.followService.getFollowing(this.userId))
			.expectNextMatches(b -> b != null && b.length > 0)
			.verifyComplete();

		verify(this.followRepository).findByFollowerIdAndStatus(this.userId, Status.ACCEPTED.name());
		verify(this.valueOperations).set(anyString(), any(byte[].class), any(Duration.class));
	}

	@Test
	void getFollowing_CacheHit_EmptyBytesIsTreatedAsMiss() {
		// Covers the .filter(bytes -> bytes.length > 0) branch
		// empty cached bytes fall through to the switchIfEmpty
		// deferred repository read.
		given(this.valueOperations.get(anyString())).willReturn(Mono.just(new byte[0]));
		given(this.followRepository.findByFollowerIdAndStatus(this.userId, Status.ACCEPTED.name()))
			.willReturn(Flux.empty());
		given(this.valueOperations.set(anyString(), any(byte[].class), any(Duration.class)))
			.willReturn(Mono.just(true));

		StepVerifier.create(this.followService.getFollowing(this.userId))
			.expectNextMatches(b -> b != null)
			.verifyComplete();

		verify(this.followRepository).findByFollowerIdAndStatus(this.userId, Status.ACCEPTED.name());
	}

	@Test
	void follow_PrivateUser_RecordsPending() {
		// Covers the userSummary.isPrivate==true branch — the
		// entity is built with Status.PENDING.
		UserDto privateUser = new UserDto();
		privateUser.setId(this.followedId);
		privateUser.setIsPrivate(true);
		FollowEntity saved = new FollowEntity();
		saved.setId(UUID.randomUUID());
		saved.setStatus("PENDING");

		given(this.userService.fetchUserSummary(this.followedId)).willReturn(Mono.just(privateUser));
		given(this.followMapper.mapRequestToEntity(this.userId, this.followedId, Status.PENDING.name()))
			.willReturn(saved);
		given(this.followRepository.save(any(FollowEntity.class))).willReturn(Mono.just(saved));
		// follow() evicts the actor's following + recommendations caches (two keys).
		given(this.redisTemplate.delete(anyString(), anyString())).willReturn(Mono.just(2L));

		// follow() returns Mono<Void> after the 204-no-content fix.
		StepVerifier.create(this.followService.follow(this.userId, this.followedId)).verifyComplete();

		verify(this.followMapper).mapRequestToEntity(this.userId, this.followedId, Status.PENDING.name());
	}

	@Test
	void updateFollowRequest_PendingStatus_RaisesInvalidStatusError() {
		// Covers the "status == Status.PENDING" branch — caller
		// tries to set the request back to PENDING which is rejected.
		UUID followRequestId = UUID.randomUUID();
		FollowEntity existing = new FollowEntity();
		existing.setId(followRequestId);
		existing.setFollowerId(UUID.randomUUID());

		given(this.followRepository.findById(followRequestId)).willReturn(Mono.just(existing));

		StepVerifier.create(this.followService.updateFollowRequest(this.userId, followRequestId, Status.PENDING))
			.expectErrorMatches(e -> e instanceof ResponseStatusException rse
					&& rse.getStatusCode() == HttpStatus.BAD_REQUEST && "Invalid status update".equals(rse.getReason()))
			.verify();

		verify(this.followRepository, never()).save(any());
	}

	@Test
	void updateFollowRequest_FollowerSelfAccept_RaisesInvalidStatusError() {
		// Covers the "followEntity.followerId.equals(userId) && status==ACCEPTED"
		// branch — a user trying to accept their own outgoing
		// follow request is rejected.
		UUID followRequestId = UUID.randomUUID();
		FollowEntity existing = new FollowEntity();
		existing.setId(followRequestId);
		existing.setFollowerId(this.userId);

		given(this.followRepository.findById(followRequestId)).willReturn(Mono.just(existing));

		StepVerifier.create(this.followService.updateFollowRequest(this.userId, followRequestId, Status.ACCEPTED))
			.expectErrorMatches(e -> e instanceof ResponseStatusException rse
					&& rse.getStatusCode() == HttpStatus.BAD_REQUEST && "Invalid status update".equals(rse.getReason()))
			.verify();

		verify(this.followRepository, never()).save(any());
	}

	@Test
	void getFollowing_CacheHit_NonEmptyBytes_ReturnsCacheDirectly() {
		// Covers the "filter passes" outcome on the cache-hit branch:
		// .filter(bytes -> bytes.length > 0)
		// With a real, non-empty byte[] the filter evaluates true and the
		// pipeline must short-circuit BEFORE the switchIfEmpty block — so
		// no repository or set() invocation is expected.
		given(this.valueOperations.get(anyString())).willReturn(Mono.just(new byte[] { 1, 2, 3, 4 }));

		StepVerifier.create(this.followService.getFollowing(this.userId))
			.expectNextMatches(
					bytes -> bytes.length == 4 && bytes[0] == 1 && bytes[1] == 2 && bytes[2] == 3 && bytes[3] == 4)
			.verifyComplete();
	}

	@Test
	void updateFollowRequest_RejectedStatus_PersistsRejection() {
		// Covers the "all conditions false" arm of updateFollowRequest:
		// status == REJECTED and followerId != userId → bad-status guard is
		// false. The actor must also be the followed party (ownership check) to
		// proceed to setStatus + repository.save.
		UUID followRequestId = UUID.randomUUID();
		UUID otherFollower = UUID.randomUUID();
		FollowEntity entity = new FollowEntity();
		entity.setId(followRequestId);
		entity.setFollowerId(otherFollower);
		entity.setFollowedId(this.userId); // actor IS the followed party — passes ownership check
		entity.setStatus(Status.PENDING.name());

		given(this.followRepository.findById(followRequestId)).willReturn(Mono.just(entity));
		given(this.followRepository.save(any(FollowEntity.class))).willReturn(Mono.just(entity));

		// A reject is not an accept, so no recommendation eviction fires.
		StepVerifier.create(this.followService.updateFollowRequest(this.userId, followRequestId, Status.REJECTED))
			.verifyComplete();

		verify(this.redisTemplate, never()).delete(anyString());
	}

	@Test
	void updateFollowRequest_FollowerSelfReject_FallsThroughToSave() {
		// Covers the remaining branch combination on updateFollowRequest:
		// the follower is the actor but the status is REJECTED (not ACCEPTED), so
		// the bad-status guard is false. The actor must also be the followed party
		// (ownership check) to proceed to setStatus + save. Here followedId==userId
		// satisfies the ownership check even though followerId==userId too.
		UUID followRequestId = UUID.randomUUID();
		FollowEntity entity = new FollowEntity();
		entity.setId(followRequestId);
		entity.setFollowerId(this.userId);
		entity.setFollowedId(this.userId); // actor IS the followed party — passes ownership check
		entity.setStatus(Status.PENDING.name());

		given(this.followRepository.findById(followRequestId)).willReturn(Mono.just(entity));
		given(this.followRepository.save(any(FollowEntity.class))).willReturn(Mono.just(entity));

		// A reject is not an accept, so no recommendation eviction fires.
		StepVerifier.create(this.followService.updateFollowRequest(this.userId, followRequestId, Status.REJECTED))
			.verifyComplete();

		verify(this.redisTemplate, never()).delete(anyString());
	}

	@Test
	void getFollowersCountFromCache_FallbackPath_ReturnsCachedSize() {
		// Exercises the Resilience4j fallback method getFollowersCountFromCache directly.
		// The following_cache holds who the user FOLLOWS, not their followers, so reading
		// it to infer a followers count is incorrect. The fallback now degrades to 0L — a
		// safe placeholder that self-corrects the instant the circuit breaker closes.
		StepVerifier.create(this.followService.getFollowersCountFromCache(this.userId)).expectNext(0L).verifyComplete();
	}

}
