/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import ro.tweebyte.interactionservice.model.RetweetCreateRequest;
import ro.tweebyte.interactionservice.model.RetweetDto;
import ro.tweebyte.interactionservice.model.RetweetUpdateRequest;
import ro.tweebyte.interactionservice.service.RetweetService;

@RestController
@RequestMapping(path = "/retweets")
@RequiredArgsConstructor
public class RetweetController {

	// Upper bound on a single page so a hostile/absurd ?size= can never ask the DB
	// for an unbounded result set; mirrors the tweet-service search controller's clamp.
	private static final int MAX_PAGE_SIZE = 10_000;

	private final RetweetService retweetService;

	private static int normalizePage(int page) {
		return Math.max(page, 0);
	}

	private static int normalizeSize(int size) {
		return Math.clamp(size, 1, MAX_PAGE_SIZE);
	}

	@PostMapping("/{userId}")
	public Mono<RetweetDto> createRetweet(@PathVariable("userId") UUID userId,
			@Valid @RequestBody RetweetCreateRequest request) {
		return this.retweetService.createRetweet(request.setRetweeterId(userId));
	}

	@PutMapping("/{userId}/{retweetId}")
	public Mono<Void> updateRetweet(@PathVariable("userId") UUID userId, @PathVariable UUID retweetId,
			@RequestBody RetweetUpdateRequest request) {
		return this.retweetService.updateRetweet(request.setId(retweetId).setRetweeterId(userId));
	}

	@DeleteMapping("/{userId}/{retweetId}")
	public Mono<Void> deleteRetweet(@PathVariable("userId") UUID userId, @PathVariable UUID retweetId) {
		return this.retweetService.deleteRetweet(retweetId, userId);
	}

	@GetMapping("/user/{userId}")
	public Flux<RetweetDto> getAllRetweetsByUser(@PathVariable UUID userId,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "10") int size) {
		return this.retweetService.getRetweetsByUser(userId, normalizePage(page), normalizeSize(size));
	}

	@GetMapping("/tweet/{tweetId}")
	public Flux<RetweetDto> getAllRetweetsOfTweet(@PathVariable UUID tweetId,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "10") int size) {
		return this.retweetService.getRetweetsOfTweet(tweetId, normalizePage(page), normalizeSize(size));
	}

	@GetMapping("/tweet/{tweetId}/count")
	public Mono<Long> getRetweetCountOfTweet(@PathVariable UUID tweetId) {
		return this.retweetService.getRetweetCountOfTweet(tweetId);
	}

	@PostMapping("/tweet/counts")
	public Mono<Map<UUID, Long>> getRetweetCountsForTweets(@RequestBody List<UUID> tweetIds) {
		return this.retweetService.getRetweetCountsForTweets(tweetIds);
	}

}
