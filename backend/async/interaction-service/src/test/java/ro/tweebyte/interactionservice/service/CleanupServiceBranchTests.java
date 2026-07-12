/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.repository.FollowRepository;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Branch-coverage tests for CleanupService: - cleanupRejectedFollowRequests while-loop
 * entry/exit - cleanupOrphanLikes REPLY (non-TWEET) branch of likeableType check -
 * shutdownScheduler awaitTermination=false branch
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CleanupServiceBranchTests {

	@Mock
	private ScheduledExecutorService scheduler;

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
	void cleanupRejectedFollowRequests_iteratesAndDeletes() {
		// First page non-empty → enters loop body once; second page empty → exits.
		Page<FollowEntity> page = new PageImpl<>(Collections.singletonList(new FollowEntity()));
		given(this.followRepository.findByStatus(any(), any(Pageable.class))).willReturn(page).willReturn(Page.empty());

		this.cleanupService.cleanupRejectedFollowRequests();

		verify(this.followRepository, times(2)).findByStatus(any(), any(Pageable.class));
		verify(this.followRepository).deleteAll(any(Iterable.class));
	}

	@Test
	void cleanupRejectedFollowRequests_emptyOnFirstCall_skipsLoop() {
		// Loop predicate immediately false → body never enters.
		given(this.followRepository.findByStatus(any(), any(Pageable.class))).willReturn(Page.empty());

		this.cleanupService.cleanupRejectedFollowRequests();

		verify(this.followRepository, times(1)).findByStatus(any(), any(Pageable.class));
		verify(this.followRepository, never()).deleteAll(any(Iterable.class));
	}

	@Test
	void cleanupOrphanLikes_tweetTypeStillExists_keepsEntity() {
		// Branch: likeableType == TWEET TRUE arm; tweetService returns summary → return
		// false (keep).
		UUID likeableId = UUID.randomUUID();
		LikeEntity likeEntity = new LikeEntity();
		likeEntity.setLikeableId(likeableId);
		likeEntity.setLikeableType(LikeEntity.LikeableType.TWEET);

		given(this.likeRepository.findAll()).willReturn(Collections.singletonList(likeEntity));
		// A completed future means the tweet still exists; entity not deleted.
		given(this.tweetService.getTweetSummary(likeableId)).willReturn(CompletableFuture.completedFuture(new TweetDto()));

		this.cleanupService.cleanupOrphanLikes();

		verify(this.tweetService).getTweetSummary(likeableId);
		// Nothing orphaned → deletableLikes is empty → deleteInBatches never calls
		// deleteAll.
		verify(this.likeRepository, never()).deleteAll(anyList());
	}

	@Test
	void cleanupOrphanLikes_replyType_usesRepositoryLookup() {
		// likeableType == REPLY → else-branch: replyRepository.findById(...).isEmpty()
		// decides.
		UUID likeableId = UUID.randomUUID();
		LikeEntity likeEntity = new LikeEntity();
		likeEntity.setLikeableId(likeableId);
		likeEntity.setLikeableType(LikeEntity.LikeableType.REPLY);

		given(this.likeRepository.findAll()).willReturn(Collections.singletonList(likeEntity));
		given(this.replyRepository.existsById(likeableId)).willReturn(false); // orphan →
																			// delete

		this.cleanupService.cleanupOrphanLikes();

		verify(this.likeRepository).findAll();
		verify(this.replyRepository).existsById(likeableId);
		verify(this.likeRepository).deleteAll(anyList());
	}

	@Test
	void cleanupOrphanLikes_replyTypeStillReferenced_keepsEntity() {
		// findById non-empty → filter returns false → entity not deleted.
		UUID likeableId = UUID.randomUUID();
		LikeEntity likeEntity = new LikeEntity();
		likeEntity.setLikeableId(likeableId);
		likeEntity.setLikeableType(LikeEntity.LikeableType.REPLY);

		given(this.likeRepository.findAll()).willReturn(Collections.singletonList(likeEntity));
		given(this.replyRepository.existsById(likeableId)).willReturn(true);

		this.cleanupService.cleanupOrphanLikes();

		verify(this.replyRepository).existsById(likeableId);
		// Reply still referenced → kept → empty batch → no deleteAll.
		verify(this.likeRepository, never()).deleteAll(anyList());
	}

	@Test
	void shutdownScheduler_awaitTerminationFalse_callsShutdownNow() throws InterruptedException {
		// Branch: awaitTermination returns false → scheduler.shutdownNow() invoked.
		given(this.scheduler.awaitTermination(anyLong(), any(TimeUnit.class))).willReturn(false);

		this.cleanupService.shutdownScheduler();

		verify(this.scheduler).shutdown();
		verify(this.scheduler).awaitTermination(anyLong(), any(TimeUnit.class));
		verify(this.scheduler).shutdownNow();
	}

	@Test
	void shutdownScheduler_awaitTerminationTrue_skipsShutdownNow() throws InterruptedException {
		// Branch: awaitTermination returns true → shutdownNow NOT invoked.
		given(this.scheduler.awaitTermination(anyLong(), any(TimeUnit.class))).willReturn(true);

		this.cleanupService.shutdownScheduler();

		verify(this.scheduler).shutdown();
		verify(this.scheduler, never()).shutdownNow();
	}

	@Test
	void shutdownScheduler_interrupted_swallowsAndShutsDown() throws InterruptedException {
		// Branch: awaitTermination throws InterruptedException → catch invokes
		// shutdownNow() and restores the interrupt status.
		willThrow(new InterruptedException("boom")).given(this.scheduler).awaitTermination(anyLong(), any(TimeUnit.class));

		this.cleanupService.shutdownScheduler();

		verify(this.scheduler).shutdown();
		verify(this.scheduler).shutdownNow();
	}

}
