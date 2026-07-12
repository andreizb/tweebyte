/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import ro.tweebyte.userservice.client.InteractionClient;
import ro.tweebyte.userservice.client.KeycloakClient;
import ro.tweebyte.userservice.client.TweetClient;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.exception.UserAlreadyExistsException;
import ro.tweebyte.userservice.exception.UserException;
import ro.tweebyte.userservice.exception.UserNotFoundException;
import ro.tweebyte.userservice.mapper.UserMapper;
import ro.tweebyte.userservice.model.FollowCountsDto;
import ro.tweebyte.userservice.model.TweetDto;
import ro.tweebyte.userservice.model.TweetInteractionsEntryDto;
import ro.tweebyte.userservice.model.UserDto;
import ro.tweebyte.userservice.model.UserUpdateRequest;
import ro.tweebyte.userservice.repository.MediaAssetRepository;
import ro.tweebyte.userservice.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserService {

	private static final String USER_NOT_FOUND_BY_ID = "User not found for id: ";

	private final UserRepository userRepository;

	private final MediaAssetRepository mediaAssetRepository;

	private final InteractionClient interactionClient;

	private final TweetClient tweetClient;

	private final UserMapper userMapper;

	private final BCryptPasswordEncoder passwordEncoder;

	private final UserUpdateRetryDelegate userUpdateRetryDelegate;

	// Optional by design: in the benchmark profile app.keycloak.enabled=false, so the
	// KeycloakClient bean does not exist. Resolving it through ObjectProvider lets the
	// measured updateUser path run byte-for-byte unchanged (no credential propagation) when
	// the bean is absent, while prod/FE (bean present) mirrors email/password onto the realm.
	private final ObjectProvider<KeycloakClient> keycloakClient;

	@Qualifier("ioExecutor")
	private final ExecutorService executorService;

	// Three cross-service round-trips collapsed to two. Leg A (findById, local) runs
	// concurrently with hop1 (getUserProfileTweets — un-enriched tweets from tweet-service);
	// once both resolve, hop2 (getProfileInteractions) fetches BOTH the follow counts and the
	// per-tweet interactions for that page in one combined call. The interactions are then merged
	// onto the un-enriched tweets — keyed by tweet id, tweet list as the order source — so the
	// external profile output is unchanged.
	public CompletableFuture<UserDto> getUserProfile(UUID userId) {
		CompletableFuture<UserEntity> userFuture = CompletableFuture.supplyAsync(
				() -> this.userRepository.findById(userId)
					.orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND_BY_ID + userId)),
				this.executorService);

		CompletableFuture<List<TweetDto>> tweetsFuture = this.tweetClient.getUserProfileTweets(userId);

		return CompletableFuture.allOf(userFuture, tweetsFuture).thenCompose(v -> {
			UserEntity user;
			List<TweetDto> tweets;
			try {
				user = userFuture.get();
				tweets = tweetsFuture.get();
			}
			catch (ExecutionException ex) {
				// Surface the raw downstream cause (e.g. UserNotFoundException ->
				// 404, FollowRetrievingException -> 500) instead of masking it as a
				// blanket UserException -> 500. Mirrors reactive Mono.zip, which
				// propagates the original error unwrapped.
				Throwable cause = ex.getCause();
				if (cause instanceof RuntimeException runtimeCause) {
					throw runtimeCause;
				}
				throw new UserException(ex);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new UserException(ex);
			}
			List<UUID> tweetIds = tweets.stream().map(TweetDto::getId).toList();
			return this.interactionClient.getProfileInteractions(userId, tweetIds).thenApply(profile -> {
				mergeInteractions(tweets, profile.getTweetInteractions());
				FollowCountsDto followCounts = profile.getFollowCounts();
				return this.userMapper.mapToProfileDto(user, followCounts.getFollowing(), followCounts.getFollowers(),
						tweets);
			});
		});
	}

	// Merge the per-tweet interactions onto the un-enriched tweets in place: set each tweet's
	// like/reply/retweet counts and top reply from its matching entry, leaving hashtags and
	// mentions (already populated by tweet-service) untouched. A tweet with no matching entry
	// keeps its un-enriched zero counts / empty top reply, mirroring the enriched path's default.
	private static void mergeInteractions(List<TweetDto> tweets, List<TweetInteractionsEntryDto> interactions) {
		if (interactions == null || interactions.isEmpty()) {
			return;
		}
		Map<UUID, TweetInteractionsEntryDto> byId = new HashMap<>();
		for (TweetInteractionsEntryDto entry : interactions) {
			byId.put(entry.getTweetId(), entry);
		}
		for (TweetDto tweet : tweets) {
			TweetInteractionsEntryDto entry = byId.get(tweet.getId());
			if (entry != null) {
				tweet.setLikesCount(entry.getLikes());
				tweet.setRepliesCount(entry.getReplies());
				tweet.setRetweetsCount(entry.getRetweets());
				tweet.setTopReply(entry.getTopReply());
			}
		}
	}

	public CompletableFuture<UserDto> getUserSummary(UUID userId) {
		return CompletableFuture
			.supplyAsync(
					() -> this.userRepository.findById(userId)
						.orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND_BY_ID + userId)),
					this.executorService)
			.thenApply(this.userMapper::mapToSummaryDto);
	}

	// Batched counterpart of getUserSummary: resolves a whole page of ids in ONE
	// WHERE id IN (...) query (findAllById), reusing the same summary mapping. Unlike the
	// single-id read this does not 404 on a missing id — an absent user is simply omitted
	// from the result, so the caller (the following-cache fill) can fall back to its own
	// read-through for whatever it didn't get back. An empty request issues no query.
	public CompletableFuture<List<UserDto>> getUserSummaries(List<UUID> userIds) {
		if (userIds.isEmpty()) {
			return CompletableFuture.completedFuture(List.of());
		}
		return CompletableFuture.supplyAsync(() -> this.userRepository.findAllById(userIds), this.executorService)
			.thenApply(entities -> entities.stream().map(this.userMapper::mapToSummaryDto).toList());
	}

	public CompletableFuture<UserDto> getUserSummaryByUserName(String userName) {
		return CompletableFuture
			.supplyAsync(
					() -> this.userRepository.findByUserName(userName)
						.orElseThrow(() -> new UserNotFoundException("User not found for name: " + userName)),
					this.executorService)
			.thenApply(this.userMapper::mapToSummaryDto);
	}

	public CompletableFuture<List<UserDto>> searchUser(String searchTerm, int page, int size) {
		return CompletableFuture
			.supplyAsync(() -> this.userRepository.searchUsers('%' + searchTerm + '%', size, page * size),
					this.executorService)
			.thenApply(
					entities -> entities.stream().map(this.userMapper::mapToSummaryDto).toList());
	}

	public CompletableFuture<Void> updateUser(UUID userId, UserUpdateRequest userUpdateRequest) {
		// Capture the raw password and the new email before the supplyAsync lambda hashes the
		// password in place — Keycloak's reset-password needs the cleartext, and the realm
		// must learn the new email so the old credential stops authenticating.
		String newRawPassword = userUpdateRequest.getPassword();
		String newEmail = userUpdateRequest.getEmail();

		return CompletableFuture.supplyAsync(() -> {
			// FIX 3: use existsByEmailAndIdNot so the user's own row is excluded — updating
			// a profile with an unchanged email/username was wrongly rejected before this fix.
			if (userUpdateRequest.getEmail() != null
					&& this.userRepository.existsByEmailAndIdNot(userUpdateRequest.getEmail(), userId)) {
				throw new UserAlreadyExistsException("A user with this email already exists");
			}

			if (userUpdateRequest.getUserName() != null
					&& this.userRepository.existsByUserNameAndIdNot(userUpdateRequest.getUserName(), userId)) {
				throw new UserAlreadyExistsException("A user with this username already exists");
			}

			// Swap-only: a supplied picture id must reference a real asset
			// (the sentinel is itself a seeded asset, so "remove" passes too).
			if (userUpdateRequest.getProfilePictureId() != null
					&& !this.mediaAssetRepository.existsById(userUpdateRequest.getProfilePictureId())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Profile picture not found for id: " + userUpdateRequest.getProfilePictureId());
			}

			UserEntity userEntity = this.userRepository.findById(userId)
				.orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND_BY_ID + userId));
			// Hash here, ahead of the mapper field-copy, so a raw password never reaches
			// the persisted column; the mapper stays a pure transform.
			if (newRawPassword != null) {
				userUpdateRequest.setPassword(this.passwordEncoder.encode(newRawPassword));
			}
			this.userMapper.mapRequestToEntity(userUpdateRequest, userEntity);
			return userEntity;
		}, this.executorService)
			// Save through the retryable delegate. When app.resilience.retry.enabled=true
			// (default), Spring Retry intercepts and retries on transient DB failures.
			// When false (benchmark), the interceptor isn't registered and this is a
			// plain bean call. Mirrors reactive UserService.updateUser .retryWhen.
			// FIX 2: wrap the save in a DataIntegrityViolationException handler so a
			// concurrent update racing past the pre-checks produces a structured 400
			// instead of leaking the DB unique-constraint violation as a raw 500.
			.thenAcceptAsync(entity -> {
				try {
					this.userUpdateRetryDelegate.save(entity);
				}
				catch (DataIntegrityViolationException ex) {
					throw new UserAlreadyExistsException("A user with this email or username already exists");
				}
			}, this.executorService)
			.thenRunAsync(() -> propagateCredentials(userId, newEmail, newRawPassword), this.executorService);
	}

	// Mirror a changed email and/or password onto the Keycloak realm after the local save
	// commits, so a stale email or the old password can no longer authenticate. Resolved
	// through ObjectProvider: when app.keycloak.enabled=false (benchmark) the bean is absent
	// and this is a no-op — the measured updateUser path adds nothing.
	private void propagateCredentials(UUID userId, String newEmail, String newRawPassword) {
		if (newEmail == null && newRawPassword == null) {
			return;
		}
		KeycloakClient client = this.keycloakClient.getIfAvailable();
		if (client != null) {
			client.updateCredentials(userId.toString(), newEmail, newRawPassword);
		}
	}

	@Async("ioExecutor")
	public CompletableFuture<Void> deleteUser(UUID userId) {
		UserEntity userEntity = this.userRepository.findById(userId)
			.orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND_BY_ID + userId));
		this.userRepository.delete(userEntity);
		return CompletableFuture.completedFuture(null);
	}

}
