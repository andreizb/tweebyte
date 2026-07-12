/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.controller;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ro.tweebyte.tweetservice.model.HashtagDto;
import ro.tweebyte.tweetservice.model.TweetCreationRequest;
import ro.tweebyte.tweetservice.model.TweetDto;
import ro.tweebyte.tweetservice.model.TweetSummaryDto;
import ro.tweebyte.tweetservice.model.TweetUpdateRequest;
import ro.tweebyte.tweetservice.service.HashtagService;
import ro.tweebyte.tweetservice.service.TweetService;

@RestController
@RequestMapping(path = "/tweets")
@RequiredArgsConstructor
public class TweetController {

	// Upper bound on a single page. Caps a hostile/absurd ?size= so the service
	// never asks the DB for an unbounded result set; the benchmark's largest page
	// (tweets-get size=1000) sits well under this.
	private static final int MAX_PAGE_SIZE = 10_000;

	private final TweetService tweetService;

	private final HashtagService hashtagService;

	private static int normalizePage(int page) {
		return Math.max(page, 0);
	}

	private static int normalizeSize(int size) {
		return Math.clamp(size, 1, MAX_PAGE_SIZE);
	}

	@GetMapping("/{userId}/feed")
	public CompletableFuture<List<TweetDto>> getFeed(@PathVariable("userId") UUID userId,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "10") int size) {
		return this.tweetService.getUserFeed(userId, normalizePage(page), normalizeSize(size));
	}

	@GetMapping("/{tweetId}")
	public CompletableFuture<TweetDto> getTweet(@PathVariable("tweetId") UUID tweetId) {
		return this.tweetService.getTweet(tweetId);
	}

	@GetMapping("/search/{searchTerm}")
	public CompletableFuture<List<TweetDto>> searchTweets(@PathVariable("searchTerm") String searchTerm,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "10") int size) {
		return this.tweetService.searchTweets(searchTerm, normalizePage(page), normalizeSize(size));
	}

	@GetMapping("/search/hashtag/{searchTerm}")
	public CompletableFuture<List<TweetDto>> searchTweetsByHashtag(@PathVariable("searchTerm") String searchTerm,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "10") int size) {
		return this.tweetService.searchTweetsByHashtag(searchTerm, normalizePage(page), normalizeSize(size));
	}

	@GetMapping("/hashtag/popular")
	public CompletableFuture<List<HashtagDto>> computePopularHashtags() {
		return this.hashtagService.computePopularHashtags();
	}

	@GetMapping("/media/referenced")
	public CompletableFuture<List<UUID>> getReferencedMediaIds() {
		return this.tweetService.getReferencedMediaIds();
	}

	// enrich defaults to true so tweets-get (and any caller that omits the param) gets the
	// fully-enriched page unchanged. The user-profile read opts out with enrich=false to fetch
	// the tweets un-enriched, then resolves their interactions in its own combined call —
	// trimming one cross-service round-trip off the profile path.
	@GetMapping("/user/{userId}")
	public CompletableFuture<List<TweetDto>> getUserTweets(@PathVariable("userId") UUID userId,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "10") int size,
			@RequestParam(value = "enrich", defaultValue = "true") boolean enrich) {
		return this.tweetService.getUserTweets(userId, normalizePage(page), normalizeSize(size), enrich);
	}

	@GetMapping("/{tweetId}/summary")
	public CompletableFuture<TweetDto> getTweetSummary(@PathVariable("tweetId") UUID tweetId) {
		return this.tweetService.getTweetSummary(tweetId);
	}

	@GetMapping("/user/{userId}/summary")
	public CompletableFuture<List<TweetSummaryDto>> getUserTweetsSummary(@PathVariable("userId") UUID userId) {
		return this.tweetService.getUserTweetsSummary(userId);
	}

	@PostMapping("/{userId}")
	public CompletableFuture<TweetDto> createTweet(@PathVariable("userId") UUID userId,
			@Valid @RequestBody TweetCreationRequest request) {
		return this.tweetService.createTweet(request.setUserId(userId));
	}

	@PutMapping("/{userId}/{tweetId}")
	public CompletableFuture<Void> updateTweet(@PathVariable("userId") UUID userId,
			@PathVariable("tweetId") UUID tweetId, @Valid @RequestBody TweetUpdateRequest request) {
		return this.tweetService.updateTweet(request.setId(tweetId).setUserId(userId));
	}

	@DeleteMapping("/{userId}/{tweetId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public CompletableFuture<Void> deleteTweet(@PathVariable("userId") UUID userId,
			@PathVariable("tweetId") UUID tweetId) {
		return this.tweetService.deleteTweet(userId, tweetId);
	}

}
