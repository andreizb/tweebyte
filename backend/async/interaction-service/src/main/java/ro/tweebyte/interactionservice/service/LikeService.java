/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import ro.tweebyte.interactionservice.cache.CountCache;
import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.mapper.LikeMapper;
import ro.tweebyte.interactionservice.model.LikeDto;
import ro.tweebyte.interactionservice.model.TweetCount;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;

@Service
@RequiredArgsConstructor
public class LikeService {

	private final UserService userService;

	private final TweetService tweetService;

	private final LikeRepository likeRepository;

	private final ReplyRepository replyRepository;

	private final LikeMapper likeMapper;

	private final CountCache countCache;

	private final ExecutorService executorService;

	public CompletableFuture<List<LikeDto>> getUserLikes(UUID userId, int page, int size) {
		return CompletableFuture
			.supplyAsync(() -> this.likeRepository.findByUserIdAndLikeableType(userId, LikeEntity.LikeableType.TWEET,
					size, page * size), this.executorService)
			.thenCompose(likeEntities -> {
				List<CompletableFuture<LikeDto>> futures = likeEntities.stream()
					.map(likeEntity -> this.tweetService.getTweetSummary(likeEntity.getLikeableId())
						.thenApply(tweetSummary -> this.likeMapper.mapToDto(likeEntity, tweetSummary)))
					.toList();

				return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
					.thenApply(v -> futures.stream().map(CompletableFuture::join).toList());
			});
	}

	public CompletableFuture<List<LikeDto>> getTweetLikes(UUID tweetId, int page, int size) {
		return CompletableFuture
			.supplyAsync(() -> this.likeRepository.findByLikeableIdAndLikeableType(tweetId, LikeEntity.LikeableType.TWEET,
					size, page * size), this.executorService)
			.thenCompose(likeEntities -> {
				List<CompletableFuture<LikeDto>> futures = likeEntities.stream()
					.map(likeEntity -> this.userService.getUserSummary(likeEntity.getUserId())
						.thenApply(userSummary -> this.likeMapper.mapToDto(likeEntity, userSummary)))
					.toList();

				return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
					.thenApply(v -> futures.stream().map(CompletableFuture::join).toList());
			});
	}

	// Read-through the per-tweet like_count key (shared with the batched read and the
	// like/unlike deltas); a miss seeds it from one COUNT query. The synchronous
	// CountCache call runs inside supplyAsync, matching how the bare repository read was
	// wrapped before.
	public CompletableFuture<Long> getTweetLikesCount(UUID tweetId) {
		return CompletableFuture.supplyAsync(() -> this.countCache.get(tweetLikeCountKey(tweetId),
				() -> this.likeRepository.countByLikeableIdAndLikeableType(tweetId, LikeEntity.LikeableType.TWEET)),
				this.executorService);
	}

	// Batched like counts for a page of tweets. The cache is consulted first (sharing the
	// per-tweet like_count key with the single-id read and the like/unlike deltas); only
	// the misses fall through to one GROUP BY query, and their results — including the
	// zeros the GROUP BY omits — are written back so the returned map holds every tweet.
	public CompletableFuture<Map<UUID, Long>> getTweetLikesCounts(List<UUID> tweetIds) {
		return CompletableFuture.supplyAsync(() -> this.countCache.getAll(tweetIds, LikeService::tweetLikeCountKey,
				ids -> this.likeRepository.countByLikeableIdInAndLikeableType(ids, LikeEntity.LikeableType.TWEET)
					.stream()
					.collect(Collectors.toMap(TweetCount::tweetId, TweetCount::total))),
				this.executorService);
	}

	static String tweetLikeCountKey(UUID tweetId) {
		return "like_count::" + tweetId;
	}

	public CompletableFuture<LikeDto> likeTweet(UUID userId, UUID tweetId) {
		// getTweetSummary surfaces TweetClient's TweetNotFoundException as 404 for a
		// missing tweet, so the result is never null here.
		return this.tweetService.getTweetSummary(tweetId)
			.thenApply(tweet -> saveOrGetExisting(userId, tweetId, LikeEntity.LikeableType.TWEET))
			.thenApply(this.likeMapper::mapEntityToDto);
	}

	// Idempotent like: a duplicate insert trips the UNIQUE(user_id, likeable_id,
	// likeable_type) constraint, surfaced by Spring as DataIntegrityViolationException.
	// Swallow it and return the row that already exists so repeated likes are no-ops. The
	// count delta runs only on a genuine insert (before the duplicate is swallowed), so a
	// repeated like does not inflate the cached count.
	private LikeEntity saveOrGetExisting(UUID userId, UUID likeableId, LikeEntity.LikeableType likeableType) {
		try {
			LikeEntity saved = this.likeRepository.save(this.likeMapper.mapRequestToEntity(userId, likeableId, likeableType));
			onTweetLikeInserted(likeableType, likeableId);
			return saved;
		}
		catch (DataIntegrityViolationException ex) {
			return this.likeRepository.findByUserIdAndLikeableIdAndLikeableType(userId, likeableId, likeableType)
				.orElseThrow(() -> ex);
		}
	}

	// Only tweet likes carry a cached counter; reply likes have none, so they skip the delta.
	private void onTweetLikeInserted(LikeEntity.LikeableType likeableType, UUID likeableId) {
		if (likeableType == LikeEntity.LikeableType.TWEET) {
			this.countCache.increment(tweetLikeCountKey(likeableId));
		}
	}

	@Transactional
	@Async("ioExecutor")
	public CompletableFuture<Void> unlikeTweet(UUID userId, UUID tweetId) {
		// Resolve the row first so the delta only fires for a like that was actually
		// counted (mirrors FollowService.unfollow and ReplyService.deleteReply). A
		// repeated unlike finds no row, deletes nothing and skips the decrement; the
		// conditional decrement floors at zero and the 60s TTL reseeds from the database,
		// so the cached count cannot drift far.
		if (this.likeRepository.findByUserIdAndLikeableIdAndLikeableType(userId, tweetId, LikeEntity.LikeableType.TWEET)
			.isPresent()) {
			this.likeRepository.deleteByUserIdAndLikeableIdAndLikeableType(userId, tweetId,
					LikeEntity.LikeableType.TWEET);
			this.countCache.decrement(tweetLikeCountKey(tweetId));
		}
		return CompletableFuture.completedFuture(null);
	}

	public CompletableFuture<LikeDto> likeReply(UUID userId, UUID replyId) {
		// `findById(replyId)` — the reply just needs to exist; the liker need
		// not be its author. Matches reactive's `findById(replyId)` in
		// reactive/.../LikeService.java for cross-stack symmetry.
		return CompletableFuture.supplyAsync(() -> {
			if (this.replyRepository.findById(replyId).isEmpty()) {
				throw new IllegalArgumentException("Reply does not exist.");
			}
			return saveOrGetExisting(userId, replyId, LikeEntity.LikeableType.REPLY);
		}, this.executorService).thenApply(this.likeMapper::mapEntityToDto);
	}

	@Transactional
	@Async("ioExecutor")
	public CompletableFuture<Void> unlikeReply(UUID userId, UUID replyId) {
		this.likeRepository.deleteByUserIdAndLikeableIdAndLikeableType(userId, replyId, LikeEntity.LikeableType.REPLY);
		return CompletableFuture.completedFuture(null);
	}

}
