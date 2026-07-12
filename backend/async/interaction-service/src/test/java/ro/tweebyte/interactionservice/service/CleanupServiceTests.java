/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.exception.TweetNotFoundException;
import ro.tweebyte.interactionservice.repository.FollowRepository;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@SpringBootTest
class CleanupServiceTests {

	@Mock
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

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

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(this.cleanupService, "scheduler", this.scheduler);
	}

	@Test
	void testCleanupOrphanLikes() {
		LikeEntity like = new LikeEntity();
		like.setLikeableType(LikeEntity.LikeableType.TWEET);
		given(this.likeRepository.findAll()).willReturn(Collections.singletonList(like));
		given(this.tweetService.getTweetSummary(any()))
			.willReturn(CompletableFuture.failedFuture(new TweetNotFoundException("not found")));

		this.cleanupService.cleanupOrphanLikes();

		verify(this.likeRepository).findAll();
		verify(this.likeRepository).deleteAll(anyList());
	}

	@Test
	void testCleanupOrphanReplies() {
		given(this.replyRepository.findAll()).willReturn(Collections.singletonList(new ReplyEntity()));
		given(this.tweetService.getTweetSummary(any()))
			.willReturn(CompletableFuture.failedFuture(new TweetNotFoundException("not found")));

		this.cleanupService.cleanupOrphanReplies();

		verify(this.replyRepository).findAll();
		verify(this.replyRepository).deleteAll(anyList());
	}

	@Test
	void testCleanupOrphanRetweets() {
		given(this.retweetRepository.findAll()).willReturn(Collections.singletonList(new RetweetEntity()));
		given(this.tweetService.getTweetSummary(any()))
			.willReturn(CompletableFuture.failedFuture(new TweetNotFoundException("not found")));

		this.cleanupService.cleanupOrphanRetweets();

		verify(this.retweetRepository).findAll();
		verify(this.retweetRepository).deleteAll(anyList());
	}

	@Test
	void testSchedulerShutdown() throws InterruptedException {
		this.cleanupService.shutdownScheduler();
		verify(this.scheduler).shutdown();
		verify(this.scheduler).awaitTermination(anyLong(), any());
	}

}
