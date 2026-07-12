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

import ro.tweebyte.interactionservice.model.ReplyCreateRequest;
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.ReplyUpdateRequest;
import ro.tweebyte.interactionservice.service.ReplyService;

@RestController
@RequestMapping(path = "/replies")
@RequiredArgsConstructor
public class ReplyController {

	// Upper bound on a single page so a hostile/absurd ?size= can never ask the DB
	// for an unbounded result set; mirrors the tweet-service search controller's clamp.
	private static final int MAX_PAGE_SIZE = 10_000;

	private final ReplyService replyService;

	private static int normalizePage(int page) {
		return Math.max(page, 0);
	}

	private static int normalizeSize(int size) {
		return Math.clamp(size, 1, MAX_PAGE_SIZE);
	}

	@PostMapping("/{userId}")
	public Mono<ReplyDto> createReply(@PathVariable("userId") UUID userId,
			@Valid @RequestBody ReplyCreateRequest request) {
		return this.replyService.createReply(request.setUserId(userId));
	}

	@PutMapping("/{userId}/{replyId}")
	public Mono<Void> updateReply(@PathVariable("userId") UUID userId, @PathVariable UUID replyId,
			@RequestBody ReplyUpdateRequest request) {
		return this.replyService.updateReply(request.setId(replyId).setUserId(userId));
	}

	@DeleteMapping("/{userId}/{replyId}")
	public Mono<Void> deleteReply(@PathVariable("userId") UUID userId, @PathVariable UUID replyId) {
		return this.replyService.deleteReply(userId, replyId);
	}

	@GetMapping("/tweet/{tweetId}")
	public Flux<ReplyDto> getAllRepliesForTweet(@PathVariable UUID tweetId,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "10") int size) {
		return this.replyService.getRepliesForTweet(tweetId, normalizePage(page), normalizeSize(size));
	}

	@GetMapping("/tweet/{tweetId}/count")
	public Mono<Long> getReplyCountForTweet(@PathVariable UUID tweetId) {
		return this.replyService.getReplyCountForTweet(tweetId);
	}

	@PostMapping("/tweet/counts")
	public Mono<Map<UUID, Long>> getReplyCountsForTweets(@RequestBody List<UUID> tweetIds) {
		return this.replyService.getReplyCountsForTweets(tweetIds);
	}

	@GetMapping("/tweet/{tweetId}/top")
	public Mono<ReplyDto> getTopReplyForTweet(@PathVariable UUID tweetId) {
		return this.replyService.getTopReplyForTweet(tweetId);
	}

	@PostMapping("/tweet/top")
	public Mono<Map<UUID, ReplyDto>> getTopRepliesForTweets(@RequestBody List<UUID> tweetIds) {
		return this.replyService.getTopRepliesForTweets(tweetIds);
	}

}
