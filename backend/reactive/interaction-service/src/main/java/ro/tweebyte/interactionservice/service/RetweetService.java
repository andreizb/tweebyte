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
import ro.tweebyte.interactionservice.mapper.RetweetMapper;
import ro.tweebyte.interactionservice.model.RetweetCreateRequest;
import ro.tweebyte.interactionservice.model.RetweetDto;
import ro.tweebyte.interactionservice.model.RetweetUpdateRequest;
import ro.tweebyte.interactionservice.model.TweetCount;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

@Service
@RequiredArgsConstructor
public class RetweetService {

	private final TweetService tweetService;

	private final RetweetRepository retweetRepository;

	private final RetweetMapper retweetMapper;

	private final UserService userService;

	private final CountCache countCache;

	public Mono<RetweetDto> createRetweet(RetweetCreateRequest request) {
		return this.tweetService.getTweetSummary(request.getOriginalTweetId())
			.flatMap(tweet -> validateMediaIds(request.getMediaIds())
				.then(this.retweetRepository.save(this.retweetMapper.mapRequestToEntity(request))))
			.flatMap(saved -> this.countCache.increment(retweetCountKey(request.getOriginalTweetId()))
				.thenReturn(saved))
			.map(this.retweetMapper::mapEntityToDto);
	}

	// Reject a retweet that references media ids the user-service does not know.
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

	public Mono<Void> updateRetweet(RetweetUpdateRequest request) {
		// Missing retweet id raises IllegalArgumentException (mapped to 404). A
		// retweet owned by a different user yields 403 Forbidden — same ownership
		// contract deleteRetweet and the tweet PUT/DELETE paths enforce. Symmetric
		// with async.
		return this.retweetRepository.findById(request.getId())
			.switchIfEmpty(Mono.error(new IllegalArgumentException("Retweet does not exist.")))
			.flatMap(retweet -> {
				if (!retweet.getRetweeterId().equals(request.getRetweeterId())) {
					return Mono
						.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Retweet does not belong to user."));
				}
				this.retweetMapper.mapRequestToEntity(request, retweet);
				return this.retweetRepository.save(retweet);
			})
			.then();
	}

	public Mono<Void> deleteRetweet(UUID retweetId, UUID userId) {
		// Missing retweet id raises IllegalArgumentException (mapped to 404 by the
		// GlobalExceptionHandler). A retweet owned by a different user yields 403
		// Forbidden, so one user can't delete another's retweet — same ownership
		// contract the tweet PUT/DELETE paths enforce. Symmetric with async.
		return this.retweetRepository.findById(retweetId)
			.switchIfEmpty(Mono.error(new IllegalArgumentException("Retweet does not exist.")))
			.flatMap(retweet -> retweet.getRetweeterId().equals(userId)
					? this.retweetRepository.deleteById(retweetId)
						.then(Mono.defer(() -> this.countCache.decrement(retweetCountKey(retweet.getOriginalTweetId()))))
					: Mono
						.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Retweet does not belong to user.")));
	}

	public Flux<RetweetDto> getRetweetsByUser(UUID userId, int page, int size) {
		// Every row's retweeterId equals the method parameter, so resolve the
		// retweeter summary once and reuse it for all rows via cache(); getUserSummary
		// is cache-backed, so this also collapses N redundant round-trips into one.
		Mono<UserDto> userSummary = this.userService.getUserSummary(userId).cache();
		return this.retweetRepository.findByRetweeterId(userId, size, page * size)
			.flatMapSequential(retweetEntity -> this.tweetService.getTweetSummary(retweetEntity.getOriginalTweetId())
				.zipWith(userSummary)
				.map(tuple -> this.retweetMapper.mapEntityToDto(retweetEntity, tuple.getT2(), tuple.getT1())));
	}

	public Flux<RetweetDto> getRetweetsOfTweet(UUID tweetId, int page, int size) {
		return this.retweetRepository.findByOriginalTweetId(tweetId, size, page * size)
			.flatMapSequential(retweetEntity -> this.userService.getUserSummary(retweetEntity.getRetweeterId())
				.map(userDto -> this.retweetMapper.mapEntityToDto(retweetEntity, userDto)));
	}

	public Mono<Long> getRetweetCountOfTweet(UUID tweetId) {
		return this.countCache.get(retweetCountKey(tweetId), this.retweetRepository.countByOriginalTweetId(tweetId));
	}

	static String retweetCountKey(UUID tweetId) {
		return "retweet_count::" + tweetId;
	}

	// Batched retweet counts for a page of tweets. The cache is consulted first (sharing
	// the per-tweet retweet_count key with the single-id read and the create/delete
	// deltas); only the misses hit one GROUP BY query, and their results — including the
	// zeros the GROUP BY omits — are written back so the returned map holds every tweet.
	public Mono<Map<UUID, Long>> getRetweetCountsForTweets(List<UUID> tweetIds) {
		return this.countCache.getAll(tweetIds, RetweetService::retweetCountKey,
				ids -> this.retweetRepository.countByOriginalTweetIdIn(ids)
					.collectMap(TweetCount::tweetId, TweetCount::total));
	}

}
