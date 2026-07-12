/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import ro.tweebyte.userservice.client.InteractionClient;
import ro.tweebyte.userservice.client.KeycloakClient;
import ro.tweebyte.userservice.client.TweetClient;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.mapper.UserMapper;
import ro.tweebyte.userservice.model.FollowCountsDto;
import ro.tweebyte.userservice.model.ProfileInteractionsDto;
import ro.tweebyte.userservice.model.TweetDto;
import ro.tweebyte.userservice.model.TweetInteractionsEntryDto;
import ro.tweebyte.userservice.model.UserDto;
import ro.tweebyte.userservice.model.UserUpdateRequest;
import ro.tweebyte.userservice.repository.MediaAssetRepository;
import ro.tweebyte.userservice.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Branch-coverage tests for the conditional arms {@link UserServiceTests} and
 * {@link UserServiceAdditionalTests} leave open: the in-place {@code mergeInteractions}
 * guards (null / empty interactions short-circuit, and a tweet with no matching entry
 * keeping its zero counts), the batched {@code getUserSummaries} (empty issues no query /
 * non-empty maps the page), the {@code updateUser} profile-picture validation (missing id →
 * 400 vs present id → save), and the credential-propagation short-circuit on a
 * password-only change.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceBranchTests {

	@Mock
	private UserRepository userRepository;

	@Mock
	private MediaAssetRepository mediaAssetRepository;

	@Mock
	private InteractionClient interactionClient;

	@Mock
	private TweetClient tweetClient;

	@Mock
	private UserMapper userMapper;

	@Mock
	private BCryptPasswordEncoder passwordEncoder;

	@Mock
	private UserUpdateRetryDelegate userUpdateRetryDelegate;

	@Mock
	private org.springframework.beans.factory.ObjectProvider<KeycloakClient> keycloakClient;

	@Mock
	private ExecutorService executorService;

	@InjectMocks
	private UserService userService;

	@BeforeEach
	void setup() {
		ReflectionTestUtils.setField(this.userService, "executorService", Executors.newFixedThreadPool(1));
	}

	// --- getUserProfile -> mergeInteractions guards (L121, L130) ------
	// mergeInteractions mutates the un-enriched tweets in place, so each test asserts on
	// the very tweet reference handed to getUserProfile after the future resolves.

	@Test
	void getUserProfileLeavesTweetUntouchedWhenInteractionsNull() {
		UUID userId = UUID.randomUUID();
		TweetDto tweet = new TweetDto();
		tweet.setId(UUID.randomUUID());
		tweet.setLikesCount(0L);
		given(this.userRepository.findById(userId)).willReturn(Optional.of(new UserEntity()));
		given(this.tweetClient.getUserProfileTweets(userId))
			.willReturn(CompletableFuture.completedFuture(List.of(tweet)));
		// null interactions → mergeInteractions short-circuits before building the index.
		given(this.interactionClient.getProfileInteractions(eq(userId), anyList())).willReturn(CompletableFuture
			.completedFuture(new ProfileInteractionsDto(new FollowCountsDto(1L, 2L), null)));
		given(this.userMapper.mapToProfileDto(any(), any(), any(), anyList())).willReturn(new UserDto());

		this.userService.getUserProfile(userId).join();

		// Un-enriched defaults preserved: no interaction merge happened.
		assertThat(tweet.getLikesCount()).isZero();
		assertThat(tweet.getTopReply()).isNull();
	}

	@Test
	void getUserProfileLeavesTweetUntouchedWhenInteractionsEmpty() {
		UUID userId = UUID.randomUUID();
		TweetDto tweet = new TweetDto();
		tweet.setId(UUID.randomUUID());
		tweet.setRepliesCount(0L);
		given(this.userRepository.findById(userId)).willReturn(Optional.of(new UserEntity()));
		given(this.tweetClient.getUserProfileTweets(userId))
			.willReturn(CompletableFuture.completedFuture(List.of(tweet)));
		// empty interactions → same short-circuit via the isEmpty() arm.
		given(this.interactionClient.getProfileInteractions(eq(userId), anyList())).willReturn(CompletableFuture
			.completedFuture(new ProfileInteractionsDto(new FollowCountsDto(0L, 0L), List.of())));
		given(this.userMapper.mapToProfileDto(any(), any(), any(), anyList())).willReturn(new UserDto());

		this.userService.getUserProfile(userId).join();

		assertThat(tweet.getRepliesCount()).isZero();
	}

	@Test
	void getUserProfileKeepsUnenrichedCountsForTweetWithNoMatchingEntry() {
		UUID userId = UUID.randomUUID();
		// Un-enriched tweet as tweet-service emits it: zero counts, no top reply.
		TweetDto tweet = new TweetDto();
		tweet.setId(UUID.randomUUID());
		tweet.setLikesCount(0L);
		tweet.setRetweetsCount(0L);
		given(this.userRepository.findById(userId)).willReturn(Optional.of(new UserEntity()));
		given(this.tweetClient.getUserProfileTweets(userId))
			.willReturn(CompletableFuture.completedFuture(List.of(tweet)));
		// The single interaction entry is keyed by a DIFFERENT tweet id, so byId.get(tweet.getId())
		// returns null and the per-tweet merge is skipped (entry == null arm): the stray entry's
		// 9/8/7 counts must NOT leak onto this tweet.
		TweetInteractionsEntryDto strayEntry = new TweetInteractionsEntryDto(UUID.randomUUID(), 9L, 8L, 7L, null);
		given(this.interactionClient.getProfileInteractions(eq(userId), anyList())).willReturn(CompletableFuture
			.completedFuture(new ProfileInteractionsDto(new FollowCountsDto(0L, 0L), List.of(strayEntry))));
		given(this.userMapper.mapToProfileDto(any(), any(), any(), anyList())).willReturn(new UserDto());

		this.userService.getUserProfile(userId).join();

		// No matching entry → the tweet keeps its un-enriched zero counts.
		assertThat(tweet.getLikesCount()).isZero();
		assertThat(tweet.getRetweetsCount()).isZero();
		assertThat(tweet.getTopReply()).isNull();
	}

	// --- getUserSummaries: empty vs non-empty (L154) ------------------

	@Test
	void getUserSummariesReturnsEmptyAndIssuesNoQueryForEmptyIds() {
		List<UserDto> result = this.userService.getUserSummaries(List.of()).join();

		assertThat(result).isEmpty();
		// Empty request issues no findAllById query.
		verify(this.userRepository, never()).findAllById(anyList());
	}

	@Test
	void getUserSummariesMapsPageForNonEmptyIds() {
		UUID id = UUID.randomUUID();
		UserEntity entity = new UserEntity();
		UserDto dto = new UserDto();
		given(this.userRepository.findAllById(List.of(id))).willReturn(List.of(entity));
		given(this.userMapper.mapToSummaryDto(entity)).willReturn(dto);

		List<UserDto> result = this.userService.getUserSummaries(List.of(id)).join();

		assertThat(result).containsExactly(dto);
		verify(this.userRepository).findAllById(List.of(id));
	}

	// --- updateUser: profile-picture validation (L198, L199) ----------

	@Test
	void updateUserRejectsUnknownProfilePictureIdWithBadRequest() {
		UUID userId = UUID.randomUUID();
		UUID pictureId = UUID.randomUUID();
		UserUpdateRequest req = new UserUpdateRequest();
		req.setProfilePictureId(pictureId);
		// Supplied picture id references no asset → 400 BAD_REQUEST before findById/save.
		given(this.mediaAssetRepository.existsById(pictureId)).willReturn(false);

		Throwable ex = catchThrowable(() -> this.userService.updateUser(userId, req).join());

		assertThat(ex).isInstanceOf(CompletionException.class);
		assertThat(ex.getCause()).isInstanceOf(ResponseStatusException.class);
		assertThat(((ResponseStatusException) ex.getCause()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		verify(this.userUpdateRetryDelegate, never()).save(any());
	}

	@Test
	void updateUserAcceptsExistingProfilePictureIdAndSaves() {
		UUID userId = UUID.randomUUID();
		UUID pictureId = UUID.randomUUID();
		UserUpdateRequest req = new UserUpdateRequest();
		req.setProfilePictureId(pictureId);
		UserEntity entity = new UserEntity();
		// A supplied id that DOES reference a real asset passes the swap-only guard.
		given(this.mediaAssetRepository.existsById(pictureId)).willReturn(true);
		given(this.userRepository.findById(userId)).willReturn(Optional.of(entity));

		this.userService.updateUser(userId, req).join();

		verify(this.userMapper).mapRequestToEntity(req, entity);
		verify(this.userUpdateRetryDelegate).save(entity);
	}

	// --- propagateCredentials: password-only short-circuit miss (L227) ---

	@Test
	void updateUserPropagatesWhenOnlyPasswordChanged() {
		// email null but password present → the (newEmail == null && newRawPassword == null)
		// guard is FALSE on its second operand, so propagation proceeds to the provider.
		UUID userId = UUID.randomUUID();
		UserUpdateRequest req = new UserUpdateRequest();
		req.setPassword("rawPass");
		UserEntity entity = new UserEntity();
		KeycloakClient client = org.mockito.Mockito.mock(KeycloakClient.class);
		given(this.userRepository.findById(userId)).willReturn(Optional.of(entity));
		given(this.passwordEncoder.encode("rawPass")).willReturn("hashed");
		given(this.keycloakClient.getIfAvailable()).willReturn(client);

		this.userService.updateUser(userId, req).join();

		// Raw (pre-hash) password reaches Keycloak; email stays null.
		verify(client).updateCredentials(userId.toString(), null, "rawPass");
	}

}
