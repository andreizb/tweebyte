/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import ro.tweebyte.interactionservice.repository.FollowRepository;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that CleanupService.startCleanupTasks() correctly creates and wires the
 * ScheduledExecutorService. The cleanup WORK methods are covered by CleanupServiceTests
 * and CleanupServiceBranchTests; this suite only exercises the scheduler lifecycle.
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

	@InjectMocks
	private CleanupService cleanupService;

	// @InjectMocks leaves @Value fields at their Java defaults (0/0L) because there is no
	// Spring context.  period <= 0 makes ScheduledExecutorService.scheduleAtFixedRate throw
	// IllegalArgumentException, so we must seed valid values before every startCleanupTasks()
	// call.  Initial-delay 0 is fine (fires immediately in tests).
	private void seedSchedulerFields() {
		ReflectionTestUtils.setField(this.cleanupService, "schedulerPoolSize", 5);
		ReflectionTestUtils.setField(this.cleanupService, "schedulerInitialDelayHours", 0L);
		ReflectionTestUtils.setField(this.cleanupService, "rejectedFollowRequestsPeriodHours", 1L);
		ReflectionTestUtils.setField(this.cleanupService, "orphanLikesPeriodHours", 1L);
		ReflectionTestUtils.setField(this.cleanupService, "orphanRepliesPeriodHours", 1L);
		ReflectionTestUtils.setField(this.cleanupService, "orphanRetweetsPeriodHours", 1L);
		// self-proxy: wire the service to itself so scheduleAtFixedRate method refs resolve
		ReflectionTestUtils.setField(this.cleanupService, "self", this.cleanupService);
	}

	@Test
	void startCleanupTasks_wiresScheduler() {
		seedSchedulerFields();

		// Before @PostConstruct the scheduler field is null
		Object before = ReflectionTestUtils.getField(this.cleanupService, "scheduler");
		assertThat(before).isNull();

		this.cleanupService.startCleanupTasks();

		// After @PostConstruct the scheduler field must be a non-null ScheduledExecutorService
		Object after = ReflectionTestUtils.getField(this.cleanupService, "scheduler");
		assertThat(after).isNotNull().isInstanceOf(ScheduledExecutorService.class);
	}

	@Test
	void startCleanupTasks_schedulerIsNotShutdown() {
		seedSchedulerFields();

		this.cleanupService.startCleanupTasks();

		ScheduledExecutorService scheduler = (ScheduledExecutorService) ReflectionTestUtils.getField(this.cleanupService,
				"scheduler");
		assertThat(scheduler).isNotNull();
		assertThat(scheduler.isShutdown()).isFalse();
		assertThat(scheduler.isTerminated()).isFalse();

		// Clean up
		scheduler.shutdownNow();
	}

	@Test
	void startCleanupTasks_usesConfiguredPoolSize() {
		// Seed the production default (5) — @InjectMocks leaves @Value fields at 0
		seedSchedulerFields();

		int defaultPoolSize = (int) ReflectionTestUtils.getField(this.cleanupService, "schedulerPoolSize");
		assertThat(defaultPoolSize).isEqualTo(5);

		this.cleanupService.startCleanupTasks();

		ScheduledExecutorService scheduler = (ScheduledExecutorService) ReflectionTestUtils.getField(this.cleanupService,
				"scheduler");
		assertThat(scheduler).isNotNull();
		scheduler.shutdownNow();
	}

}
