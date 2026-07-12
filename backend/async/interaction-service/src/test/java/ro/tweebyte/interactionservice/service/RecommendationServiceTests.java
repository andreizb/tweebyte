/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.model.TweetSummaryDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.FollowRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class RecommendationServiceTests {

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
		ReflectionTestUtils.setField(this.recommendationService, "executorService", java.util.concurrent.Executors.newSingleThreadExecutor());
	}

	@AfterEach
	void tearDown() {
		ExecutorService executorService = (ExecutorService) ReflectionTestUtils.getField(this.recommendationService,
				"executorService");
		executorService.shutdownNow();
	}

	@Test
	void testGetUserRecommendations() {
		UUID userId = UUID.randomUUID();

		ReflectionTestUtils.setField(this.recommendationService, "self", this.recommendationService);

		given(this.followRepository.findByFollowerIdAndStatus(any(), any()))
			.willReturn(Collections.singletonList(new FollowEntity()));
		given(this.followRepository.findByFollowerIdInAndStatus(any(), any()))
			.willReturn(Collections.singletonList(new FollowEntity()));

		given(this.userService.getUserSummary(any())).willReturn(CompletableFuture.completedFuture(new UserDto()));

		given(this.cacheManager.getCache(any())).willReturn(mock(Cache.class));

		this.recommendationService.getUserRecommendations(userId);

		// The caller's direct follows are resolved once; friends-of-friends now go
		// through a single IN-list query instead of one lookup per followed id.
		verify(this.followRepository, times(1)).findByFollowerIdAndStatus(any(), any());
		verify(this.followRepository, times(1)).findByFollowerIdInAndStatus(any(), any());
	}

	@Test
	void testComputePopularUsers() {
		given(this.followRepository.findAllFollowedIds()).willReturn(Collections.emptyList());

		this.recommendationService.computePopularUsers();

		verify(this.followRepository).findAllFollowedIds();
		verify(this.tweetService, times(0)).getUserTweetsSummary(any());
		verify(this.likeService, times(0)).getTweetLikesCounts(any());
	}

	@Test
	void testComputePopularHashtags() throws ExecutionException, InterruptedException {
		given(this.tweetService.getPopularHashtags()).willReturn(CompletableFuture.completedFuture(Collections.emptyList()));

		this.recommendationService.fetchPopularHashtags().get();

		verify(this.tweetService).getPopularHashtags();
	}

	@Test
	void testFetchPopularUsersWithCache() {
		Cache cache = mock(Cache.class);
		given(this.cacheManager.getCache("popular_users")).willReturn(cache);
		given(cache.get("p0", Map.class)).willReturn(Map.of(UUID.randomUUID(), 1.0));

		Collection<UUID> result = ReflectionTestUtils.invokeMethod(this.recommendationService, "fetchPopularUsers");

		assertThat(result).isNotNull();
		verify(this.cacheManager).getCache("popular_users");
	}

	@Test
	void testFetchPopularUsersWithoutCache() {
		Cache cache = mock(Cache.class);
		given(this.cacheManager.getCache("popular_users")).willReturn(cache);
		given(cache.get("p0", Map.class)).willReturn(null);

		Map<UUID, Double> popularUsers = Map.of(UUID.randomUUID(), 1.0);

		RecommendationService spyRecommendationService = spy(this.recommendationService);
		ReflectionTestUtils.setField(spyRecommendationService, "self", spyRecommendationService);
		doReturn(popularUsers).when(spyRecommendationService).computePopularUsers();

		Collection<UUID> result = ReflectionTestUtils.invokeMethod(spyRecommendationService, "fetchPopularUsers");

		assertThat(result).isNotNull().isEqualTo(popularUsers.keySet());
		verify(spyRecommendationService).computePopularUsers();
	}

	@Test
	void testComputePopularUsersAndScore() {
		UUID userId1 = UUID.randomUUID();
		UUID userId2 = UUID.randomUUID();
		UUID tweetId1 = UUID.randomUUID();
		UUID tweetId2 = UUID.randomUUID();

		given(this.followRepository.findAllFollowedIds()).willReturn(List.of(userId1, userId2));
		given(this.tweetService.getUserTweetsSummary(userId1))
			.willReturn(CompletableFuture.completedFuture(List.of(new TweetSummaryDto(tweetId1, null, null))));
		given(this.tweetService.getUserTweetsSummary(userId2))
			.willReturn(CompletableFuture.completedFuture(List.of(new TweetSummaryDto(tweetId2, null, null))));
		given(this.followRepository.countByFollowedIdAndStatus(userId1, FollowEntity.Status.ACCEPTED))
			.willReturn(10L);
		given(this.followRepository.countByFollowedIdAndStatus(userId2, FollowEntity.Status.ACCEPTED))
			.willReturn(20L);
		given(this.likeService.getTweetLikesCounts(anyList()))
			.willReturn(CompletableFuture.completedFuture(Map.of(tweetId1, 5L)))
			.willReturn(CompletableFuture.completedFuture(Map.of(tweetId2, 15L)));
		given(this.retweetService.getRetweetCountsForTweets(anyList()))
			.willReturn(CompletableFuture.completedFuture(Map.of(tweetId1, 2L)))
			.willReturn(CompletableFuture.completedFuture(Map.of(tweetId2, 4L)));

		Cache cache = mock(Cache.class);
		given(this.cacheManager.getCache("popular_users")).willReturn(cache);

		Map<UUID, Double> result = this.recommendationService.computePopularUsers();

		assertThat(result).isNotNull().hasSize(2);
		assertThat(result.get(userId1)).isGreaterThan(0.0);
		assertThat(result.get(userId2)).isGreaterThan(0.0);

		verify(this.followRepository).findAllFollowedIds();
		verify(this.tweetService, times(2)).getUserTweetsSummary(any());
		verify(this.followRepository, times(2)).countByFollowedIdAndStatus(any(), eq(FollowEntity.Status.ACCEPTED));
		verify(this.likeService, times(2)).getTweetLikesCounts(anyList());
		verify(this.retweetService, times(2)).getRetweetCountsForTweets(anyList());
	}

}
