/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import ro.tweebyte.userservice.client.InteractionClient;
import ro.tweebyte.userservice.client.KeycloakClient;
import ro.tweebyte.userservice.client.TweetClient;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.exception.UserAlreadyExistsException;
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

	private final ObjectProvider<Retry> updateUserRetry;

	// Optional by design: in the benchmark profile app.keycloak.enabled=false, so the
	// KeycloakClient bean does not exist. Resolving it through ObjectProvider lets the
	// measured updateUser path run byte-for-byte unchanged (no credential propagation) when
	// the bean is absent, while prod/FE (bean present) mirrors email/password onto the realm.
	private final ObjectProvider<KeycloakClient> keycloakClient;

	// Three cross-service round-trips collapsed to two. Leg A (findById, local) runs
	// concurrently with hop1 (getUserProfileTweets — un-enriched tweets from tweet-service);
	// once the tweets resolve, hop2 (getProfileInteractions) fetches BOTH the follow counts and
	// the per-tweet interactions for that page in one combined call. The interactions are then
	// merged onto the un-enriched tweets — keyed by tweet id, tweet list as the order source —
	// so the external profile output is unchanged.
	public Mono<UserDto> getUserProfile(UUID userId) {
		Mono<UserEntity> userMono = this.userRepository.findById(userId)
			.switchIfEmpty(Mono.error(() -> new UserNotFoundException(USER_NOT_FOUND_BY_ID + userId)));

		Mono<List<TweetDto>> tweetsMono = this.tweetClient.getUserProfileTweets(userId).collectList();

		return Mono.zip(userMono, tweetsMono).flatMap(tuple -> {
			UserEntity user = tuple.getT1();
			List<TweetDto> tweets = tuple.getT2();
			List<UUID> tweetIds = tweets.stream().map(TweetDto::getId).toList();
			return this.interactionClient.getProfileInteractions(userId, tweetIds).map(profile -> {
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

	public Mono<UserDto> getUserSummary(UUID userId) {
		return this.userRepository.findById(userId)
			.switchIfEmpty(Mono.error(() -> new UserNotFoundException(USER_NOT_FOUND_BY_ID + userId)))
			.map(this.userMapper::mapToSummaryDto);
	}

	// Batched counterpart of getUserSummary: resolves a whole page of ids in ONE
	// WHERE id IN (...) query (findAllById), reusing the same summary mapping. Unlike the
	// single-id read this does not 404 on a missing id — an absent user is simply omitted
	// from the result, so the caller (the following-cache fill) can fall back to its own
	// read-through for whatever it didn't get back. An empty request issues no query.
	public Flux<UserDto> getUserSummaries(List<UUID> userIds) {
		if (userIds.isEmpty()) {
			return Flux.empty();
		}
		return this.userRepository.findAllById(userIds).map(this.userMapper::mapToSummaryDto);
	}

	public Mono<UserDto> getUserSummaryByUserName(String userName) {
		return this.userRepository.findByUserName(userName)
			.switchIfEmpty(Mono.error(() -> new UserNotFoundException("User not found for name: " + userName)))
			.map(this.userMapper::mapToSummaryDto);
	}

	public Flux<UserDto> searchUser(String searchTerm, int page, int size) {
		// wrap with % on both sides so the repository's ILIKE matches substrings,
		// mirroring async's UserService. Without this a search for "alic" doesn't match
		// user "alice" — the repo would do `ILIKE 'alic'` instead of `ILIKE '%alic%'`.
		return this.userRepository.searchUsers("%" + searchTerm + "%", size, page * size)
			.map(this.userMapper::mapToSummaryDto);
	}

	public Mono<Void> updateUser(UUID userId, UserUpdateRequest userUpdateRequest) {
		// Capture the raw password and the new email before the save lambda hashes the
		// password in place — Keycloak's reset-password needs the cleartext, and the realm
		// must learn the new email so the old credential stops authenticating.
		String newRawPassword = userUpdateRequest.getPassword();
		String newEmail = userUpdateRequest.getEmail();

		Mono<UserEntity> saveOp = this.userRepository.findById(userId)
			.switchIfEmpty(Mono.error(() -> new UserNotFoundException(USER_NOT_FOUND_BY_ID + userId)))
			.flatMap(user -> {
				// Hash ahead of the mapper field-copy so a raw password never reaches the persisted
				// column. BCrypt is CPU-heavy: run it off the event loop on Schedulers.parallel
				// (the convention MediaService already uses) so it can't stall colocated requests.
				// The no-password path stays inline — unchanged from the field-copy + save below.
				if (newRawPassword != null) {
					return Mono.fromCallable(() -> this.passwordEncoder.encode(newRawPassword))
						.subscribeOn(Schedulers.parallel())
						.flatMap(hashed -> {
							userUpdateRequest.setPassword(hashed);
							this.userMapper.mapRequestToEntity(userUpdateRequest, user);
							// FIX 2: map DB unique-constraint violation to structured 400.
							return this.userRepository.save(user)
								.onErrorMap(DataIntegrityViolationException.class,
										ex -> new UserAlreadyExistsException(
												"A user with this email or username already exists"));
						});
				}
				this.userMapper.mapRequestToEntity(userUpdateRequest, user);
				// FIX 2: map DB unique-constraint violation to structured 400.
				return this.userRepository.save(user)
					.onErrorMap(DataIntegrityViolationException.class,
							ex -> new UserAlreadyExistsException(
									"A user with this email or username already exists"));
			});

		// Apply the retry operator only when the conditional Retry bean is registered
		// (app.resilience.retry.enabled=true, the default). In benchmark mode the bean
		// is not registered → no .retryWhen() added to the chain → save runs once on
		// failure. The UserNotFoundException filter is baked into the spec inside
		// RetryConfiguration (since the abstract Retry type doesn't expose .filter()).
		// Mirrors the async-stack mechanism that gates Spring Retry's @EnableRetry on
		// the same property.
		Retry retrySpec = this.updateUserRetry.getIfAvailable();
		Mono<UserEntity> withRetry = (retrySpec != null) ? saveOp.retryWhen(retrySpec) : saveOp;

		// Uniqueness pre-checks run before (and outside) the retried save, mirroring
		// async UserService.updateUser where existsByEmailAndIdNot/existsByUserNameAndIdNot
		// guard the load+map step inside the supplyAsync lambda, ahead of the retryable
		// delegate. The userId is passed so the user's own row is excluded (FIX 3).
		return ensureEmailAvailable(userUpdateRequest, userId).then(ensureUserNameAvailable(userUpdateRequest, userId))
			.then(ensureProfilePictureExists(userUpdateRequest))
			.then(withRetry)
			.then(Mono.defer(() -> propagateCredentials(userId, newEmail, newRawPassword)));
	}

	// Mirror a changed email and/or password onto the Keycloak realm after the local save
	// commits, so a stale email or the old password can no longer authenticate. Resolved
	// through ObjectProvider: when app.keycloak.enabled=false (benchmark) the bean is absent
	// and this collapses to Mono.empty() — the measured updateUser path adds nothing.
	private Mono<Void> propagateCredentials(UUID userId, String newEmail, String newRawPassword) {
		if (newEmail == null && newRawPassword == null) {
			return Mono.empty();
		}
		KeycloakClient client = this.keycloakClient.getIfAvailable();
		if (client == null) {
			return Mono.empty();
		}
		return client.updateCredentials(userId.toString(), newEmail, newRawPassword);
	}

	public Mono<Void> deleteUser(UUID userId) {
		return this.userRepository.findById(userId)
			.switchIfEmpty(Mono.error(() -> new UserNotFoundException(USER_NOT_FOUND_BY_ID + userId)))
			.flatMap(user -> this.userRepository.deleteById(userId));
	}

	// Swap-only: a supplied picture id must reference a real asset (the sentinel is
	// itself a seeded asset, so "remove" passes too). Absent leaves the picture
	// unchanged and skips the lookup.
	private Mono<Void> ensureProfilePictureExists(UserUpdateRequest userUpdateRequest) {
		if (userUpdateRequest.getProfilePictureId() == null) {
			return Mono.empty();
		}
		return this.mediaAssetRepository.existsById(userUpdateRequest.getProfilePictureId())
			.flatMap(exists -> Boolean.TRUE.equals(exists) ? Mono.<Void>empty()
					: Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
							"Profile picture not found for id: " + userUpdateRequest.getProfilePictureId())));
	}

	// FIX 3: pass the current userId so the user's own row is excluded from the
	// uniqueness check — updating with an unchanged email/username was wrongly rejected.
	private Mono<Void> ensureEmailAvailable(UserUpdateRequest userUpdateRequest, UUID userId) {
		if (userUpdateRequest.getEmail() == null) {
			return Mono.empty();
		}
		return this.userRepository.existsByEmailAndIdNot(userUpdateRequest.getEmail(), userId)
			.flatMap(exists -> exists
					? Mono.<Void>error(new UserAlreadyExistsException("A user with this email already exists"))
					: Mono.empty());
	}

	private Mono<Void> ensureUserNameAvailable(UserUpdateRequest userUpdateRequest, UUID userId) {
		if (userUpdateRequest.getUserName() == null) {
			return Mono.empty();
		}
		return this.userRepository.existsByUserNameAndIdNot(userUpdateRequest.getUserName(), userId)
			.flatMap(exists -> exists
					? Mono.<Void>error(new UserAlreadyExistsException("A user with this username already exists"))
					: Mono.empty());
	}

}
