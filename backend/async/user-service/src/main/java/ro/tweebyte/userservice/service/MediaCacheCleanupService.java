/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Bounds {@link MediaCache} to its most-recently-used entries so the process-local store
 * can't grow without limit in a long-lived deployment. Pruning runs with a 24-hour
 * INITIAL DELAY so a single benchmark or dev session (minutes, not days) never evicts —
 * the resident benchmark assets stay hot and every request remains a cache hit, keeping
 * the latency / throughput numbers free of eviction noise. Mirrors interaction-service's
 * CleanupService: same 24h initial delay, same app.cleanup.* gating + pool sizing,
 * switched off entirely in the benchmark profile via app.cleanup.enabled=false.
 *
 * @author Andrei Zbarcea
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MediaCacheCleanupService {

	@Value("${app.cleanup.scheduler.pool-size:1}")
	private int schedulerPoolSize;

	@Value("${app.cleanup.scheduler.initial-delay-hours:24}")
	private long schedulerInitialDelayHours;

	@Value("${app.cleanup.media-cache.period-hours:1}")
	private long mediaCachePeriodHours;

	@Value("${app.cleanup.media.max-entries:10}")
	private int maxEntries;

	private final MediaCache cache;

	private ScheduledExecutorService scheduler;

	@PostConstruct
	public void startCleanupTasks() {
		this.scheduler = Executors.newScheduledThreadPool(this.schedulerPoolSize);
		this.scheduler.scheduleAtFixedRate(() -> this.cache.evictToMostRecentlyUsed(this.maxEntries),
				this.schedulerInitialDelayHours, this.mediaCachePeriodHours, TimeUnit.HOURS);
	}

	@PreDestroy
	public void shutdownScheduler() {
		this.scheduler.shutdown();
		try {
			if (!this.scheduler.awaitTermination(1, TimeUnit.MINUTES)) {
				this.scheduler.shutdownNow();
			}
		}
		catch (InterruptedException ex) {
			this.scheduler.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

}
