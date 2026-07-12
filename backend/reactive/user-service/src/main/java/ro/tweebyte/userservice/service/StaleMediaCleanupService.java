/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

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

	@Value("${app.cleanup.scheduler.pool-size:1}")
	private int schedulerPoolSize;

	@Value("${app.cleanup.scheduler.queue-capacity:1}")
	private int schedulerQueueCapacity;

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

	private Scheduler scheduler;

	@PostConstruct
	public void startCleanupTasks() {
		this.scheduler = Schedulers.newBoundedElastic(this.schedulerPoolSize, this.schedulerQueueCapacity,
				"StaleMediaCleanupService");
		Flux.interval(Duration.ofHours(this.schedulerInitialDelayHours), Duration.ofHours(this.staleMediaPeriodHours),
				this.scheduler)
			.flatMap(tick -> cleanupStaleMedia())
			.subscribe();
	}

	public Mono<Void> cleanupStaleMedia() {
		LocalDateTime cutoff = LocalDateTime.now().minusDays(this.staleAfterDays);
		// The default-avatar sentinel is permanently reachable (fresh registrations
		// point at it and "remove my picture" swaps back to it), so it must survive
		// every sweep even at moments when no user row happens to reference it.
		Mono<Set<UUID>> referenced = Flux.merge(this.userRepository.findReferencedProfilePictureIds(),
				this.tweetClient.getReferencedMediaIds(), this.interactionClient.getReferencedMediaIds(),
				// Transitive edge: a surviving preview pins its original via
				// source_media_id.
				this.mediaAssetRepository.findReferencedSourceMediaIds(), Flux.just(MediaConstants.DEFAULT_AVATAR_ID))
			.collect(Collectors.toSet());

		return referenced
			.flatMap(refIds -> this.mediaAssetRepository.findStaleIds(cutoff)
				.filter(id -> !refIds.contains(id))
				.buffer(100)
				.flatMap(this.mediaAssetRepository::deleteAllById)
				.then())
			// Fail closed: if any reachability source is unreachable we cannot prove a
			// row is unreferenced, so this tick deletes nothing and the interval retries
			// next period rather than risk deleting live media.
			.onErrorResume(e -> Mono.empty());
	}

	@PreDestroy
	public void shutdownScheduler() {
		this.scheduler.dispose();
	}

}
