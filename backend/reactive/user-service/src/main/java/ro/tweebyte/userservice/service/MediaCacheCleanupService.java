/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.service;

import java.time.Duration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Bounds {@link MediaCache} to its most-recently-used entries so the process-local store
 * can't grow without limit in a long-lived deployment. Pruning runs with a 24-hour
 * INITIAL DELAY so a single benchmark or dev session (minutes, not days) never evicts —
 * the resident benchmark assets stay hot and every request remains a cache hit, keeping
 * the latency / throughput numbers free of eviction noise. Mirrors interaction-service's
 * CleanupService: same 24h initial delay via Flux.interval on a bounded-elastic
 * scheduler, same app.cleanup.* gating + sizing, switched off entirely in the benchmark
 * profile via app.cleanup.enabled=false.
 *
 * @author Andrei Zbarcea
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MediaCacheCleanupService {

	@Value("${app.cleanup.scheduler.pool-size:1}")
	private int schedulerPoolSize;

	@Value("${app.cleanup.scheduler.queue-capacity:1}")
	private int schedulerQueueCapacity;

	@Value("${app.cleanup.scheduler.initial-delay-hours:24}")
	private long schedulerInitialDelayHours;

	@Value("${app.cleanup.media-cache.period-hours:1}")
	private long mediaCachePeriodHours;

	@Value("${app.cleanup.media.max-entries:10}")
	private int maxEntries;

	private final MediaCache cache;

	private Scheduler scheduler;

	@PostConstruct
	public void startCleanupTasks() {
		this.scheduler = Schedulers.newBoundedElastic(this.schedulerPoolSize, this.schedulerQueueCapacity,
				"MediaCacheCleanup");
		Flux.interval(Duration.ofHours(this.schedulerInitialDelayHours), Duration.ofHours(this.mediaCachePeriodHours),
				this.scheduler)
			.doOnNext(tick -> this.cache.evictToMostRecentlyUsed(this.maxEntries))
			.subscribe();
	}

	@PreDestroy
	public void shutdownScheduler() {
		this.scheduler.dispose();
	}

}
