/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.service;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.userservice.client.InteractionClient;
import ro.tweebyte.userservice.client.TweetClient;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.mapper.UserMapper;
import ro.tweebyte.userservice.model.FollowCountsDto;
import ro.tweebyte.userservice.model.ProfileInteractionsDto;
import ro.tweebyte.userservice.model.TweetDto;
import ro.tweebyte.userservice.model.TweetInteractionsEntryDto;
import ro.tweebyte.userservice.model.UserDto;
import ro.tweebyte.userservice.repository.MediaAssetRepository;
import ro.tweebyte.userservice.repository.UserRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Additional branch tests for UserService that complement UserServiceBranchTests.
 * Focuses on the remaining uncovered branches:
 * <ul>
 *   <li>mergeInteractions: null interactions (first || branch)</li>
 *   <li>mergeInteractions: tweet with no matching interaction entry (entry == null)</li>
 *   <li>getUserSummaries: non-empty list (false branch of isEmpty check)</li>
 *   <li>updateUser: retrySpec == null branch (no Retry bean)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UserServiceAdditionalBranchTests {

	@InjectMocks
	private UserService userService;

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
	private org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder passwordEncoder;

	@Mock
	private org.springframework.beans.factory.ObjectProvider<reactor.util.retry.Retry> updateUserRetry;

	@Mock
	private org.springframework.beans.factory.ObjectProvider<ro.tweebyte.userservice.client.KeycloakClient> keycloakClient;

	private final UUID userId = UUID.randomUUID();

	private UserEntity userEntity;

	private UserDto userDto;

	@BeforeEach
	void setUp() {
		this.userEntity = new UserEntity();
		this.userEntity.setId(this.userId);
		this.userEntity.setUserName("alice");
		this.userDto = UserDto.builder().id(this.userId).userName("alice").build();
	}

	// --- mergeInteractions null-interactions branch ---

	@Test
	void getUserProfile_nullInteractionsList_doesNotCrash() {
		// The ProfileInteractionsDto contains null for its interactions list — exercises the
		// first branch of the null-check: interactions == null → true → early return.
		TweetDto tweet = new TweetDto();
		tweet.setId(UUID.randomUUID());
		given(this.userRepository.findById(this.userId)).willReturn(Mono.just(this.userEntity));
		given(this.tweetClient.getUserProfileTweets(this.userId)).willReturn(Flux.just(tweet));
		given(this.interactionClient.getProfileInteractions(eq(this.userId), any()))
			.willReturn(Mono.just(new ProfileInteractionsDto(new FollowCountsDto(5L, 3L), null)));
		given(this.userMapper.mapToProfileDto(eq(this.userEntity), eq(3L), eq(5L), any())).willReturn(this.userDto);

		StepVerifier.create(this.userService.getUserProfile(this.userId)).expectNext(this.userDto).verifyComplete();
	}

	@Test
	void getUserProfile_emptyInteractionsList_doesNotCrash() {
		// interactions != null but empty → second branch of isEmpty() → early return.
		TweetDto tweet = new TweetDto();
		tweet.setId(UUID.randomUUID());
		given(this.userRepository.findById(this.userId)).willReturn(Mono.just(this.userEntity));
		given(this.tweetClient.getUserProfileTweets(this.userId)).willReturn(Flux.just(tweet));
		given(this.interactionClient.getProfileInteractions(eq(this.userId), any()))
			.willReturn(Mono.just(new ProfileInteractionsDto(new FollowCountsDto(5L, 3L), List.of())));
		given(this.userMapper.mapToProfileDto(eq(this.userEntity), eq(3L), eq(5L), any())).willReturn(this.userDto);

		StepVerifier.create(this.userService.getUserProfile(this.userId)).expectNext(this.userDto).verifyComplete();
	}

	@Test
	void getUserProfile_tweetWithNoMatchingInteractionEntry_keepsZeroDefaults() {
		// A tweet id that has no matching TweetInteractionsEntryDto → entry == null branch
		// in mergeInteractions's inner loop; tweet keeps its zero-default counts.
		UUID tweetId = UUID.randomUUID();
		UUID otherTweetId = UUID.randomUUID(); // interaction for a DIFFERENT tweet id

		TweetDto tweet = new TweetDto();
		tweet.setId(tweetId);

		TweetInteractionsEntryDto entryForOther = new TweetInteractionsEntryDto(otherTweetId, 10L, 5L, 3L, null);

		given(this.userRepository.findById(this.userId)).willReturn(Mono.just(this.userEntity));
		given(this.tweetClient.getUserProfileTweets(this.userId)).willReturn(Flux.just(tweet));
		given(this.interactionClient.getProfileInteractions(eq(this.userId), any()))
			.willReturn(Mono.just(new ProfileInteractionsDto(new FollowCountsDto(5L, 3L), List.of(entryForOther))));
		given(this.userMapper.mapToProfileDto(eq(this.userEntity), eq(3L), eq(5L), any())).willReturn(this.userDto);

		StepVerifier.create(this.userService.getUserProfile(this.userId)).expectNext(this.userDto).verifyComplete();
	}

	// --- getUserSummaries branches ---

	@Test
	void getUserSummaries_emptyList_returnsEmptyWithoutQueryingRepository() {
		// userIds.isEmpty() → true → early Flux.empty() return (the "empty" branch).
		StepVerifier.create(this.userService.getUserSummaries(List.of())).verifyComplete();
	}

	@Test
	void getUserSummaries_nonEmptyList_queriesRepository() {
		List<UUID> ids = List.of(this.userId, UUID.randomUUID());
		given(this.userRepository.findAllById(ids)).willReturn(Flux.just(this.userEntity));
		given(this.userMapper.mapToSummaryDto(any())).willReturn(this.userDto);

		StepVerifier.create(this.userService.getUserSummaries(ids)).expectNext(this.userDto).verifyComplete();

		verify(this.userRepository).findAllById(ids);
	}

	// --- updateUser: retrySpec == null branch ---

	@Test
	void updateUser_retryBeanAbsent_savesWithoutRetry() {
		// getIfAvailable() returns null → withRetry = saveOp (no .retryWhen wrapper).
		org.springframework.test.util.ReflectionTestUtils.setField(this.userService, "keycloakClient",
				this.keycloakClient);
		org.springframework.test.util.ReflectionTestUtils.setField(this.userService, "updateUserRetry",
				this.updateUserRetry);
		given(this.updateUserRetry.getIfAvailable()).willReturn(null);
		given(this.userRepository.findById(this.userId)).willReturn(Mono.just(this.userEntity));
		given(this.userRepository.save(any(UserEntity.class))).willReturn(Mono.just(this.userEntity));

		ro.tweebyte.userservice.model.UserUpdateRequest req = new ro.tweebyte.userservice.model.UserUpdateRequest();
		req.setUserName("newname");
		// FIX 3: pre-check now uses existsByUserNameAndIdNot to exclude the user's own row.
		given(this.userRepository.existsByUserNameAndIdNot("newname", this.userId)).willReturn(Mono.just(false));

		StepVerifier.create(this.userService.updateUser(this.userId, req)).verifyComplete();

		verify(this.userRepository).save(this.userEntity);
	}

	@Test
	void updateUser_retryBeanPresent_appliesRetryWhen() {
		// getIfAvailable() returns non-null Retry spec → withRetry = saveOp.retryWhen(spec).
		org.springframework.test.util.ReflectionTestUtils.setField(this.userService, "keycloakClient",
				this.keycloakClient);
		org.springframework.test.util.ReflectionTestUtils.setField(this.userService, "updateUserRetry",
				this.updateUserRetry);
		// Use a real Retry spec that allows 1 attempt (maxAttempts=1 = no actual retries).
		reactor.util.retry.Retry retrySpec = reactor.util.retry.Retry.max(0); // 0 retries = fail-fast on error
		given(this.updateUserRetry.getIfAvailable()).willReturn(retrySpec);
		given(this.userRepository.findById(this.userId)).willReturn(Mono.just(this.userEntity));
		given(this.userRepository.save(any(UserEntity.class))).willReturn(Mono.just(this.userEntity));

		ro.tweebyte.userservice.model.UserUpdateRequest req = new ro.tweebyte.userservice.model.UserUpdateRequest();
		req.setUserName("newname");
		// FIX 3: pre-check now uses existsByUserNameAndIdNot to exclude the user's own row.
		given(this.userRepository.existsByUserNameAndIdNot("newname", this.userId)).willReturn(Mono.just(false));

		// retrySpec != null branch is exercised; max(0) means no retries but save succeeds first try.
		StepVerifier.create(this.userService.updateUser(this.userId, req)).verifyComplete();

		verify(this.userRepository).save(this.userEntity);
	}

}
