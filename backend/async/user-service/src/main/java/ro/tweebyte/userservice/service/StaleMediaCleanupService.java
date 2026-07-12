/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import ro.tweebyte.userservice.client.InteractionClient;
import ro.tweebyte.userservice.client.TweetClient;
import ro.tweebyte.userservice.repository.MediaAssetRepository;
import ro.tweebyte.userservice.repository.UserRepository;
import ro.tweebyte.userservice.util.MediaConstants;

/**
 * Stale-media GC. media_assets is content-addressed (one row can back several references)
 * and reachable from three databases — users.profile_picture_id (local, in-DB FK),
 * tweets.media_ids (tweet-service) and replies/retweets.media_ids (interaction-service) —
 * plus one transitive edge inside media_assets itself: a preview row pins its original
 * through source_media_id, so an original stays reachable as long as any preview of it
 * survives. A row is orphaned only when NONE of those reference it, so the sweep unions
 * all reachability sources before deleting. The 30-day created_at floor gives freshly
 * uploaded but not-yet-attached media a grace window (upload and reference are a two-step
 * client flow). The 24h INITIAL DELAY keeps a benchmark/dev session free of cleanup
 * noise; the whole bean is switched off in the benchmark profile via
 * app.cleanup.enabled=false. Mirrors interaction-service's CleanupService.
 *
 * @author Andrei Zbarcea
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StaleMediaCleanupService {

	private static final int BATCH_SIZE = 100;

	@Value("${app.cleanup.scheduler.pool-size:1}")
	private int schedulerPoolSize;

	@Value("${app.cleanup.scheduler.initial-delay-hours:24}")
	private long schedulerInitialDelayHours;

	@Value("${app.cleanup.stale-media.period-hours:1}")
	private long staleMediaPeriodHours;

	@Value("${app.cleanup.media.stale-after-days:30}")
	private long staleAfterDays;

	private final UserRepository userRepository;

	private final MediaAssetRepository mediaAssetRepository;

	private final TweetClient tweetClient;

	private final InteractionClient interactionClient;

	private ScheduledExecutorService scheduler;

	@PostConstruct
	public void startCleanupTasks() {
		this.scheduler = Executors.newScheduledThreadPool(this.schedulerPoolSize);
		this.scheduler.scheduleAtFixedRate(this::runCleanupStaleMedia, this.schedulerInitialDelayHours,
				this.staleMediaPeriodHours, TimeUnit.HOURS);
	}

	// Wrapper so an exception (e.g. a peer service being unreachable) doesn't cancel
	// the fixed-rate schedule. Fail closed: a failed tick deletes nothing and the
	// next tick retries, rather than risk deleting live media on partial reachability.
	private void runCleanupStaleMedia() {
		try {
			cleanupStaleMedia();
		}
		catch (Exception ignored) {
			// next tick retries
		}
	}

	public void cleanupStaleMedia() {
		LocalDateTime cutoff = LocalDateTime.now().minusDays(this.staleAfterDays);

		Set<UUID> referenced = new HashSet<>(this.userRepository.findReferencedProfilePictureIds());
		referenced.addAll(this.tweetClient.getReferencedMediaIds().join());
		referenced.addAll(this.interactionClient.getReferencedMediaIds().join());
		// Transitive edge: a surviving preview pins its original via source_media_id.
		referenced.addAll(this.mediaAssetRepository.findReferencedSourceMediaIds());
		// The default-avatar sentinel is permanently reachable (fresh registrations
		// point at it and "remove my picture" swaps back to it), so it must survive
		// every sweep even at moments when no user row happens to reference it.
		referenced.add(MediaConstants.DEFAULT_AVATAR_ID);

		List<UUID> orphans = this.mediaAssetRepository.findStaleIds(cutoff)
			.stream()
			.filter(id -> !referenced.contains(id))
			.toList();

		for (int i = 0; i < orphans.size(); i += BATCH_SIZE) {
			this.mediaAssetRepository
				.deleteAllByIdInBatch(orphans.subList(i, Math.min(i + BATCH_SIZE, orphans.size())));
		}
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
