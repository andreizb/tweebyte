/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.controller;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ro.tweebyte.interactionservice.model.TweetInteractionsEntryDto;
import ro.tweebyte.interactionservice.service.TweetInteractionsService;

/**
 * Consolidated per-tweet interaction read for the tweet-enrichment fan-out: one POST over
 * a list of tweet ids returns each tweet's like/reply/retweet counts and top reply,
 * replacing the prior four batched POSTs. The original per-type batch endpoints
 * (/likes/counts, /replies/tweet/counts, /retweets/tweet/counts, /replies/tweet/top) stay
 * for other callers.
 *
 * @author Andrei Zbarcea
 */
@RestController
@RequestMapping(path = "/tweets")
@RequiredArgsConstructor
public class TweetInteractionsController {

	private final TweetInteractionsService tweetInteractionsService;

	@PostMapping("/interactions")
	public CompletableFuture<List<TweetInteractionsEntryDto>> getTweetInteractions(@RequestBody List<UUID> tweetIds) {
		return this.tweetInteractionsService.getTweetInteractionsEntries(tweetIds);
	}

}
