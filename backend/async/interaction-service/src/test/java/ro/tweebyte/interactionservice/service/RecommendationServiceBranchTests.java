/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.FollowRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Branch-coverage tests for RecommendationService — drives the compound predicate inside
 * getUserRecommendations through every arm: - candidate already in followedIds (left side
 * false) - candidate equals the requesting userId (right side false) - candidate is
 * genuinely new (both true)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecommendationServiceBranchTests {

	@Mock
	private UserService userService;

	@Mock
	private TweetService tweetService;

	@Mock
	private FollowRepository followRepository;

	@Mock
	private LikeService likeService;

	@Mock
	private RetweetService retweetService;

	@Mock
	private CacheManager cacheManager;

	@InjectMocks
	private RecommendationService recommendationService;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(this.recommendationService, "self", this.recommendationService);
	}

	@Test
	void getUserRecommendations_candidateAlreadyFollowed_skipped() {
		// Branch: !followedIds.contains(...) is FALSE → short-circuits.
		UUID userId = UUID.randomUUID();
		UUID followedA = UUID.randomUUID();
		UUID followedB = UUID.randomUUID();

		FollowEntity firstHop = new FollowEntity();
		firstHop.setFollowedId(followedA);
		FollowEntity secondHop1 = new FollowEntity();
		secondHop1.setFollowedId(followedB); // already in followedIds (returned by 1st
												// hop too)
		FollowEntity secondHop2 = new FollowEntity();
		secondHop2.setFollowedId(followedA); // also already in followedIds

		// First hop returns BOTH followedA and followedB so any candidate from second hop
		// is in set.
		FollowEntity firstHopExtra = new FollowEntity();
		firstHopExtra.setFollowedId(followedB);

		given(this.followRepository.findByFollowerIdAndStatus(eq(userId), any()))
			.willReturn(List.of(firstHop, firstHopExtra));
		given(this.followRepository.findByFollowerIdInAndStatus(eq(Set.of(followedA, followedB)), any()))
			.willReturn(List.of(secondHop1, secondHop2));

		Cache popularCache = mock(Cache.class);
		given(this.cacheManager.getCache("popular_users")).willReturn(popularCache);
		given(popularCache.get("p0", Map.class)).willReturn(Map.of());

		given(this.userService.getUserSummary(any())).willReturn(CompletableFuture.completedFuture(new UserDto()));

		List<UserDto> result = this.recommendationService.getUserRecommendations(userId);

		assertThat(result).isNotNull();
		// Every second-hop entity was already in followedIds, so userService never
		// called.
		verify(this.userService, never()).getUserSummary(any());
	}

	@Test
	void getUserRecommendations_candidateIsRequestingUser_skipped() {
		// Branch: !userId.equals(...) is FALSE → short-circuits at right side.
		UUID userId = UUID.randomUUID();
		UUID followedA = UUID.randomUUID();

		FollowEntity firstHop = new FollowEntity();
		firstHop.setFollowedId(followedA);

		FollowEntity secondHop = new FollowEntity();
		secondHop.setFollowedId(userId); // candidate == userId itself

		given(this.followRepository.findByFollowerIdAndStatus(eq(userId), any())).willReturn(List.of(firstHop));
		given(this.followRepository.findByFollowerIdInAndStatus(eq(Set.of(followedA)), any()))
			.willReturn(List.of(secondHop));

		Cache popularCache = mock(Cache.class);
		given(this.cacheManager.getCache("popular_users")).willReturn(popularCache);
		given(popularCache.get("p0", Map.class)).willReturn(Map.of());

		List<UserDto> result = this.recommendationService.getUserRecommendations(userId);

		assertThat(result).isNotNull();
		// Self-recommendation should be filtered out.
		verify(this.userService, never()).getUserSummary(userId);
	}

	@Test
	void getUserRecommendations_popularUserAlreadyFollowed_skipped() {
		// Branch: !followedIds.contains(popularId) — both arms via two popular ids,
		// one already-followed and one new.
		UUID userId = UUID.randomUUID();
		UUID followedA = UUID.randomUUID();
		UUID popularNew = UUID.randomUUID();

		FollowEntity firstHop = new FollowEntity();
		firstHop.setFollowedId(followedA);

		given(this.followRepository.findByFollowerIdAndStatus(eq(userId), any())).willReturn(List.of(firstHop));
		given(this.followRepository.findByFollowerIdInAndStatus(eq(Set.of(followedA)), any())).willReturn(List.of());

		Cache popularCache = mock(Cache.class);
		given(this.cacheManager.getCache("popular_users")).willReturn(popularCache);
		// followedA already followed (skipped), popularNew new (added)
		given(popularCache.get("p0", Map.class)).willReturn(Map.of(followedA, 5.0, popularNew, 3.0));

		given(this.userService.getUserSummary(popularNew)).willReturn(CompletableFuture.completedFuture(new UserDto()));

		List<UserDto> result = this.recommendationService.getUserRecommendations(userId);

		assertThat(result).isNotNull();
		verify(this.userService).getUserSummary(popularNew);
		verify(this.userService, never()).getUserSummary(followedA);
	}

	@Test
	void getUserRecommendations_popularUserIsRequestingUser_skipped() {
		// Branch: popular path !userId.equals(popularId) is FALSE → the viewer is never
		// recommended to follow themselves even when they are in the popular set.
		UUID userId = UUID.randomUUID();
		UUID followedA = UUID.randomUUID();
		UUID popularNew = UUID.randomUUID();

		FollowEntity firstHop = new FollowEntity();
		firstHop.setFollowedId(followedA);

		given(this.followRepository.findByFollowerIdAndStatus(eq(userId), any())).willReturn(List.of(firstHop));
		given(this.followRepository.findByFollowerIdInAndStatus(eq(Set.of(followedA)), any())).willReturn(List.of());

		Cache popularCache = mock(Cache.class);
		given(this.cacheManager.getCache("popular_users")).willReturn(popularCache);
		// The viewer is themselves "popular" (followed by others) and must be dropped;
		// popularNew is a genuine new candidate and is added.
		given(popularCache.get("p0", Map.class)).willReturn(Map.of(userId, 9.0, popularNew, 3.0));

		given(this.userService.getUserSummary(popularNew)).willReturn(CompletableFuture.completedFuture(new UserDto()));

		List<UserDto> result = this.recommendationService.getUserRecommendations(userId);

		assertThat(result).isNotNull();
		verify(this.userService).getUserSummary(popularNew);
		verify(this.userService, never()).getUserSummary(userId);
	}

	@Test
	void getUserRecommendations_genuinelyNewCandidate_isAdded() {
		// Branch: both sides TRUE → recommendation accumulated.
		UUID userId = UUID.randomUUID();
		UUID followedA = UUID.randomUUID();
		UUID candidate = UUID.randomUUID();

		FollowEntity firstHop = new FollowEntity();
		firstHop.setFollowedId(followedA);

		FollowEntity secondHop = new FollowEntity();
		secondHop.setFollowedId(candidate); // not in followedIds, not userId

		given(this.followRepository.findByFollowerIdAndStatus(eq(userId), any())).willReturn(List.of(firstHop));
		given(this.followRepository.findByFollowerIdInAndStatus(eq(Set.of(followedA)), any()))
			.willReturn(List.of(secondHop));

		Cache popularCache = mock(Cache.class);
		given(this.cacheManager.getCache("popular_users")).willReturn(popularCache);
		given(popularCache.get("p0", Map.class)).willReturn(Map.of());

		given(this.userService.getUserSummary(candidate)).willReturn(CompletableFuture.completedFuture(new UserDto()));

		List<UserDto> result = this.recommendationService.getUserRecommendations(userId);

		assertThat(result).isNotNull();
		verify(this.userService).getUserSummary(candidate);
	}

}
