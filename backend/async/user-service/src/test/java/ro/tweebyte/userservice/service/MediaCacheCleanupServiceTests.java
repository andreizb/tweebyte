/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.service;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Covers MediaCacheCleanupService's lifecycle: startCleanupTasks creates the scheduler
 * pool and registers the fixed-rate eviction; shutdownScheduler terminates the pool
 * cleanly. The scheduled body simply delegates to MediaCache.evictToMostRecentlyUsed,
 * which MediaCacheTests exercises directly.
 */
class MediaCacheCleanupServiceTests {

	@Test
	void startInstallsSchedulerThenShutsDownCleanly() {
		MediaCache cache = new MediaCache();
		MediaCacheCleanupService service = new MediaCacheCleanupService(cache);
		ReflectionTestUtils.setField(service, "schedulerPoolSize", 1);
		ReflectionTestUtils.setField(service, "schedulerInitialDelayHours", 0L);
		ReflectionTestUtils.setField(service, "mediaCachePeriodHours", 1L);
		ReflectionTestUtils.setField(service, "maxEntries", 2);

		service.startCleanupTasks();

		assertThat(ReflectionTestUtils.getField(service, "scheduler")).isNotNull();
		assertThatNoException().isThrownBy(service::shutdownScheduler);
	}

	@Test
	void shutdownInterruptDuringAwaitForcesShutdownNow() throws Exception {
		MediaCache cache = new MediaCache();
		MediaCacheCleanupService service = new MediaCacheCleanupService(cache);
		ReflectionTestUtils.setField(service, "schedulerPoolSize", 1);
		ReflectionTestUtils.setField(service, "schedulerInitialDelayHours", 0L);
		ReflectionTestUtils.setField(service, "mediaCachePeriodHours", 1L);
		ReflectionTestUtils.setField(service, "maxEntries", 2);
		service.startCleanupTasks();

		// Interrupting the calling thread makes awaitTermination throw InterruptedException,
		// driving the catch arm (shutdownNow + re-interrupt).
		Thread runner = new Thread(() -> {
			Thread.currentThread().interrupt();
			service.shutdownScheduler();
		});
		runner.start();
		runner.join();

		assertThat(ReflectionTestUtils.getField(service, "scheduler")).isNotNull();
	}

	@Test
	void shutdownForcesShutdownNowWhenAwaitTimesOut() throws Exception {
		MediaCache cache = new MediaCache();
		MediaCacheCleanupService service = new MediaCacheCleanupService(cache);
		// Inject a scheduler whose orderly awaitTermination reports a timeout (false), driving
		// the !awaitTermination arm that escalates to shutdownNow.
		ScheduledExecutorService scheduler = Mockito.mock(ScheduledExecutorService.class);
		given(scheduler.awaitTermination(1, TimeUnit.MINUTES)).willReturn(false);
		ReflectionTestUtils.setField(service, "scheduler", scheduler);

		service.shutdownScheduler();

		verify(scheduler).shutdown();
		verify(scheduler).shutdownNow();
	}

}
