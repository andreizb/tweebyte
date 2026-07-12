/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import ro.tweebyte.interactionservice.cache.CountCache;
import ro.tweebyte.interactionservice.cache.TopReplyCache;
import ro.tweebyte.interactionservice.mapper.ReplyMapper;
import ro.tweebyte.interactionservice.model.ReplyCreateRequest;
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.ReplyUpdateRequest;
import ro.tweebyte.interactionservice.model.TweetCount;
import ro.tweebyte.interactionservice.repository.ReplyRepository;

@Service
@RequiredArgsConstructor
public class ReplyService {

	private static final String UNAUTHORIZED_OR_NOT_FOUND = "Unauthorized or reply not found";

	private final TweetService tweetService;

	private final UserService userService;

	private final ReplyRepository replyRepository;

	private final ReplyMapper replyMapper;

	private final CountCache countCache;

	private final TopReplyCache topReplyCache;

	public Mono<ReplyDto> createReply(ReplyCreateRequest request) {
		return this.tweetService.getTweetSummary(request.getTweetId())
			.flatMap(tweet -> validateMediaIds(request.getMediaIds())
				.then(this.replyRepository.save(this.replyMapper.mapRequestToEntity(request))))
			.flatMap(saved -> this.countCache.increment(replyCountKey(request.getTweetId())).thenReturn(saved))
			.map(this.replyMapper::mapEntityToCreationDto);
	}

	// Reject a reply that references media ids the user-service does not know.
	// Empty/absent media_ids skip the round-trip. A missing asset is a client
	// error (400) carried by ResponseStatusException, which the
	// GlobalExceptionHandler maps to its status.
	private Mono<Void> validateMediaIds(UUID[] mediaIds) {
		if (mediaIds == null || mediaIds.length == 0) {
			return Mono.empty();
		}
		return Flux.fromArray(mediaIds)
			.flatMap(id -> this.userService.mediaExists(id)
				.flatMap(exists -> Boolean.TRUE.equals(exists) ? Mono.empty()
						: Mono.error(
								new ResponseStatusException(HttpStatus.BAD_REQUEST, "Media not found for id: " + id))))
			.then();
	}

	public Mono<Void> updateReply(ReplyUpdateRequest request) {
		// A missing reply id raises IllegalArgumentException (404). An existing
		// reply owned by a different user raises ResponseStatusException(FORBIDDEN)
		// — mirroring RetweetService's analogous ownership check and matching the
		// async stack's updateReply below.
		return this.replyRepository.findById(request.getId())
			.switchIfEmpty(Mono.error(new IllegalArgumentException(UNAUTHORIZED_OR_NOT_FOUND)))
			.flatMap(reply -> {
				if (reply.getUserId().equals(request.getUserId())) {
					this.replyMapper.mapRequestToEntity(request, reply);
					return this.replyRepository.save(reply).then();
				}
				return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, UNAUTHORIZED_OR_NOT_FOUND));
			});
	}

	public Mono<Void> deleteReply(UUID userId, UUID replyId) {
		// A missing reply id raises IllegalArgumentException (404). An existing
		// reply owned by a different user raises ResponseStatusException(FORBIDDEN)
		// — mirroring RetweetService's analogous ownership check and matching the
		// async stack's deleteReply below.
		return this.replyRepository.findById(replyId)
			.switchIfEmpty(Mono.error(new IllegalArgumentException(UNAUTHORIZED_OR_NOT_FOUND)))
			.flatMap(reply -> {
				if (reply.getUserId().equals(userId)) {
					return this.replyRepository.deleteById(replyId)
						.then(Mono.defer(() -> this.countCache.decrement(replyCountKey(reply.getTweetId()))));
				}
				return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, UNAUTHORIZED_OR_NOT_FOUND));
			});
	}

	public Flux<ReplyDto> getRepliesForTweet(UUID tweetId, int page, int size) {
		return this.replyRepository.findByTweetIdOrderByCreatedAtDescIdDesc(tweetId, size, page * size)
			.flatMapSequential(replyEntity -> this.userService.getUserSummary(replyEntity.getUserId())
				.map(userSummary -> this.replyMapper.mapEntityToDto(replyEntity, userSummary.getUserName())));
	}

	public Mono<Long> getReplyCountForTweet(UUID tweetId) {
		return this.countCache.get(replyCountKey(tweetId), this.replyRepository.countByTweetId(tweetId));
	}

	static String replyCountKey(UUID tweetId) {
		return "reply_count::" + tweetId;
	}

	public Mono<ReplyDto> getTopReplyForTweet(UUID tweetId) {
		return this.topReplyCache.get(topReplyKey(tweetId), loadTopReply(tweetId));
	}

	private Mono<ReplyDto> loadTopReply(UUID tweetId) {
		return this.replyRepository.findTopReplyByLikesForTweetId(tweetId)
			.next()
			.flatMap(top -> this.userService.getUserSummary(top.userId())
				.map(userDto -> new ReplyDto(top.id(), top.userId(), top.content(), top.createdAt(), top.likeCount())
					.setUserName(userDto.getUserName())));
	}

	static String topReplyKey(UUID tweetId) {
		return "top_reply::" + tweetId;
	}

	// Batched reply counts for a page of tweets. The cache is consulted first (sharing the
	// per-tweet reply_count key with the single-id read and the create/delete deltas);
	// only the misses hit one GROUP BY query, and their results — including the zeros the
	// GROUP BY omits — are written back so the returned map holds every tweet.
	public Mono<Map<UUID, Long>> getReplyCountsForTweets(List<UUID> tweetIds) {
		return this.countCache.getAll(tweetIds, ReplyService::replyCountKey,
				ids -> this.replyRepository.countByTweetIdIn(ids).collectMap(TweetCount::tweetId, TweetCount::total));
	}

	// Batched top reply per tweet: one window-function query ranks each tweet's
	// replies and keeps rank 1, then the author name is resolved from the
	// Redis-cached user summary (cheap, one per top reply). Tweets without a top
	// reply are absent from the map; callers default to an empty ReplyDto.
	public Mono<Map<UUID, ReplyDto>> getTopRepliesForTweets(List<UUID> tweetIds) {
		if (tweetIds == null || tweetIds.isEmpty()) {
			return Mono.just(Map.of());
		}
		return this.topReplyCache.getAll(tweetIds, ReplyService::topReplyKey, this::loadTopRepliesForTweets);
	}

	private Mono<Map<UUID, ReplyDto>> loadTopRepliesForTweets(List<UUID> tweetIds) {
		return this.replyRepository.findTopRepliesByLikesForTweetIds(tweetIds)
			.flatMapSequential(top -> this.userService.getUserSummary(top.userId())
				.map(userDto -> Map.entry(top.tweetId(),
						new ReplyDto(top.id(), top.userId(), top.content(), top.createdAt(), top.likeCount())
							.setUserName(userDto.getUserName()))))
			.collectMap(Map.Entry::getKey, Map.Entry::getValue);
	}

}
