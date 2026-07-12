/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.exception.TweetNotFoundException;
import ro.tweebyte.interactionservice.repository.FollowRepository;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CleanupService {

	private static final int BATCH_SIZE = 100;

	@Value("${app.cleanup.scheduler.pool-size:5}")
	private int schedulerPoolSize;

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

	private ScheduledExecutorService scheduler;

	private final TweetService tweetService;

	private final FollowRepository followRepository;

	private final LikeRepository likeRepository;

	private final ReplyRepository replyRepository;

	private final RetweetRepository retweetRepository;

	@Lazy
	@Resource(name = "cleanupService")
	private CleanupService self;

	/**
	 * Cleanup tasks fire with a 24-hour INITIAL DELAY so the orphan/cleanup sweeps don't
	 * run during a single benchmark or dev session (typically minutes, not days), keeping
	 * latency / GC measurements free of cleanup noise. Once the
	 * application has been up for 24h (production-like), the schedules tick at their
	 * configured periods. The reactive stack's CleanupService.startCleanupTasks uses the
	 * same 24h initial delay via Flux.interval — same observable behaviour on both
	 * stacks.
	 */
	@PostConstruct
	public void startCleanupTasks() {
		this.scheduler = Executors.newScheduledThreadPool(this.schedulerPoolSize);
		this.scheduler.scheduleAtFixedRate(this.self::cleanupRejectedFollowRequests, this.schedulerInitialDelayHours,
				this.rejectedFollowRequestsPeriodHours, TimeUnit.HOURS);
		this.scheduler.scheduleAtFixedRate(this.self::cleanupOrphanLikes, this.schedulerInitialDelayHours,
				this.orphanLikesPeriodHours, TimeUnit.HOURS);
		this.scheduler.scheduleAtFixedRate(this.self::cleanupOrphanReplies, this.schedulerInitialDelayHours,
				this.orphanRepliesPeriodHours, TimeUnit.HOURS);
		this.scheduler.scheduleAtFixedRate(this.self::cleanupOrphanRetweets, this.schedulerInitialDelayHours,
				this.orphanRetweetsPeriodHours, TimeUnit.HOURS);
	}

	@Transactional
	public void cleanupRejectedFollowRequests() {
		// Always fetch page 0: each iteration deletes the whole page, so the next
		// batch of rejected rows shifts down into page 0. Advancing the page index
		// while deleting would step past the rows that shifted and skip them.
		Page<FollowEntity> followEntities = this.followRepository.findByStatus(FollowEntity.Status.REJECTED,
				PageRequest.of(0, BATCH_SIZE));
		while (!followEntities.isEmpty()) {
			this.followRepository.deleteAll(followEntities);
			followEntities = this.followRepository.findByStatus(FollowEntity.Status.REJECTED, PageRequest.of(0, BATCH_SIZE));
		}
	}

	@Transactional
	public void cleanupOrphanLikes() {
		List<LikeEntity> deletableLikes = this.likeRepository.findAll().stream().filter(likeEntity -> {
			if (likeEntity.getLikeableType() == LikeEntity.LikeableType.TWEET) {
				try {
					this.tweetService.getTweetSummary(likeEntity.getLikeableId()).join();
					return false;
				}
				catch (CompletionException ex) {
					if (ex.getCause() instanceof TweetNotFoundException) {
						return true;
					}
					throw ex;
				}
			}
			else {
				return !this.replyRepository.existsById(likeEntity.getLikeableId());
			}
		}).toList();

		deleteInBatches(deletableLikes, this.likeRepository::deleteAll);
	}

	@Transactional
	public void cleanupOrphanReplies() {
		List<ReplyEntity> deletableReplies = this.replyRepository.findAll().stream().filter(replyEntity -> {
			try {
				this.tweetService.getTweetSummary(replyEntity.getTweetId()).join();
				return false;
			}
			catch (CompletionException ex) {
				if (ex.getCause() instanceof TweetNotFoundException) {
					return true;
				}
				throw ex;
			}
		}).toList();

		deleteInBatches(deletableReplies, this.replyRepository::deleteAll);
	}

	@Transactional
	public void cleanupOrphanRetweets() {
		List<RetweetEntity> deletableRetweets = this.retweetRepository.findAll().stream().filter(retweetEntity -> {
			try {
				this.tweetService.getTweetSummary(retweetEntity.getOriginalTweetId()).join();
				return false;
			}
			catch (CompletionException ex) {
				if (ex.getCause() instanceof TweetNotFoundException) {
					return true;
				}
				throw ex;
			}
		}).toList();

		deleteInBatches(deletableRetweets, this.retweetRepository::deleteAll);
	}

	/**
	 * Snapshot-then-delete: the orphan sweeps load the whole table once, filter, then
	 * delete the orphans in fixed-size batches. A paged scan that advances the page index
	 * while deleting only some rows per page skips the boundary rows that shift down into
	 * an already-visited page. This mirrors the reactive stack's single forward scan
	 * (findAll().buffer(BATCH_SIZE)).
	 * @param <T> the entity type being deleted
	 * @param entities the snapshot of entities to delete
	 * @param deleteAll the repository delete operation applied to each batch
	 */
	private static <T> void deleteInBatches(List<T> entities, Consumer<List<T>> deleteAll) {
		for (int i = 0; i < entities.size(); i += BATCH_SIZE) {
			deleteAll.accept(entities.subList(i, Math.min(i + BATCH_SIZE, entities.size())));
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
