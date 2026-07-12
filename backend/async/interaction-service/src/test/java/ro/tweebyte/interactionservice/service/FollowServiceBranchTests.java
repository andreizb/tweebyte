/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import ro.tweebyte.interactionservice.cache.CountCache;
import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.mapper.FollowMapper;
import ro.tweebyte.interactionservice.model.FollowingEntryDto;
import ro.tweebyte.interactionservice.model.TweetInteractionsEntryDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.FollowCountsRow;
import ro.tweebyte.interactionservice.repository.FollowRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Branch-coverage tests for FollowService — exercises: - getFollowing cache-hit vs
 * cache-miss arms - getFollowing JSON-serialise failure path - follow() public/private
 * toggle - updateFollowRequest invalid-status branches
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FollowServiceBranchTests {

	@Mock
	private FollowRepository followRepository;

	@Mock
	private FollowMapper followMapper;

	@Mock
	private UserService userService;

	@Mock
	private ExecutorService executorService;

	@Mock
	private RedisTemplate<String, byte[]> redisTemplate;

	@Mock
	private ValueOperations<String, byte[]> valueOperations;

	@Mock
	private CountCache countCache;

	@Mock
	private TweetInteractionsService tweetInteractionsService;

	@InjectMocks
	private FollowService followService;

	@BeforeEach
	void setUp() {
		// Make supplyAsync(...) on this executor run synchronously.
		willAnswer(invocation -> {
			((Runnable) invocation.getArgument(0)).run();
			return null;
		}).given(this.executorService).execute(any(Runnable.class));
	}

	@Test
	void getFollowing_cacheMiss_writesToRedis() throws Exception {
		UUID userId = UUID.randomUUID();
		given(this.redisTemplate.opsForValue()).willReturn(this.valueOperations);
		given(this.valueOperations.get(anyString())).willReturn(null); // cache miss
		given(this.followRepository.findByFollowerIdAndStatus(userId, FollowEntity.Status.ACCEPTED))
			.willReturn(Collections.emptyList());

		byte[] result = this.followService.getFollowing(userId).get();

		assertThat(result).isNotNull();
		verify(this.valueOperations).set(anyString(), any(byte[].class), any());
	}

	@Test
	void getFollowing_emptyByteArray_treatedAsMiss() throws Exception {
		UUID userId = UUID.randomUUID();
		given(this.redisTemplate.opsForValue()).willReturn(this.valueOperations);
		given(this.valueOperations.get(anyString())).willReturn(new byte[0]); // length=0 path
		given(this.followRepository.findByFollowerIdAndStatus(userId, FollowEntity.Status.ACCEPTED))
			.willReturn(Collections.emptyList());

		byte[] result = this.followService.getFollowing(userId).get();

		assertThat(result).isNotNull();
		verify(this.valueOperations).set(anyString(), any(byte[].class), any());
	}

	@Test
	void getFollowing_serializerFails_propagates() throws Exception {
		UUID userId = UUID.randomUUID();
		given(this.redisTemplate.opsForValue()).willReturn(this.valueOperations);
		given(this.valueOperations.get(anyString())).willReturn(null);

		FollowEntity entity = new FollowEntity();
		given(this.followRepository.findByFollowerIdAndStatus(userId, FollowEntity.Status.ACCEPTED))
			.willReturn(List.of(entity));
		UserDto userDto = new UserDto();
		userDto.setUserName("testUser");
		// The cold entry (its followedId is null on a bare FollowEntity) resolves through the
		// single batched getUserSummaries call rather than a per-user read.
		given(this.userService.getUserSummaries(any()))
			.willReturn(CompletableFuture.completedFuture(Collections.singletonMap(null, userDto)));
		given(this.followMapper.mapEntityToEntry(any(FollowEntity.class), eq("testUser")))
			.willReturn(new FollowingEntryDto(null, "testUser", null));

		// Inject a mock ObjectMapper that throws on writeValueAsBytes.
		ObjectMapper mockMapper = mock(ObjectMapper.class);
		given(mockMapper.writeValueAsBytes(any())).willThrow(new JsonProcessingException("boom") {
		});
		ReflectionTestUtils.setField(this.followService, "objectMapper", mockMapper);

		Throwable ex = catchThrowable(() -> this.followService.getFollowing(userId).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(RuntimeException.class);
	}

	@Test
	void follow_privateUser_pendingStatus() throws Exception {
		// Ternary branch: isPrivate=true → status PENDING, not ACCEPTED.
		UUID userId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();

		UserDto userDto = new UserDto();
		userDto.setIsPrivate(true);
		given(this.userService.fetchUserSummary(followedId)).willReturn(CompletableFuture.completedFuture(userDto));

		FollowEntity entity = new FollowEntity();
		given(this.followMapper.mapRequestToEntity(userId, followedId, FollowEntity.Status.PENDING)).willReturn(entity);
		given(this.followRepository.save(entity)).willReturn(entity);

		this.followService.follow(userId, followedId).get();

		verify(this.followMapper).mapRequestToEntity(userId, followedId, FollowEntity.Status.PENDING);
		verify(this.followMapper, never()).mapRequestToEntity(any(), any(), eq(FollowEntity.Status.ACCEPTED));
	}

	@Test
	void updateFollowRequest_pendingStatus_throws() {
		// Negative branch: trying to "update" to PENDING is rejected.
		UUID followRequestId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		FollowEntity entity = new FollowEntity();
		entity.setFollowerId(userId);
		given(this.followRepository.findById(followRequestId)).willReturn(Optional.of(entity));

		Throwable ex = catchThrowable(
				() -> this.followService.updateFollowRequest(userId, followRequestId, FollowEntity.Status.PENDING).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(RuntimeException.class);
		verify(this.followRepository, never()).save(any());
	}

	@Test
	void updateFollowRequest_followerEqualsUserAndAccepted_throws() {
		// Negative branch: user accepting their own request → reject.
		UUID followRequestId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		FollowEntity entity = new FollowEntity();
		entity.setFollowerId(userId); // same as caller
		given(this.followRepository.findById(followRequestId)).willReturn(Optional.of(entity));

		Throwable ex = catchThrowable(
				() -> this.followService.updateFollowRequest(userId, followRequestId, FollowEntity.Status.ACCEPTED).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(RuntimeException.class);
		verify(this.followRepository, never()).save(any());
	}

	@Test
	void updateFollowRequest_followerEqualsUserButRejected_savesEntity() throws Exception {
		// Branch: equals(userId) true, status == ACCEPTED false (REJECTED) → first guard
		// false. The actor must also be the followed party (the request recipient) to pass
		// the ownership check — so followedId == userId here.
		UUID followRequestId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		FollowEntity entity = new FollowEntity();
		entity.setFollowerId(userId); // same as caller (a user who sent a follow request to themselves — edge case)
		entity.setFollowedId(userId); // actor IS the followed party — passes ownership check
		given(this.followRepository.findById(followRequestId)).willReturn(Optional.of(entity));

		this.followService.updateFollowRequest(userId, followRequestId, FollowEntity.Status.REJECTED).get();

		verify(this.followRepository).save(entity);
		assertThat(entity.getStatus()).isEqualTo(FollowEntity.Status.REJECTED);
	}

	@Test
	void updateFollowRequest_rejected_savesEntity() throws Exception {
		// Positive: REJECTED status with a different follower, and actor is the followed
		// party (the request recipient) — both guards pass → save proceeds.
		UUID followRequestId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		FollowEntity entity = new FollowEntity();
		entity.setFollowerId(UUID.randomUUID()); // different from actor
		entity.setFollowedId(userId);            // actor IS the followed party — passes ownership check
		given(this.followRepository.findById(followRequestId)).willReturn(Optional.of(entity));

		this.followService.updateFollowRequest(userId, followRequestId, FollowEntity.Status.REJECTED).get();

		verify(this.followRepository).save(entity);
		assertThat(entity.getStatus()).isEqualTo(FollowEntity.Status.REJECTED);
	}

	@Test
	void getFollowing_cacheMiss_cachedUser_deserialisesFromMget() throws Exception {
		// A followed user already present in the users:: MGET payload deserialises inline (no
		// per-user read, no batch fetch for it).
		UUID userId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();
		given(this.redisTemplate.opsForValue()).willReturn(this.valueOperations);
		given(this.valueOperations.get(anyString())).willReturn(null); // following_cache miss

		FollowEntity entity = new FollowEntity();
		entity.setFollowedId(followedId);
		given(this.followRepository.findByFollowerIdAndStatus(userId, FollowEntity.Status.ACCEPTED))
			.willReturn(List.of(entity));

		// The user's summary is cached: the MGET returns its JSON bytes, so it resolves inline.
		ObjectMapper realMapper = new ObjectMapper().findAndRegisterModules();
		UserDto cachedUser = new UserDto();
		cachedUser.setId(followedId);
		cachedUser.setUserName("cachedHandle");
		byte[] cachedBytes = realMapper.writeValueAsBytes(cachedUser);
		given(this.valueOperations.multiGet(any())).willReturn(List.of(cachedBytes));
		ReflectionTestUtils.setField(this.followService, "objectMapper", realMapper);

		// No cold ids → batch fetch resolves to an empty map.
		given(this.userService.getUserSummaries(any()))
			.willReturn(CompletableFuture.completedFuture(Collections.emptyMap()));
		given(this.followMapper.mapEntityToEntry(entity, "cachedHandle"))
			.willReturn(new FollowingEntryDto(followedId, "cachedHandle", null));

		byte[] result = this.followService.getFollowing(userId).get();

		assertThat(result).isNotEmpty();
		verify(this.followMapper).mapEntityToEntry(entity, "cachedHandle");
		// A cached user is never fetched per-id.
		verify(this.userService, never()).getUserSummary(followedId);
	}

	@Test
	void getFollowing_cacheMiss_coldUser_resolvesViaBatch() throws Exception {
		// A cold followed user (absent from the MGET) resolves from the single batched
		// getUserSummaries call rather than a per-user read.
		UUID userId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();
		given(this.redisTemplate.opsForValue()).willReturn(this.valueOperations);
		given(this.valueOperations.get(anyString())).willReturn(null);

		FollowEntity entity = new FollowEntity();
		entity.setFollowedId(followedId);
		given(this.followRepository.findByFollowerIdAndStatus(userId, FollowEntity.Status.ACCEPTED))
			.willReturn(List.of(entity));
		// MGET misses (null), so the id is cold.
		given(this.valueOperations.multiGet(any())).willReturn(Collections.singletonList(null));
		ReflectionTestUtils.setField(this.followService, "objectMapper",
				new ObjectMapper().findAndRegisterModules());

		UserDto batched = new UserDto();
		batched.setId(followedId);
		batched.setUserName("batchHandle");
		given(this.userService.getUserSummaries(List.of(followedId)))
			.willReturn(CompletableFuture.completedFuture(Collections.singletonMap(followedId, batched)));
		given(this.followMapper.mapEntityToEntry(entity, "batchHandle"))
			.willReturn(new FollowingEntryDto(followedId, "batchHandle", null));

		byte[] result = this.followService.getFollowing(userId).get();

		assertThat(result).isNotEmpty();
		verify(this.userService).getUserSummaries(List.of(followedId));
		verify(this.followMapper).mapEntityToEntry(entity, "batchHandle");
		verify(this.userService, never()).getUserSummary(followedId);
	}

	@Test
	void getFollowing_cacheMiss_coldUserAbsentFromBatch_fallsBackPerUser() throws Exception {
		// A cold id the batch did not return (genuinely-missing user) falls back to the per-user
		// read-through so its not-found signal still surfaces.
		UUID userId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();
		given(this.redisTemplate.opsForValue()).willReturn(this.valueOperations);
		given(this.valueOperations.get(anyString())).willReturn(null);

		FollowEntity entity = new FollowEntity();
		entity.setFollowedId(followedId);
		given(this.followRepository.findByFollowerIdAndStatus(userId, FollowEntity.Status.ACCEPTED))
			.willReturn(List.of(entity));
		given(this.valueOperations.multiGet(any())).willReturn(Collections.singletonList(null));
		ReflectionTestUtils.setField(this.followService, "objectMapper",
				new ObjectMapper().findAndRegisterModules());

		// The batch returns an empty map (id absent), so the per-user read fills the gap.
		given(this.userService.getUserSummaries(List.of(followedId)))
			.willReturn(CompletableFuture.completedFuture(Collections.emptyMap()));
		UserDto perUser = new UserDto();
		perUser.setId(followedId);
		perUser.setUserName("fallbackHandle");
		given(this.userService.getUserSummary(followedId)).willReturn(CompletableFuture.completedFuture(perUser));
		given(this.followMapper.mapEntityToEntry(entity, "fallbackHandle"))
			.willReturn(new FollowingEntryDto(followedId, "fallbackHandle", null));

		byte[] result = this.followService.getFollowing(userId).get();

		assertThat(result).isNotEmpty();
		// Absent from the batch → per-user read-through is exercised.
		verify(this.userService).getUserSummary(followedId);
		verify(this.followMapper).mapEntityToEntry(entity, "fallbackHandle");
	}

	@Test
	void getFollowing_cacheMiss_cachedUserCorruptBytes_failsFuture() throws Exception {
		// A cached user whose bytes will not deserialise propagates an InteractionException
		// (the IOException arm of resolveFollowingEntry).
		UUID userId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();
		given(this.redisTemplate.opsForValue()).willReturn(this.valueOperations);
		given(this.valueOperations.get(anyString())).willReturn(null);

		FollowEntity entity = new FollowEntity();
		entity.setFollowedId(followedId);
		given(this.followRepository.findByFollowerIdAndStatus(userId, FollowEntity.Status.ACCEPTED))
			.willReturn(List.of(entity));
		// Non-empty but un-parseable bytes take the cached arm, then fail to deserialise.
		given(this.valueOperations.multiGet(any()))
			.willReturn(List.of("not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		ReflectionTestUtils.setField(this.followService, "objectMapper",
				new ObjectMapper().findAndRegisterModules());
		given(this.userService.getUserSummaries(any()))
			.willReturn(CompletableFuture.completedFuture(Collections.emptyMap()));

		Throwable ex = catchThrowable(() -> this.followService.getFollowing(userId).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(RuntimeException.class);
	}

	@Test
	void getFollowing_cacheMiss_nullMget_allEntitiesCold() throws Exception {
		// A null users:: MGET payload is defensively treated as every followed user cold, so each
		// resolves through the batch (the cached == null ternary arm).
		UUID userId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();
		given(this.redisTemplate.opsForValue()).willReturn(this.valueOperations);
		given(this.valueOperations.get(anyString())).willReturn(null);

		FollowEntity entity = new FollowEntity();
		entity.setFollowedId(followedId);
		given(this.followRepository.findByFollowerIdAndStatus(userId, FollowEntity.Status.ACCEPTED))
			.willReturn(List.of(entity));
		// multiGet returns null → every entity is cold.
		given(this.valueOperations.multiGet(any())).willReturn(null);
		ReflectionTestUtils.setField(this.followService, "objectMapper",
				new ObjectMapper().findAndRegisterModules());

		UserDto batched = new UserDto();
		batched.setId(followedId);
		batched.setUserName("coldHandle");
		given(this.userService.getUserSummaries(List.of(followedId)))
			.willReturn(CompletableFuture.completedFuture(Collections.singletonMap(followedId, batched)));
		given(this.followMapper.mapEntityToEntry(entity, "coldHandle"))
			.willReturn(new FollowingEntryDto(followedId, "coldHandle", null));

		byte[] result = this.followService.getFollowing(userId).get();

		assertThat(result).isNotEmpty();
		verify(this.userService).getUserSummaries(List.of(followedId));
	}

	@Test
	void getFollowing_cacheMiss_writeBackSerialiseFails_propagates() throws Exception {
		// The entries resolve from the batch, but the final write-back serialization throws — the
		// outer catch in getFollowing wraps it as an InteractionException.
		UUID userId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();
		given(this.redisTemplate.opsForValue()).willReturn(this.valueOperations);
		given(this.valueOperations.get(anyString())).willReturn(null);

		FollowEntity entity = new FollowEntity();
		entity.setFollowedId(followedId);
		given(this.followRepository.findByFollowerIdAndStatus(userId, FollowEntity.Status.ACCEPTED))
			.willReturn(List.of(entity));
		given(this.valueOperations.multiGet(any())).willReturn(null);

		UserDto batched = new UserDto();
		batched.setId(followedId);
		batched.setUserName("handle");
		given(this.userService.getUserSummaries(List.of(followedId)))
			.willReturn(CompletableFuture.completedFuture(Collections.singletonMap(followedId, batched)));
		given(this.followMapper.mapEntityToEntry(entity, "handle"))
			.willReturn(new FollowingEntryDto(followedId, "handle", null));

		// A mapper whose per-user reads succeed but whose final list write-back throws a generic
		// exception (the catch (Exception ex) arm, not the IOException deserialise arm).
		ObjectMapper writeFailingMapper = mock(ObjectMapper.class);
		given(writeFailingMapper.writeValueAsBytes(any())).willThrow(new IllegalStateException("write boom"));
		ReflectionTestUtils.setField(this.followService, "objectMapper", writeFailingMapper);

		Throwable ex = catchThrowable(() -> this.followService.getFollowing(userId).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(RuntimeException.class);
	}

	@Test
	void getFollowCounts_cacheMiss_loadsCombinedCountQuery() throws Exception {
		// On a cache miss getPair runs the combined COUNT loader, which issues ONE
		// countFollowersAndFollowing query and returns both counts in [followers, following]
		// order.
		UUID userId = UUID.randomUUID();
		FollowCountsRow row = mock(FollowCountsRow.class);
		given(row.getFollowers()).willReturn(15L);
		given(row.getFollowing()).willReturn(8L);
		given(this.followRepository.countFollowersAndFollowing(userId, FollowEntity.Status.ACCEPTED.name()))
			.willReturn(row);
		// Drive getPair's miss-loader so the combined query runs.
		given(this.countCache.getPair(anyString(), anyString(), any())).willAnswer(invocation -> {
			java.util.function.Supplier<long[]> loader = invocation.getArgument(2);
			return loader.get();
		});

		var counts = this.followService.getFollowCounts(userId).get();

		assertThat(counts.getFollowers()).isEqualTo(15L);
		assertThat(counts.getFollowing()).isEqualTo(8L);
		verify(this.followRepository).countFollowersAndFollowing(userId, FollowEntity.Status.ACCEPTED.name());
	}

	@Test
	void getFollowCounts_returnsPairFromCountCache() throws Exception {
		// getFollowCounts collapses both counters into ONE getPair call (MGET + on-miss combined
		// COUNT + EVAL), surfacing them as a FollowCountsDto in [followers, following] order.
		UUID userId = UUID.randomUUID();
		given(this.countCache.getPair(eq("followers_count::" + userId), eq("following_count::" + userId), any()))
			.willReturn(new long[] { 42L, 7L });

		var counts = this.followService.getFollowCounts(userId).get();

		assertThat(counts.getFollowers()).isEqualTo(42L);
		assertThat(counts.getFollowing()).isEqualTo(7L);
	}

	@Test
	void getProfileInteractions_emptyTweetIds_skipsInteractionsSubCall() throws Exception {
		// An empty tweet-id page skips the interactions sub-call entirely (empty list) but still
		// reads the real follow counts.
		UUID userId = UUID.randomUUID();
		given(this.countCache.getPair(anyString(), anyString(), any())).willReturn(new long[] { 3L, 9L });

		var profile = this.followService.getProfileInteractions(userId, List.of()).get();

		assertThat(profile.getFollowCounts().getFollowers()).isEqualTo(3L);
		assertThat(profile.getFollowCounts().getFollowing()).isEqualTo(9L);
		assertThat(profile.getTweetInteractions()).isEmpty();
		// The interactions sub-service is never invoked for an empty page.
		verify(this.tweetInteractionsService, never()).getTweetInteractionsEntries(any());
	}

	@Test
	void getProfileInteractions_withTweetIds_combinesCountsAndInteractions() throws Exception {
		// A non-empty page combines the follow counts with the per-tweet interactions in one
		// grouped payload.
		UUID userId = UUID.randomUUID();
		UUID tweetId = UUID.randomUUID();
		List<UUID> tweetIds = List.of(tweetId);
		given(this.countCache.getPair(anyString(), anyString(), any())).willReturn(new long[] { 5L, 6L });
		TweetInteractionsEntryDto entry = new TweetInteractionsEntryDto(tweetId, 1L, 2L, 3L, null);
		given(this.tweetInteractionsService.getTweetInteractionsEntries(tweetIds))
			.willReturn(CompletableFuture.completedFuture(List.of(entry)));

		var profile = this.followService.getProfileInteractions(userId, tweetIds).get();

		assertThat(profile.getFollowCounts().getFollowers()).isEqualTo(5L);
		assertThat(profile.getTweetInteractions()).containsExactly(entry);
		verify(this.tweetInteractionsService).getTweetInteractionsEntries(tweetIds);
	}

	@Test
	void follow_duplicateEdge_swallowsViolationAndSkipsIncrement() throws Exception {
		// An idempotent re-follow hits the UNIQUE(follower_id, followed_id) constraint: the
		// DataIntegrityViolationException is swallowed as a no-op success and the count delta is
		// NOT applied (the duplicate must not inflate the cached counts).
		UUID userId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();

		UserDto userDto = new UserDto();
		userDto.setIsPrivate(false);
		given(this.userService.fetchUserSummary(followedId)).willReturn(CompletableFuture.completedFuture(userDto));
		FollowEntity entity = new FollowEntity();
		given(this.followMapper.mapRequestToEntity(userId, followedId, FollowEntity.Status.ACCEPTED))
			.willReturn(entity);
		given(this.followRepository.save(entity))
			.willThrow(new DataIntegrityViolationException("duplicate edge"));

		this.followService.follow(userId, followedId).get();

		verify(this.followRepository).save(entity);
		// The duplicate is swallowed: no count increment, but the actor's caches are still evicted.
		verify(this.countCache, never()).incrementBoth(anyString(), anyString());
		verify(this.redisTemplate).delete(anyList());
	}

	@Test
	void unfollow_pendingEdge_deletesWithoutDecrement() throws Exception {
		// A PENDING edge never incremented the counts, so removing it must delete the row
		// but skip the decrement.
		UUID followerId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();
		FollowEntity edge = new FollowEntity();
		edge.setStatus(FollowEntity.Status.PENDING);
		given(this.followRepository.findByFollowerIdAndFollowedId(followerId, followedId)).willReturn(Optional.of(edge));

		this.followService.unfollow(followerId, followedId).get();

		verify(this.followRepository).deleteByFollowerIdAndFollowedId(followerId, followedId);
		verify(this.countCache, never()).decrementBoth(any(), any());
	}

	@Test
	void unfollow_missingEdge_skipsDeleteAndDecrement() throws Exception {
		// A repeated unfollow finds no edge: nothing to delete, no delta — only the
		// actor-scoped cache eviction still runs.
		UUID followerId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();
		given(this.followRepository.findByFollowerIdAndFollowedId(followerId, followedId)).willReturn(Optional.empty());

		this.followService.unfollow(followerId, followedId).get();

		verify(this.followRepository, never()).deleteByFollowerIdAndFollowedId(any(), any());
		verify(this.countCache, never()).decrementBoth(any(), any());
	}

}
