/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import ro.tweebyte.interactionservice.cache.CountCache;
import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.mapper.LikeMapper;
import ro.tweebyte.interactionservice.model.LikeDto;
import ro.tweebyte.interactionservice.model.LikeableType;
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

	private final TransactionalOperator txOperator;

	public Flux<LikeDto> getUserLikes(UUID userId, int page, int size) {
		return this.likeRepository.findByUserIdAndLikeableType(userId, LikeableType.TWEET.name(), size, page * size)
			.flatMapSequential(likeEntity -> this.tweetService.getTweetSummary(likeEntity.getLikeableId())
				.map(tweetSummary -> this.likeMapper.mapToDto(likeEntity, tweetSummary)));
	}

	public Flux<LikeDto> getTweetLikes(UUID tweetId, int page, int size) {
		return this.likeRepository.findByLikeableIdAndLikeableType(tweetId, LikeableType.TWEET.name(), size, page * size)
			.flatMapSequential(likeEntity -> this.userService.getUserSummary(likeEntity.getUserId())
				.map(userSummary -> this.likeMapper.mapToDto(likeEntity, userSummary)));
	}

	public Mono<Long> getTweetLikesCount(UUID tweetId) {
		return this.countCache.get(tweetLikeCountKey(tweetId),
				this.likeRepository.countByLikeableIdAndLikeableType(tweetId, LikeableType.TWEET.name()));
	}

	// Batched like counts for a page of tweets. The cache is consulted first (sharing the
	// per-tweet like_count key with the single-id read and the like/unlike deltas); only
	// the misses fall through to one GROUP BY query, and their results — including the
	// zeros the GROUP BY omits — are written back so the returned map holds every tweet.
	public Mono<Map<UUID, Long>> getTweetLikesCounts(List<UUID> tweetIds) {
		return this.countCache.getAll(tweetIds, LikeService::tweetLikeCountKey,
				ids -> this.likeRepository.countByLikeableIdInAndLikeableType(ids, LikeableType.TWEET.name())
					.collectMap(TweetCount::tweetId, TweetCount::total));
	}

	static String tweetLikeCountKey(UUID tweetId) {
		return "like_count::" + tweetId;
	}

	public Mono<LikeDto> likeTweet(UUID userId, UUID tweetId) {
		// The "tweet not found" case is handled upstream:
		// tweetService.getTweetSummary surfaces TweetClient's
		// TweetNotFoundException via the WebClient onStatus(NOT_FOUND, ...) chain → 404.
		// Reactor's flatMap contract forbids invoking the lambda with null, so no
		// null-guard is needed (or coverable) inside flatMap.
		return this.tweetService.getTweetSummary(tweetId)
			.flatMap(tweet -> saveOrGetExisting(userId, tweetId, LikeableType.TWEET.name())
				.map(this.likeMapper::mapEntityToDto));
	}

	// Idempotent like: a duplicate insert trips the UNIQUE(user_id, likeable_id,
	// likeable_type) constraint, surfaced by R2DBC as DataIntegrityViolationException.
	// Swallow it and return the row that already exists so repeated likes are no-ops. The
	// count delta runs only on a genuine insert (before the duplicate is swallowed), so a
	// repeated like does not inflate the cached count. If the concurrent delete window
	// empties the findBy... Mono (race between constraint violation and row deletion), fall
	// back to rethrow the original DIVE — matching async's rethrow on the same path.
	private Mono<LikeEntity> saveOrGetExisting(UUID userId, UUID likeableId, String likeableType) {
		return this.likeRepository.save(this.likeMapper.mapRequestToEntity(userId, likeableId, likeableType))
			.flatMap(saved -> onTweetLikeInserted(likeableType, likeableId).thenReturn(saved))
			.onErrorResume(DataIntegrityViolationException.class,
					e -> this.likeRepository
						.findByUserIdAndLikeableIdAndLikeableType(userId, likeableId, likeableType)
						.switchIfEmpty(Mono.error(e)));
	}

	// Only tweet likes carry a cached counter; reply likes have none, so they skip the delta.
	private Mono<Void> onTweetLikeInserted(String likeableType, UUID likeableId) {
		if (LikeableType.TWEET.name().equals(likeableType)) {
			return this.countCache.increment(tweetLikeCountKey(likeableId));
		}
		return Mono.empty();
	}

	public Mono<Void> unlikeTweet(UUID userId, UUID tweetId) {
		// Resolve the row first so the delta only fires for a like that was actually
		// counted (mirrors FollowService.unfollow and ReplyService.deleteReply). A
		// repeated unlike finds no row, deletes nothing and skips the decrement; the
		// conditional decrement floors at zero and the 60s TTL reseeds from the database,
		// so the cached count cannot drift far. The find + delete run inside a reactive
		// transaction so a crash between the two operations cannot leave a phantom row
		// (mirroring async's @Transactional on the same path). The cache decrement fires
		// only after the delete commits successfully, inside the flatMap so it is skipped
		// when no row is found.
		return this.likeRepository
			.findByUserIdAndLikeableIdAndLikeableType(userId, tweetId, LikeableType.TWEET.name())
			.flatMap(existing -> this.likeRepository
				.deleteByUserIdAndLikeableIdAndLikeableType(userId, tweetId, LikeableType.TWEET.name())
				.then(Mono.defer(() -> this.countCache.decrement(tweetLikeCountKey(tweetId)))))
			.as(this.txOperator::transactional)
			.then();
	}

	public Mono<LikeDto> likeReply(UUID userId, UUID replyId) {
		return this.replyRepository.findById(replyId)
			.switchIfEmpty(Mono.error(new IllegalArgumentException("Reply does not exist.")))
			.flatMap(reply -> saveOrGetExisting(userId, replyId, LikeableType.REPLY.name()))
			.map(this.likeMapper::mapEntityToDto);
	}

	public Mono<Void> unlikeReply(UUID userId, UUID replyId) {
		return this.likeRepository.deleteByUserIdAndLikeableIdAndLikeableType(userId, replyId,
				LikeableType.REPLY.name());
	}

}
