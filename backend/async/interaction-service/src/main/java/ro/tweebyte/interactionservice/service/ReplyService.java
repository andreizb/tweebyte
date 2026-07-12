/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import ro.tweebyte.interactionservice.cache.CountCache;
import ro.tweebyte.interactionservice.cache.TopReplyCache;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.mapper.ReplyMapper;
import ro.tweebyte.interactionservice.model.ReplyCreateRequest;
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.ReplyUpdateRequest;
import ro.tweebyte.interactionservice.model.TopReply;
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

	private final ExecutorService executorService;

	public CompletableFuture<ReplyDto> createReply(ReplyCreateRequest request) {
		// getTweetSummary surfaces a 404 for a missing tweet, so it is never null here.
		return this.tweetService.getTweetSummary(request.getTweetId())
			.thenCompose(tweet -> validateMediaIds(request.getMediaIds()))
			.thenApply(ignored -> {
				ReplyEntity reply = this.replyRepository.save(this.replyMapper.mapRequestToEntity(request));
				// Delta after the row is confirmed persisted, mirroring reactive's
				// flatMap(saved -> increment...). The conditional script only touches a
				// key already cached; a cold key reseeds on the next read.
				this.countCache.increment(replyCountKey(request.getTweetId()));
				return reply;
			})
			.thenApply(this.replyMapper::mapEntityToCreationDto);
	}

	// Reject a reply that references media ids the user-service does not know.
	// Empty/absent media_ids skip the round-trip. A missing asset is a client
	// error (400) carried by ResponseStatusException, which the
	// GlobalExceptionHandler maps to its status.
	private CompletableFuture<Void> validateMediaIds(UUID[] mediaIds) {
		if (mediaIds == null || mediaIds.length == 0) {
			return CompletableFuture.completedFuture(null);
		}
		CompletableFuture<?>[] checks = Arrays.stream(mediaIds)
			.map(id -> this.userService.mediaExists(id).thenAccept(exists -> {
				if (!Boolean.TRUE.equals(exists)) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Media not found for id: " + id);
				}
			}))
			.toArray(CompletableFuture[]::new);
		return CompletableFuture.allOf(checks);
	}

	public CompletableFuture<Void> updateReply(ReplyUpdateRequest request) {
		return CompletableFuture.runAsync(() -> {
			ReplyEntity reply = this.replyRepository.findById(request.getId())
				.orElseThrow(() -> new IllegalArgumentException(UNAUTHORIZED_OR_NOT_FOUND));
			// A missing reply raises IllegalArgumentException (404). An existing
			// reply owned by a different user raises ResponseStatusException(FORBIDDEN)
			// — mirroring RetweetService's analogous ownership check and the reactive
			// stack's updateReply.
			if (!reply.getUserId().equals(request.getUserId())) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, UNAUTHORIZED_OR_NOT_FOUND);
			}
			this.replyMapper.mapRequestToEntity(request, reply);
			this.replyRepository.save(reply);
		}, this.executorService);
	}

	public CompletableFuture<Void> deleteReply(UUID userId, UUID replyId) {
		return CompletableFuture.runAsync(() -> {
			// A missing reply raises IllegalArgumentException (404). An existing
			// reply owned by a different user raises ResponseStatusException(FORBIDDEN)
			// — mirroring RetweetService's analogous ownership check and the reactive
			// stack's deleteReply.
			ReplyEntity entity = this.replyRepository.findById(replyId)
				.orElseThrow(() -> new IllegalArgumentException(UNAUTHORIZED_OR_NOT_FOUND));
			if (!entity.getUserId().equals(userId)) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, UNAUTHORIZED_OR_NOT_FOUND);
			}
			this.replyRepository.deleteById(replyId);
			this.countCache.decrement(replyCountKey(entity.getTweetId()));
		}, this.executorService);
	}

	public CompletableFuture<List<ReplyDto>> getRepliesForTweet(UUID tweetId, int page, int size) {
		return CompletableFuture
			.supplyAsync(() -> this.replyRepository.findByTweetIdOrderByCreatedAtDescIdDesc(tweetId, size, page * size),
					this.executorService)
			.thenCompose(replyEntities -> {
				List<CompletableFuture<ReplyDto>> replyFutures = replyEntities.stream()
					.map(replyEntity -> this.userService.getUserSummary(replyEntity.getUserId())
						.thenApply(userSummary -> this.replyMapper.mapEntityToDto(replyEntity, userSummary.getUserName())))
					.toList();

				return CompletableFuture.allOf(replyFutures.toArray(new CompletableFuture[0]))
					.thenApply(v -> replyFutures.stream().map(CompletableFuture::join).toList());
			});
	}

	// Read-through the per-tweet reply_count key (shared with the batched read and the
	// create/delete deltas); a miss seeds it from one COUNT query.
	public CompletableFuture<Long> getReplyCountForTweet(UUID tweetId) {
		return CompletableFuture
			.supplyAsync(() -> this.countCache.get(replyCountKey(tweetId), () -> this.replyRepository.countByTweetId(tweetId)),
					this.executorService);
	}

	static String replyCountKey(UUID tweetId) {
		return "reply_count::" + tweetId;
	}

	// Read-through the per-tweet top_reply key: a hit serves the cached top reply (or the
	// negative-cached "no top reply" as an empty ReplyDto), a miss loads from the database
	// and caches either the resolved reply or the absent marker.
	public CompletableFuture<ReplyDto> getTopReplyForTweet(UUID tweetId) {
		return CompletableFuture
			.supplyAsync(() -> this.topReplyCache.get(topReplyKey(tweetId), () -> loadTopReply(tweetId)),
					this.executorService);
	}

	// Resolves the top reply for a tweet, returning an empty ReplyDto (null id) when the
	// tweet has no reply — the TopReplyCache reads the null id as "absent" and caches it.
	private ReplyDto loadTopReply(UUID tweetId) {
		var page = this.replyRepository.findTopReplyByLikesForTweetId(tweetId, PageRequest.of(0, 1));
		if (page.getContent().isEmpty()) {
			return new ReplyDto();
		}
		ReplyDto replyDto = page.getContent().get(0);
		replyDto.setUserName(this.userService.getUserSummary(replyDto.getUserId()).join().getUserName());
		return replyDto;
	}

	static String topReplyKey(UUID tweetId) {
		return "top_reply::" + tweetId;
	}

	// Batched reply counts for a page of tweets. The cache is consulted first (sharing the
	// per-tweet reply_count key with the single-id read and the create/delete deltas); only
	// the misses hit one GROUP BY query, and their results — including the zeros the GROUP
	// BY omits — are written back so the returned map holds every tweet.
	public CompletableFuture<Map<UUID, Long>> getReplyCountsForTweets(List<UUID> tweetIds) {
		return CompletableFuture.supplyAsync(() -> this.countCache.getAll(tweetIds, ReplyService::replyCountKey,
				ids -> this.replyRepository.countByTweetIdIn(ids)
					.stream()
					.collect(Collectors.toMap(TweetCount::tweetId, TweetCount::total))),
				this.executorService);
	}

	// Batched top reply per tweet, read through the per-tweet top_reply cache. Misses fall
	// through to loadTopRepliesForTweets; a tweet the loader omits (no top reply) is
	// negative-cached with the absent marker, and callers default it to an empty ReplyDto.
	public CompletableFuture<Map<UUID, ReplyDto>> getTopRepliesForTweets(List<UUID> tweetIds) {
		return CompletableFuture
			.supplyAsync(() -> this.topReplyCache.getAll(tweetIds, ReplyService::topReplyKey, this::loadTopRepliesForTweets),
					this.executorService);
	}

	// One query returns every reply (with its like count) for the page's tweets, ordered so
	// the top reply per tweet is the first row in its group; putIfAbsent keeps that first
	// row. Username resolution is then K cheap (Redis-cached) getUserSummary lookups, one
	// per tweet that has a reply.
	private Map<UUID, ReplyDto> loadTopRepliesForTweets(List<UUID> tweetIds) {
		Map<UUID, TopReply> topByTweet = new LinkedHashMap<>();
		for (TopReply row : this.replyRepository.findRepliesByLikesForTweetIds(tweetIds)) {
			topByTweet.putIfAbsent(row.tweetId(), row);
		}
		List<CompletableFuture<Map.Entry<UUID, ReplyDto>>> futures = topByTweet.values()
			.stream()
			.map(top -> this.userService.getUserSummary(top.userId())
				.thenApply(userDto -> Map.entry(top.tweetId(),
						new ReplyDto(top.id(), top.userId(), top.content(), top.createdAt(), top.likeCount())
							.setUserName(userDto.getUserName()))))
			.toList();
		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
			.thenApply(v -> futures.stream()
				.map(CompletableFuture::join)
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
			.join();
	}

}
