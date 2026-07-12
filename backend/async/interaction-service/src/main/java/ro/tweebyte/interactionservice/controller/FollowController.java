/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.controller;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.model.FollowCountsDto;
import ro.tweebyte.interactionservice.model.FollowDto;
import ro.tweebyte.interactionservice.model.ProfileInteractionsDto;
import ro.tweebyte.interactionservice.service.FollowService;

@RestController
@RequestMapping(path = "/follows")
@RequiredArgsConstructor
public class FollowController {

	private final FollowService followService;

	@GetMapping("/{userId}/followers")
	public CompletableFuture<List<FollowDto>> getFollowers(@PathVariable("userId") UUID userId) {
		return this.followService.getFollowers(userId);
	}

	@GetMapping("/{userId}/following")
	public CompletableFuture<byte[]> getFollowing(@PathVariable("userId") UUID userId) {
		return this.followService.getFollowing(userId);
	}

	@GetMapping("/{userId}/followers/count")
	public CompletableFuture<Long> getFollowersCount(@PathVariable("userId") UUID userId) {
		return this.followService.getFollowersCount(userId);
	}

	@GetMapping("/{userId}/followers/identifiers")
	public CompletableFuture<List<UUID>> getFollowersIdentifiers(@PathVariable("userId") UUID userId) {
		return this.followService.getFollowedIdentifiers(userId);
	}

	@GetMapping("/{userId}/following/count")
	public CompletableFuture<Long> getFollowingCount(@PathVariable("userId") UUID userId) {
		return this.followService.getFollowingCount(userId);
	}

	// Consolidated follower+following counts in one round-trip for the user-profile read;
	// the original per-type /followers/count and /following/count endpoints stay for other
	// callers.
	@GetMapping("/{userId}/counts")
	public CompletableFuture<FollowCountsDto> getFollowCounts(@PathVariable("userId") UUID userId) {
		return this.followService.getFollowCounts(userId);
	}

	// Combined user-profile read: the user's follow counts plus the per-tweet interactions for
	// the supplied tweet-id page in one round-trip, so the user-profile read makes one inbound
	// call here instead of a follow-counts call plus a nested tweet-interactions call. The
	// per-type /counts and POST /tweets/interactions endpoints stay for other callers.
	@PostMapping("/{userId}/profile-interactions")
	public CompletableFuture<ProfileInteractionsDto> getProfileInteractions(@PathVariable("userId") UUID userId,
			@RequestBody List<UUID> tweetIds) {
		return this.followService.getProfileInteractions(userId, tweetIds);
	}

	@GetMapping("/{userId}/requests")
	public CompletableFuture<List<FollowDto>> getFollowRequests(@PathVariable("userId") UUID userId) {
		return this.followService.getFollowRequests(userId);
	}

	@PostMapping("/{userId}/{followedId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public CompletableFuture<Void> follow(@PathVariable("userId") UUID userId,
			@PathVariable("followedId") UUID followedId) {
		return this.followService.follow(userId, followedId);
	}

	@PutMapping("/{userId}/{followRequestId}/{status}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public CompletableFuture<Void> updateFollowRequest(@PathVariable("userId") UUID userId,
			@PathVariable("followRequestId") UUID followRequestId, @PathVariable("status") FollowEntity.Status status) {
		return this.followService.updateFollowRequest(userId, followRequestId, status);
	}

	@DeleteMapping("/{userId}/{followedId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public CompletableFuture<Void> unfollow(@PathVariable("userId") UUID userId,
			@PathVariable("followedId") UUID followedId) {
		return this.followService.unfollow(userId, followedId);
	}

}
