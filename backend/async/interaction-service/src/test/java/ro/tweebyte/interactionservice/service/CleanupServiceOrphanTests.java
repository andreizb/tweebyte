/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.repository.FollowRepository;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Covers the CleanupService orphan-sweep arms the existing suites leave open: the
 * tweet-still-exists keep branch for replies/retweets, the non-TweetNotFoundException
 * rethrow that aborts a sweep, and the startCleanupTasks scheduler wiring.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CleanupServiceOrphanTests {

	@Mock
	private TweetService tweetService;

	@Mock
	private FollowRepository followRepository;

	@Mock
	private LikeRepository likeRepository;

	@Mock
	private ReplyRepository replyRepository;

	@Mock
	private RetweetRepository retweetRepository;

	@InjectMocks
	private CleanupService cleanupService;

	@AfterEach
	void tearDown() {
		ScheduledExecutorService scheduler = (ScheduledExecutorService) ReflectionTestUtils.getField(this.cleanupService,
				"scheduler");
		if (scheduler != null) {
			scheduler.shutdownNow();
		}
	}

	@BeforeEach
	void seedSchedulerFields() {
		// @InjectMocks leaves @Value fields at 0; period <= 0 throws IllegalArgumentException
		// in scheduleAtFixedRate.  Seed valid values so startCleanupTasks() can wire the
		// scheduler without a Spring context.  initial-delay 0 is fine in unit tests.
		ReflectionTestUtils.setField(this.cleanupService, "schedulerPoolSize", 1);
		ReflectionTestUtils.setField(this.cleanupService, "schedulerInitialDelayHours", 0L);
		ReflectionTestUtils.setField(this.cleanupService, "rejectedFollowRequestsPeriodHours", 1L);
		ReflectionTestUtils.setField(this.cleanupService, "orphanLikesPeriodHours", 1L);
		ReflectionTestUtils.setField(this.cleanupService, "orphanRepliesPeriodHours", 1L);
		ReflectionTestUtils.setField(this.cleanupService, "orphanRetweetsPeriodHours", 1L);
	}

	@Test
	void cleanupOrphanReplies_TweetExists_KeepsReply() {
		UUID tweetId = UUID.randomUUID();
		ReplyEntity reply = new ReplyEntity();
		reply.setTweetId(tweetId);
		given(this.replyRepository.findAll()).willReturn(Collections.singletonList(reply));
		given(this.tweetService.getTweetSummary(tweetId)).willReturn(CompletableFuture.completedFuture(new TweetDto()));

		this.cleanupService.cleanupOrphanReplies();

		verify(this.replyRepository, never()).deleteAll(anyList());
	}

	@Test
	void cleanupOrphanRetweets_TweetExists_KeepsRetweet() {
		UUID tweetId = UUID.randomUUID();
		RetweetEntity retweet = new RetweetEntity();
		retweet.setOriginalTweetId(tweetId);
		given(this.retweetRepository.findAll()).willReturn(Collections.singletonList(retweet));
		given(this.tweetService.getTweetSummary(tweetId)).willReturn(CompletableFuture.completedFuture(new TweetDto()));

		this.cleanupService.cleanupOrphanRetweets();

		verify(this.retweetRepository, never()).deleteAll(anyList());
	}

	@Test
	void cleanupOrphanReplies_NonNotFoundError_AbortsAndRethrows() {
		// A non-TweetNotFoundException (e.g. a transient client failure) must not be
		// mistaken for an orphan: it aborts the sweep so nothing is wrongly deleted.
		UUID tweetId = UUID.randomUUID();
		ReplyEntity reply = new ReplyEntity();
		reply.setTweetId(tweetId);
		given(this.replyRepository.findAll()).willReturn(Collections.singletonList(reply));
		given(this.tweetService.getTweetSummary(tweetId))
			.willReturn(CompletableFuture.failedFuture(new IllegalStateException("upstream down")));

		Throwable ex = catchThrowable(() -> this.cleanupService.cleanupOrphanReplies());
		assertThat(ex).isInstanceOf(RuntimeException.class);
		verify(this.replyRepository, never()).deleteAll(anyList());
	}

	@Test
	void cleanupOrphanLikes_NonNotFoundError_AbortsAndRethrows() {
		UUID tweetId = UUID.randomUUID();
		ro.tweebyte.interactionservice.entity.LikeEntity like = new ro.tweebyte.interactionservice.entity.LikeEntity();
		like.setLikeableId(tweetId);
		like.setLikeableType(ro.tweebyte.interactionservice.entity.LikeEntity.LikeableType.TWEET);
		given(this.likeRepository.findAll()).willReturn(Collections.singletonList(like));
		given(this.tweetService.getTweetSummary(tweetId))
			.willReturn(CompletableFuture.failedFuture(new IllegalStateException("upstream down")));

		Throwable ex = catchThrowable(() -> this.cleanupService.cleanupOrphanLikes());
		assertThat(ex).isInstanceOf(RuntimeException.class);
		verify(this.likeRepository, never()).deleteAll(anyList());
	}

	@Test
	void cleanupOrphanRetweets_NonNotFoundError_AbortsAndRethrows() {
		UUID tweetId = UUID.randomUUID();
		RetweetEntity retweet = new RetweetEntity();
		retweet.setOriginalTweetId(tweetId);
		given(this.retweetRepository.findAll()).willReturn(Collections.singletonList(retweet));
		given(this.tweetService.getTweetSummary(tweetId))
			.willReturn(CompletableFuture.failedFuture(new IllegalStateException("upstream down")));

		Throwable ex = catchThrowable(() -> this.cleanupService.cleanupOrphanRetweets());
		assertThat(ex).isInstanceOf(RuntimeException.class);
		verify(this.retweetRepository, never()).deleteAll(anyList());
	}

	@Test
	void startCleanupTasks_CreatesScheduler() {
		// startCleanupTasks schedules the self-proxied sweep method references, so `self`
		// must be wired before the @PostConstruct runs.
		ReflectionTestUtils.setField(this.cleanupService, "self", this.cleanupService);

		this.cleanupService.startCleanupTasks();

		ScheduledExecutorService scheduler = (ScheduledExecutorService) ReflectionTestUtils.getField(this.cleanupService,
				"scheduler");
		assertThat(scheduler).isNotNull();
		assertThat(scheduler.isShutdown()).isFalse();
	}

}
