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
import java.util.function.LongSupplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import ro.tweebyte.interactionservice.cache.CountCache;
import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.mapper.FollowMapper;
import ro.tweebyte.interactionservice.model.FollowDto;
import ro.tweebyte.interactionservice.repository.FollowRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest
class FollowServiceTests {

	@Mock
	private FollowRepository followRepository;

	@Mock
	private FollowMapper followMapper;

	@InjectMocks
	private FollowService followService;

	@Mock
	private ObjectMapper objectMapper;

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

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		given(this.redisTemplate.opsForValue()).willReturn(this.valueOperations);
		willAnswer(invocation -> {
			((Runnable) invocation.getArgument(0)).run();
			return null;
		}).given(this.executorService).execute(any(Runnable.class));
		// CountCache is the PRIMARY read-through; the pass-through stub runs its loader so
		// the underlying repository COUNT interactions stay observable.
		given(this.countCache.get(anyString(), any(LongSupplier.class)))
			.willAnswer(invocation -> invocation.getArgument(1, LongSupplier.class).getAsLong());
	}

	@Test
	void testGetFollowers() throws Exception {
		UUID userId = UUID.randomUUID();
		FollowEntity followEntity = new FollowEntity();
		followEntity.setFollowerId(UUID.randomUUID());
		List<FollowEntity> followEntities = Collections.singletonList(followEntity);

		ro.tweebyte.interactionservice.model.UserDto userDto = new ro.tweebyte.interactionservice.model.UserDto();
		userDto.setUserName("testUser");

		given(this.followRepository.findByFollowedIdAndStatusOrderByCreatedAtDesc(userId,
				FollowEntity.Status.ACCEPTED))
			.willReturn(followEntities);
		given(this.userService.getUserSummary(followEntity.getFollowerId()))
			.willReturn(CompletableFuture.completedFuture(userDto));
		given(this.followMapper.mapEntityToDto(any(FollowEntity.class), eq("testUser"))).willReturn(new FollowDto());

		List<FollowDto> result = this.followService.getFollowers(userId).get();

		assertThat(result).isNotNull().hasSize(1);
		verify(this.followRepository).findByFollowedIdAndStatusOrderByCreatedAtDesc(userId,
				FollowEntity.Status.ACCEPTED);
		verify(this.followMapper).mapEntityToDto(any(FollowEntity.class), eq("testUser"));
	}

	@Test
	void testGetFollowing() throws Exception {
		UUID userId = UUID.randomUUID();
		byte[] mockBytes = new byte[] { 1, 2, 3 };

		given(this.valueOperations.get(anyString())).willReturn(mockBytes);

		byte[] result = this.followService.getFollowing(userId).get();

		assertThat(result).isNotNull().isEqualTo(mockBytes);
		verify(this.valueOperations).get(anyString());
	}

	@Test
	void testGetFollowersCount() throws Exception {
		UUID userId = UUID.randomUUID();
		given(this.followRepository.countByFollowedIdAndStatus(userId, FollowEntity.Status.ACCEPTED)).willReturn(5L);

		Long count = this.followService.getFollowersCount(userId).get();

		assertThat(count).isEqualTo(5L);
		verify(this.followRepository).countByFollowedIdAndStatus(userId, FollowEntity.Status.ACCEPTED);
	}

	@Test
	void testGetFollowingCount() throws Exception {
		UUID userId = UUID.randomUUID();
		given(this.followRepository.countByFollowerIdAndStatus(userId, FollowEntity.Status.ACCEPTED)).willReturn(3L);

		Long count = this.followService.getFollowingCount(userId).get();

		assertThat(count).isEqualTo(3L);
		verify(this.followRepository).countByFollowerIdAndStatus(userId, FollowEntity.Status.ACCEPTED);
	}

	@Test
	void testGetFollowRequests() throws Exception {
		UUID userId = UUID.randomUUID();
		FollowEntity followEntity = new FollowEntity();
		List<FollowEntity> followEntities = Collections.singletonList(followEntity);

		given(this.followRepository.findByFollowedIdAndStatusOrderByCreatedAtDesc(userId,
				FollowEntity.Status.PENDING))
			.willReturn(followEntities);
		given(this.followMapper.mapEntityToDto(followEntity)).willReturn(new FollowDto());

		List<FollowDto> result = this.followService.getFollowRequests(userId).get();

		assertThat(result).isNotNull().hasSize(1);
		verify(this.followRepository).findByFollowedIdAndStatusOrderByCreatedAtDesc(userId,
				FollowEntity.Status.PENDING);
		verify(this.followMapper).mapEntityToDto(any(FollowEntity.class));
	}

	@Test
	void testFollow() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();
		FollowEntity followEntity = new FollowEntity();

		ro.tweebyte.interactionservice.model.UserDto userDto = new ro.tweebyte.interactionservice.model.UserDto();
		userDto.setIsPrivate(false);
		given(this.userService.fetchUserSummary(followedId)).willReturn(CompletableFuture.completedFuture(userDto));
		given(this.followMapper.mapRequestToEntity(userId, followedId, FollowEntity.Status.ACCEPTED))
			.willReturn(followEntity);
		given(this.followRepository.save(followEntity)).willReturn(followEntity);

		this.followService.follow(userId, followedId).get();

		verify(this.followMapper).mapRequestToEntity(userId, followedId, FollowEntity.Status.ACCEPTED);
		verify(this.followRepository).save(followEntity);
		// A public (immediately ACCEPTED) follow grows both counts by one in ONE batched round-trip.
		verify(this.countCache).incrementBoth("following_count::" + userId, "followers_count::" + followedId);
	}

	@Test
	void testUpdateFollowRequest() throws Exception {
		UUID followRequestId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID requesterId = UUID.randomUUID();

		FollowEntity followEntity = new FollowEntity();
		followEntity.setFollowerId(requesterId);
		followEntity.setFollowedId(userId);
		followEntity.setStatus(FollowEntity.Status.PENDING);

		given(this.followRepository.findById(followRequestId)).willReturn(Optional.of(followEntity));
		given(this.followRepository.save(any(FollowEntity.class))).willReturn(followEntity);

		this.followService.updateFollowRequest(userId, followRequestId, FollowEntity.Status.ACCEPTED).get();

		verify(this.followRepository).findById(followRequestId);
		verify(this.followRepository).save(any(FollowEntity.class));
		// On a genuine accept the requester's recommendations go stale (their following
		// set grew), so the eviction must target the requester — not the acceptor.
		verify(this.redisTemplate).delete("follow_recommendations::" + requesterId);
		// Accepting a pending request makes the edge count for the first time on both sides,
		// applied in ONE batched round-trip.
		verify(this.countCache).incrementBoth("following_count::" + requesterId, "followers_count::" + userId);
	}

	@Test
	void testUpdateFollowRequestThrowsException() {
		UUID followRequestId = UUID.randomUUID();
		given(this.followRepository.findById(followRequestId)).willReturn(Optional.empty());

		CompletableFuture<Void> result = this.followService.updateFollowRequest(UUID.randomUUID(), followRequestId,
				FollowEntity.Status.ACCEPTED);

		assertThatThrownBy(result::get).isInstanceOf(ExecutionException.class);
	}

	@Test
	void testUnfollow() throws Exception {
		UUID followerId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();

		// unfollow resolves the edge first so the count delta only fires for a previously
		// ACCEPTED edge; an ACCEPTED edge triggers the delete and the decrement.
		FollowEntity edge = new FollowEntity();
		edge.setStatus(FollowEntity.Status.ACCEPTED);
		given(this.followRepository.findByFollowerIdAndFollowedId(followerId, followedId)).willReturn(Optional.of(edge));
		doNothing().when(this.followRepository).deleteByFollowerIdAndFollowedId(followerId, followedId);

		this.followService.unfollow(followerId, followedId).get();

		verify(this.followRepository).deleteByFollowerIdAndFollowedId(followerId, followedId);
		// An ACCEPTED edge decrements both counts in ONE batched round-trip.
		verify(this.countCache).decrementBoth("following_count::" + followerId, "followers_count::" + followedId);
	}

	@Test
	void testGetFollowedIdentifiers() throws Exception {
		UUID userId = UUID.randomUUID();
		FollowEntity followEntity = new FollowEntity();
		followEntity.setFollowedId(UUID.randomUUID());
		List<FollowEntity> followEntities = Collections.singletonList(followEntity);

		given(this.followRepository.findByFollowerIdAndStatus(userId, FollowEntity.Status.ACCEPTED))
			.willReturn(followEntities);

		List<UUID> result = this.followService.getFollowedIdentifiers(userId).get();

		assertThat(result).isNotNull().hasSize(1);
		verify(this.followRepository).findByFollowerIdAndStatus(userId, FollowEntity.Status.ACCEPTED);
	}

}
