/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.CacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.model.TweetSummaryDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.FollowRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;

/**
 * Covers the RecommendationService compute paths the branch suite leaves open: the
 * recommendUsersToFollow async wrapper, computePopularUsers' GROUP-BY scoring, and the
 * fetchPopularUsers cache-absent arm (cacheManager returns no "popular_users" cache, so the
 * recommendation falls back to a live compute).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecommendationServiceComputeTests {

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

	@Mock
	private ExecutorService executorService;

	@InjectMocks
	private RecommendationService recommendationService;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(this.recommendationService, "self", this.recommendationService);
		// recommendUsersToFollow/computePopularUsers run on supplyAsync(..., executorService) —
		// run the task inline so the CompletableFuture completes synchronously here.
		willAnswer(invocation -> {
			((Runnable) invocation.getArgument(0)).run();
			return null;
		}).given(this.executorService).execute(any(Runnable.class));
	}

	@Test
	void recommendUsersToFollow_DelegatesToCacheableRecommendations() throws Exception {
		UUID userId = UUID.randomUUID();
		given(this.followRepository.findByFollowerIdAndStatus(any(), any())).willReturn(List.of());
		// No "popular_users" cache → fetchPopularUsers computes live (which itself has no
		// followed ids), so the recommendation list is empty.
		given(this.cacheManager.getCache("popular_users")).willReturn(null);
		given(this.followRepository.findAllFollowedIds()).willReturn(List.of());

		List<UserDto> result = this.recommendationService.recommendUsersToFollow(userId).get();

		assertThat(result).isEmpty();
	}

	@Test
	void computePopularUsers_ScoresAndLimitsByDescendingScore() {
		UUID popular = UUID.randomUUID();
		UUID quiet = UUID.randomUUID();
		UUID tweetId = UUID.randomUUID();
		given(this.followRepository.findAllFollowedIds()).willReturn(List.of(popular, quiet));

		// popular: one tweet, 4 followers, likes/retweets aggregate.
		TweetSummaryDto summary = new TweetSummaryDto();
		summary.setId(tweetId);
		given(this.tweetService.getUserTweetsSummary(popular)).willReturn(CompletableFuture.completedFuture(List.of(summary)));
		given(this.tweetService.getUserTweetsSummary(quiet)).willReturn(CompletableFuture.completedFuture(List.of()));
		given(this.followRepository.countByFollowedIdAndStatus(popular, FollowEntity.Status.ACCEPTED)).willReturn(4L);
		given(this.followRepository.countByFollowedIdAndStatus(quiet, FollowEntity.Status.ACCEPTED)).willReturn(0L);
		given(this.likeService.getTweetLikesCounts(List.of(tweetId)))
			.willReturn(CompletableFuture.completedFuture(Map.of(tweetId, 10L)));
		given(this.retweetService.getRetweetCountsForTweets(List.of(tweetId)))
			.willReturn(CompletableFuture.completedFuture(Map.of(tweetId, 2L)));
		given(this.likeService.getTweetLikesCounts(List.of()))
			.willReturn(CompletableFuture.completedFuture(Map.of()));
		given(this.retweetService.getRetweetCountsForTweets(List.of()))
			.willReturn(CompletableFuture.completedFuture(Map.of()));

		Map<UUID, Double> scores = this.recommendationService.computePopularUsers();

		// popular = 4 followers + 10 likes + 2 retweets = 16.0; quiet = 0.0.
		assertThat(scores).containsEntry(popular, 16.0).containsEntry(quiet, 0.0);
		// Descending order: popular comes first.
		assertThat(scores.keySet().iterator().next()).isEqualTo(popular);
	}

}
