/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.time.Duration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import ro.tweebyte.interactionservice.exception.TweetNotFoundException;
import ro.tweebyte.interactionservice.model.LikeableType;
import ro.tweebyte.interactionservice.model.Status;
import ro.tweebyte.interactionservice.repository.FollowRepository;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CleanupService {

	private final TweetService tweetService;

	private final FollowRepository followRepository;

	private final LikeRepository likeRepository;

	private final ReplyRepository replyRepository;

	private final RetweetRepository retweetRepository;

	@Lazy
	@Resource(name = "cleanupService")
	private CleanupService self;

	@Value("${app.cleanup.scheduler.pool-size:5}")
	private int schedulerPoolSize;

	@Value("${app.cleanup.scheduler.queue-capacity:10}")
	private int schedulerQueueCapacity;

	@Value("${app.cleanup.scheduler.initial-delay-hours:24}")
	private long schedulerInitialDelayHours;

	@Value("${app.cleanup.rejected-follow-requests.period-hours:3}")
	private long rejectedFollowRequestsPeriodHours;

	@Value("${app.cleanup.orphan-likes.period-hours:2}")
	private long orphanLikesPeriodHours;

	@Value("${app.cleanup.orphan-replies.period-hours:1}")
	private long orphanRepliesPeriodHours;

	@Value("${app.cleanup.orphan-retweets.period-hours:1}")
	private long orphanRetweetsPeriodHours;

	private Scheduler scheduler;

	/**
	 * Cleanup tasks fire with a 24-hour INITIAL DELAY so a single benchmark or dev
	 * session (typically minutes, not days) sees no cleanup noise in its latency / GC
	 * numbers. Once the application has been up for 24h (production-like), the schedules
	 * tick at their configured periods. The async stack's
	 * CleanupService.startCleanupTasks uses the same 24h initial delay via
	 * ScheduledExecutorService — same observable behaviour on both stacks.
	 */
	@PostConstruct
	public void startCleanupTasks() {
		this.scheduler = Schedulers.newBoundedElastic(this.schedulerPoolSize, this.schedulerQueueCapacity,
				"CleanupService");
		Flux.interval(Duration.ofHours(this.schedulerInitialDelayHours),
				Duration.ofHours(this.rejectedFollowRequestsPeriodHours), this.scheduler)
			.flatMap(tick -> this.self.cleanupRejectedFollowRequests())
			.subscribe();

		Flux.interval(Duration.ofHours(this.schedulerInitialDelayHours), Duration.ofHours(this.orphanLikesPeriodHours),
				this.scheduler)
			.flatMap(tick -> this.self.cleanupOrphanLikes())
			.subscribe();

		Flux.interval(Duration.ofHours(this.schedulerInitialDelayHours), Duration.ofHours(this.orphanRepliesPeriodHours),
				this.scheduler)
			.flatMap(tick -> this.self.cleanupOrphanReplies())
			.subscribe();

		Flux.interval(Duration.ofHours(this.schedulerInitialDelayHours), Duration.ofHours(this.orphanRetweetsPeriodHours),
				this.scheduler)
			.flatMap(tick -> this.self.cleanupOrphanRetweets())
			.subscribe();
	}

	@Transactional
	public Mono<Void> cleanupRejectedFollowRequests() {
		return this.followRepository.findByStatus(Status.REJECTED.name())
			.buffer(100)
			.flatMap(this.followRepository::deleteAll)
			.then();
	}

	@Transactional
	public Mono<Void> cleanupOrphanLikes() {
		return this.likeRepository.findAll()
			.buffer(100)
			.flatMap(likes -> Flux.fromIterable(likes).filterWhen(likeEntity -> {
				if (likeEntity.getLikeableType().equals(LikeableType.TWEET.name())) {
					return this.tweetService.getTweetSummary(likeEntity.getLikeableId())
						.thenReturn(false)
						.onErrorResume(TweetNotFoundException.class, e -> Mono.just(true));
				}
				else {
					return this.replyRepository.existsById(likeEntity.getLikeableId()).map(exists -> !exists);
				}
			}).collectList())
			.flatMap(this.likeRepository::deleteAll)
			.then();
	}

	@Transactional
	public Mono<Void> cleanupOrphanReplies() {
		return this.replyRepository.findAll()
			.buffer(100)
			.flatMap(replies -> Flux.fromIterable(replies)
				.filterWhen(replyEntity -> this.tweetService.getTweetSummary(replyEntity.getTweetId())
					.thenReturn(false)
					.onErrorResume(TweetNotFoundException.class, e -> Mono.just(true)))
				.collectList())
			.flatMap(this.replyRepository::deleteAll)
			.then();
	}

	@Transactional
	public Mono<Void> cleanupOrphanRetweets() {
		return this.retweetRepository.findAll()
			.buffer(100)
			.flatMap(retweets -> Flux.fromIterable(retweets)
				.filterWhen(retweetEntity -> this.tweetService.getTweetSummary(retweetEntity.getOriginalTweetId())
					.thenReturn(false)
					.onErrorResume(TweetNotFoundException.class, e -> Mono.just(true)))
				.collectList())
			.flatMap(this.retweetRepository::deleteAll)
			.then();
	}

	@PreDestroy
	public void shutdownScheduler() {
		this.scheduler.dispose();
	}

}
