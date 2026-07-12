/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import ro.tweebyte.tweetservice.client.UserClient;
import ro.tweebyte.tweetservice.model.UserDto;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserClient userClient;

	@Cacheable(value = "userIds", key = "#userName", unless = "#result == null")
	public CompletableFuture<UUID> getUserId(String userName) {
		return this.userClient.getUserSummary(userName).thenApply(UserDto::getId);
	}

	// Live (uncached) username -> id resolution for the tweet-update write path. No
	// @Cacheable: a tweet edit rewrites the mention every PUT, and resolving live mirrors
	// follow-create's live privacy read (write paths read live state) so a persisted
	// mention row never points at a stale cached id. The user-service round-trip is the
	// inter-service latency the update path exercises. Error behaviour matches getUserId
	// (same UserClient call).
	public CompletableFuture<UUID> getUserIdLive(String userName) {
		return this.userClient.getUserSummary(userName).thenApply(UserDto::getId);
	}

	// Batched author resolution for tweet search: one POST resolves every distinct author id
	// on the page in a single round-trip, replacing the per-result getUserSummary(UUID) fan-out.
	// Deliberately uncached — the search author lookup is an HTTP batch call with no cache layer
	// — so it passes straight through to the client. A missing id is omitted from the result;
	// the caller reproduces the per-call not-found by detecting the absent id.
	public CompletableFuture<List<UserDto>> getUserSummaries(List<UUID> userIds) {
		return this.userClient.getUserSummaries(userIds);
	}

	public CompletableFuture<Boolean> mediaExists(UUID mediaId) {
		return this.userClient.mediaExists(mediaId);
	}

}
