/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.scheduler.Scheduler;

import ro.tweebyte.interactionservice.repository.FollowRepository;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that CleanupService.startCleanupTasks() and shutdownScheduler() correctly wire
 * the Scheduler. The cleanup WORK methods (cleanupOrphanLikes, etc.) are covered by
 * CleanupServiceTests and CleanupServiceBranchTests; this suite only exercises the
 * scheduler lifecycle.
 */
@ExtendWith(MockitoExtension.class)
class CleanupServiceSetupTests {

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

	private CleanupService cleanupService;

	@BeforeEach
	void setUp() {
		this.cleanupService = new CleanupService(this.tweetService, this.followRepository, this.likeRepository,
				this.replyRepository, this.retweetRepository);
		// Wire the @Value fields that would normally come from application.properties
		ReflectionTestUtils.setField(this.cleanupService, "schedulerPoolSize", 2);
		ReflectionTestUtils.setField(this.cleanupService, "schedulerQueueCapacity", 4);
		// Wire self-reference (@Resource is not injected by Mockito); point it at this
		// same instance so subscriptions from the Flux.interval flatMaps compile fine
		ReflectionTestUtils.setField(this.cleanupService, "self", this.cleanupService);
	}

	@Test
	void startCleanupTasks_wiresScheduler() {
		// Before @PostConstruct the scheduler field is null
		Object before = ReflectionTestUtils.getField(this.cleanupService, "scheduler");
		assertThat(before).isNull();

		this.cleanupService.startCleanupTasks();

		// After startCleanupTasks the scheduler field must be a non-null Scheduler
		Object after = ReflectionTestUtils.getField(this.cleanupService, "scheduler");
		assertThat(after).isNotNull().isInstanceOf(Scheduler.class);

		// Clean up so test threads don't linger
		this.cleanupService.shutdownScheduler();
	}

	@Test
	void shutdownScheduler_disposesScheduler() {
		this.cleanupService.startCleanupTasks();

		Scheduler scheduler = (Scheduler) ReflectionTestUtils.getField(this.cleanupService, "scheduler");
		assertThat(scheduler).isNotNull();
		assertThat(scheduler.isDisposed()).isFalse();

		this.cleanupService.shutdownScheduler();

		assertThat(scheduler.isDisposed()).isTrue();
	}

	@Test
	void startCleanupTasks_customPoolSize_schedulerNonNull() {
		// Custom pool size is applied via @Value; verify scheduler is wired regardless
		ReflectionTestUtils.setField(this.cleanupService, "schedulerPoolSize", 3);
		ReflectionTestUtils.setField(this.cleanupService, "schedulerQueueCapacity", 6);

		this.cleanupService.startCleanupTasks();

		Object after = ReflectionTestUtils.getField(this.cleanupService, "scheduler");
		assertThat(after).isNotNull().isInstanceOf(Scheduler.class);

		this.cleanupService.shutdownScheduler();
	}

}
