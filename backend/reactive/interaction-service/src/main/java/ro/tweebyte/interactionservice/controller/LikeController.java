/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import ro.tweebyte.interactionservice.model.LikeDto;
import ro.tweebyte.interactionservice.service.LikeService;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/likes")
public class LikeController {

	// Upper bound on a single page so a hostile/absurd ?size= can never ask the DB
	// for an unbounded result set; mirrors the tweet-service search controller's clamp.
	private static final int MAX_PAGE_SIZE = 10_000;

	private final LikeService likeService;

	private static int normalizePage(int page) {
		return Math.max(page, 0);
	}

	private static int normalizeSize(int size) {
		return Math.clamp(size, 1, MAX_PAGE_SIZE);
	}

	@GetMapping("/user/{userId}")
	public Flux<LikeDto> getUserLikes(@PathVariable("userId") UUID userId,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "10") int size) {
		return this.likeService.getUserLikes(userId, normalizePage(page), normalizeSize(size));
	}

	@GetMapping("/tweet/{tweetId}")
	public Flux<LikeDto> getTweetLikes(@PathVariable("tweetId") UUID tweetId,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "10") int size) {
		return this.likeService.getTweetLikes(tweetId, normalizePage(page), normalizeSize(size));
	}

	@GetMapping("/{tweetId}/count")
	public Mono<Long> getTweetLikesCount(@PathVariable("tweetId") UUID tweetId) {
		return this.likeService.getTweetLikesCount(tweetId);
	}

	@PostMapping("/counts")
	public Mono<Map<UUID, Long>> getTweetLikesCounts(@RequestBody List<UUID> tweetIds) {
		return this.likeService.getTweetLikesCounts(tweetIds);
	}

	@PostMapping("/{userId}/tweets/{tweetId}")
	public Mono<LikeDto> likeTweet(@PathVariable("userId") UUID userId, @PathVariable("tweetId") UUID tweetId) {
		return this.likeService.likeTweet(userId, tweetId);
	}

	@DeleteMapping("/{userId}/tweets/{tweetId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public Mono<Void> unlikeTweet(@PathVariable("userId") UUID userId, @PathVariable("tweetId") UUID tweetId) {
		return this.likeService.unlikeTweet(userId, tweetId);
	}

	@PostMapping("/{userId}/replies/{replyId}")
	public Mono<LikeDto> likeReply(@PathVariable("userId") UUID userId, @PathVariable("replyId") UUID replyId) {
		return this.likeService.likeReply(userId, replyId);
	}

	@DeleteMapping("/{userId}/replies/{replyId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public Mono<Void> unlikeReply(@PathVariable("userId") UUID userId, @PathVariable("replyId") UUID replyId) {
		return this.likeService.unlikeReply(userId, replyId);
	}

}
