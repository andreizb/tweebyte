/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import ro.tweebyte.interactionservice.cache.CountCache;
import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.exception.InteractionException;
import ro.tweebyte.interactionservice.mapper.RetweetMapper;
import ro.tweebyte.interactionservice.model.RetweetCreateRequest;
import ro.tweebyte.interactionservice.model.RetweetDto;
import ro.tweebyte.interactionservice.model.RetweetUpdateRequest;
import ro.tweebyte.interactionservice.model.TweetCount;
import ro.tweebyte.interactionservice.model.TweetDto;
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

	private final ExecutorService executorService;

	public CompletableFuture<RetweetDto> createRetweet(RetweetCreateRequest request) {
		// getTweetSummary surfaces a 404 for a missing tweet, so it is never null here.
		return this.tweetService.getTweetSummary(request.getOriginalTweetId())
			.thenCompose(tweet -> validateMediaIds(request.getMediaIds()))
			.thenApply(ignored -> {
				RetweetEntity retweet = this.retweetRepository.save(this.retweetMapper.mapRequestToEntity(request));
				// Delta after the row is confirmed persisted, mirroring reactive's
				// flatMap(saved -> increment...). The conditional script only touches a
				// key already cached; a cold key reseeds on the next read.
				this.countCache.increment(retweetCountKey(request.getOriginalTweetId()));
				return retweet;
			})
			.thenApply(this.retweetMapper::mapEntityToDto);
	}

	// Reject a retweet that references media ids the user-service does not know.
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

	public CompletableFuture<Void> updateRetweet(RetweetUpdateRequest request) {
		// Missing retweet id raises IllegalArgumentException (mapped to 404). A
		// retweet owned by a different user yields 403 Forbidden — same ownership
		// contract deleteRetweet and the tweet PUT/DELETE paths enforce.
		return CompletableFuture.runAsync(() -> {
			RetweetEntity retweet = this.retweetRepository.findById(request.getId())
				.orElseThrow(() -> new IllegalArgumentException("Retweet does not exist."));
			if (!retweet.getRetweeterId().equals(request.getRetweeterId())) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Retweet does not belong to user.");
			}
			this.retweetMapper.mapRequestToEntity(request, retweet);
			this.retweetRepository.save(retweet);
		}, this.executorService);
	}

	public CompletableFuture<Void> deleteRetweet(UUID retweetId, UUID userId) {
		// Missing retweet id raises IllegalArgumentException (mapped to 404 by the
		// GlobalExceptionHandler). A retweet owned by a different user yields 403
		// Forbidden, so one user can't delete another's retweet — same ownership
		// contract the tweet PUT/DELETE paths enforce. Symmetric with reactive.
		return CompletableFuture.runAsync(() -> {
			RetweetEntity retweet = this.retweetRepository.findById(retweetId)
				.orElseThrow(() -> new IllegalArgumentException("Retweet does not exist."));
			if (!retweet.getRetweeterId().equals(userId)) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Retweet does not belong to user.");
			}
			this.retweetRepository.deleteById(retweetId);
			this.countCache.decrement(retweetCountKey(retweet.getOriginalTweetId()));
		}, this.executorService);
	}

	public CompletableFuture<List<RetweetDto>> getRetweetsByUser(UUID userId, int page, int size) {
		// Every row's retweeterId equals the method parameter, so resolve the
		// retweeter summary once and reuse it for all rows; getUserSummary is
		// cache-backed, so this also collapses N redundant round-trips into one.
		CompletableFuture<UserDto> userFuture = this.userService.getUserSummary(userId);
		return CompletableFuture.supplyAsync(() -> this.retweetRepository.findByRetweeterId(userId, size, page * size),
				this.executorService)
			.thenCompose(retweetEntities -> {
				List<CompletableFuture<RetweetDto>> retweetDtoFutures = retweetEntities.stream()
					.map(retweetEntity -> {
						CompletableFuture<TweetDto> originalTweetFuture = this.tweetService
							.getTweetSummary(retweetEntity.getOriginalTweetId());

						return originalTweetFuture.thenCombine(userFuture, (tweetDto, userDto) -> {
							try {
								return this.retweetMapper.mapEntityToDto(retweetEntity, userDto, tweetDto);
							}
							catch (Exception ex) {
								throw new InteractionException(ex);
							}
						});
					})
					.toList();

				return CompletableFuture.allOf(retweetDtoFutures.toArray(new CompletableFuture[0]))
					.thenApply(v -> retweetDtoFutures.stream().map(CompletableFuture::join).toList());
			});
	}

	public CompletableFuture<List<RetweetDto>> getRetweetsOfTweet(UUID tweetId, int page, int size) {
		return CompletableFuture.supplyAsync(() -> this.retweetRepository.findByOriginalTweetId(tweetId, size, page * size),
				this.executorService)
			.thenCompose(retweetEntities -> {
				List<CompletableFuture<RetweetDto>> futures = retweetEntities.stream()
					.map(retweetEntity -> this.userService.getUserSummary(retweetEntity.getRetweeterId())
						.thenApply(userDto -> this.retweetMapper.mapEntityToDto(retweetEntity, userDto)))
					.toList();

				return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
					.thenApply(v -> futures.stream().map(CompletableFuture::join).toList());
			});
	}

	// Read-through the per-tweet retweet_count key (shared with the batched read and the
	// create/delete deltas); a miss seeds it from one COUNT query.
	public CompletableFuture<Long> getRetweetCountOfTweet(UUID tweetId) {
		return CompletableFuture.supplyAsync(
				() -> this.countCache.get(retweetCountKey(tweetId), () -> this.retweetRepository.countByOriginalTweetId(tweetId)),
				this.executorService);
	}

	static String retweetCountKey(UUID tweetId) {
		return "retweet_count::" + tweetId;
	}

	// Batched retweet counts for a page of tweets, served from the same per-tweet
	// retweet_count key as the single-id read and the create/delete deltas. Only the
	// misses hit one GROUP BY query; their results, including the zeros the GROUP BY
	// omits, are written back so the returned map holds every tweet.
	public CompletableFuture<Map<UUID, Long>> getRetweetCountsForTweets(List<UUID> tweetIds) {
		return CompletableFuture.supplyAsync(() -> this.countCache.getAll(tweetIds, RetweetService::retweetCountKey,
				ids -> this.retweetRepository.countByOriginalTweetIdIn(ids)
					.stream()
					.collect(Collectors.toMap(TweetCount::tweetId, TweetCount::total))),
				this.executorService);
	}

}
