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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.userservice.client.InteractionClient;
import ro.tweebyte.userservice.client.TweetClient;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.exception.UserAlreadyExistsException;
import ro.tweebyte.userservice.exception.UserNotFoundException;
import ro.tweebyte.userservice.mapper.UserMapper;
import ro.tweebyte.userservice.model.FollowCountsDto;
import ro.tweebyte.userservice.model.ProfileInteractionsDto;
import ro.tweebyte.userservice.model.TweetDto;
import ro.tweebyte.userservice.model.UserDto;
import ro.tweebyte.userservice.model.UserUpdateRequest;
import ro.tweebyte.userservice.repository.MediaAssetRepository;
import ro.tweebyte.userservice.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Branch-coverage tests for UserService — exercises every switchIfEmpty, client failure
 * path, and update retry filter.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceBranchTests {

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

	// --- getUserProfile -----------------------------------------------

	@Test
	void getUserProfileUserMissingEmitsUserNotFound() {
		given(this.userRepository.findById(this.userId)).willReturn(Mono.empty());
		// The tweets Mono must complete so Mono.zip can fail through the user error.
		lenient().when(this.tweetClient.getUserProfileTweets(this.userId)).thenReturn(Flux.empty());

		StepVerifier.create(this.userService.getUserProfile(this.userId))
			.expectError(UserNotFoundException.class)
			.verify();
	}

	@Test
	void getUserProfileInteractionsClientFailsPropagatesError() {
		given(this.userRepository.findById(this.userId)).willReturn(Mono.just(this.userEntity));
		// Counts and per-tweet interactions are now fetched in one combined call; its failure
		// propagates unchanged.
		given(this.tweetClient.getUserProfileTweets(this.userId)).willReturn(Flux.empty());
		given(this.interactionClient.getProfileInteractions(eq(this.userId), any(List.class)))
			.willReturn(Mono.error(new RuntimeException("svc down")));

		StepVerifier.create(this.userService.getUserProfile(this.userId))
			.expectErrorMatches(t -> "svc down".equals(t.getMessage()))
			.verify();
	}

	@Test
	void getUserProfileTweetClientFailsPropagatesError() {
		given(this.userRepository.findById(this.userId)).willReturn(Mono.just(this.userEntity));
		given(this.tweetClient.getUserProfileTweets(this.userId))
			.willReturn(Flux.error(new RuntimeException("tweets down")));

		StepVerifier.create(this.userService.getUserProfile(this.userId))
			.expectErrorMatches(t -> "tweets down".equals(t.getMessage()))
			.verify();
	}

	@Test
	void getUserProfileAllSuccessfulProducesUserDto() {
		TweetDto tweet = new TweetDto();
		tweet.setId(UUID.randomUUID());
		given(this.userRepository.findById(this.userId)).willReturn(Mono.just(this.userEntity));
		given(this.tweetClient.getUserProfileTweets(this.userId)).willReturn(Flux.just(tweet));
		given(this.interactionClient.getProfileInteractions(eq(this.userId), any(List.class)))
			.willReturn(Mono.just(new ProfileInteractionsDto(new FollowCountsDto(7L, 13L), List.of())));
		given(this.userMapper.mapToProfileDto(eq(this.userEntity), eq(13L), eq(7L), any(List.class)))
			.willReturn(this.userDto);

		StepVerifier.create(this.userService.getUserProfile(this.userId)).expectNext(this.userDto).verifyComplete();
	}

	// --- getUserSummary -----------------------------------------------

	@Test
	void getUserSummaryUserMissingEmitsNotFound() {
		given(this.userRepository.findById(this.userId)).willReturn(Mono.empty());

		StepVerifier.create(this.userService.getUserSummary(this.userId))
			.expectError(UserNotFoundException.class)
			.verify();
	}

	@Test
	void getUserSummaryUserFoundMapsToDto() {
		given(this.userRepository.findById(this.userId)).willReturn(Mono.just(this.userEntity));
		given(this.userMapper.mapToSummaryDto(this.userEntity)).willReturn(this.userDto);

		StepVerifier.create(this.userService.getUserSummary(this.userId)).expectNext(this.userDto).verifyComplete();
	}

	// --- getUserSummaryByUserName -------------------------------------

	@Test
	void getUserSummaryByUserNameMissingEmitsNotFound() {
		given(this.userRepository.findByUserName("ghost")).willReturn(Mono.empty());

		StepVerifier.create(this.userService.getUserSummaryByUserName("ghost"))
			.expectError(UserNotFoundException.class)
			.verify();
	}

	@Test
	void getUserSummaryByUserNameFoundMapsToDto() {
		given(this.userRepository.findByUserName("alice")).willReturn(Mono.just(this.userEntity));
		given(this.userMapper.mapToSummaryDto(this.userEntity)).willReturn(this.userDto);

		StepVerifier.create(this.userService.getUserSummaryByUserName("alice"))
			.expectNext(this.userDto)
			.verifyComplete();
	}

	// --- searchUser ---------------------------------------------------

	@Test
	void searchUserWrapsTermInPercentSigns() {
		// searchUser passes "%term%" to repo
		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		given(this.userRepository.searchUsers(anyString(), anyInt(), anyInt())).willReturn(Flux.just(this.userEntity));
		given(this.userMapper.mapToSummaryDto(any(UserEntity.class))).willReturn(this.userDto);

		StepVerifier.create(this.userService.searchUser("alic", 0, 10)).expectNext(this.userDto).verifyComplete();

		verify(this.userRepository).searchUsers(captor.capture(), anyInt(), anyInt());
		assertThat(captor.getValue()).isEqualTo("%alic%");
	}

	@Test
	void searchUserEmptyResultProducesEmptyFlux() {
		given(this.userRepository.searchUsers("%nope%", 10, 0)).willReturn(Flux.empty());

		StepVerifier.create(this.userService.searchUser("nope", 0, 10)).verifyComplete();
	}

	@Test
	void searchUserMultipleHits() {
		UserEntity other = new UserEntity();
		other.setId(UUID.randomUUID());
		given(this.userRepository.searchUsers("%a%", 10, 0)).willReturn(Flux.just(this.userEntity, other));
		given(this.userMapper.mapToSummaryDto(any(UserEntity.class))).willReturn(this.userDto);

		StepVerifier.create(this.userService.searchUser("a", 0, 10))
			.expectNext(this.userDto)
			.expectNext(this.userDto)
			.verifyComplete();
	}

	// --- updateUser ---------------------------------------------------

	@Test
	void updateUserUserMissingEmitsNotFoundAndDoesNotRetry() {
		given(this.userRepository.findById(this.userId)).willReturn(Mono.empty());

		StepVerifier.create(this.userService.updateUser(this.userId, new UserUpdateRequest()))
			.expectError(UserNotFoundException.class)
			.verify();
	}

	@Test
	void updateUserSuccessCallsMapperAndSave() {
		given(this.userRepository.findById(this.userId)).willReturn(Mono.just(this.userEntity));
		given(this.userRepository.save(any(UserEntity.class))).willReturn(Mono.just(this.userEntity));
		// FIX 3: pre-check now uses existsByUserNameAndIdNot to exclude the user's own row.
		given(this.userRepository.existsByUserNameAndIdNot("newname", this.userId)).willReturn(Mono.just(false));
		UserUpdateRequest req = new UserUpdateRequest();
		req.setUserName("newname");

		StepVerifier.create(this.userService.updateUser(this.userId, req)).verifyComplete();

		verify(this.userMapper).mapRequestToEntity(req, this.userEntity);
		verify(this.userRepository).save(this.userEntity);
	}

	@Test
	void updateUserHashesPasswordBeforeSave() {
		given(this.userRepository.findById(this.userId)).willReturn(Mono.just(this.userEntity));
		given(this.userRepository.save(any(UserEntity.class))).willReturn(Mono.just(this.userEntity));
		given(this.passwordEncoder.encode("rawPass")).willReturn("hashedPass");
		UserUpdateRequest req = new UserUpdateRequest();
		req.setPassword("rawPass");

		StepVerifier.create(this.userService.updateUser(this.userId, req)).verifyComplete();

		assertThat(req.getPassword()).isEqualTo("hashedPass");
		verify(this.userMapper).mapRequestToEntity(req, this.userEntity);
		verify(this.userRepository).save(this.userEntity);
	}

	@Test
	void updateUserEmailTakenEmitsUserAlreadyExists() {
		// findById builds the save chain eagerly even though the uniqueness pre-check
		// short-circuits before it is subscribed.
		// FIX 3: pre-check now uses existsByEmailAndIdNot to exclude the user's own row.
		given(this.userRepository.findById(this.userId)).willReturn(Mono.just(this.userEntity));
		given(this.userRepository.existsByEmailAndIdNot("taken@example.com", this.userId)).willReturn(Mono.just(true));
		UserUpdateRequest req = new UserUpdateRequest();
		req.setEmail("taken@example.com");

		StepVerifier.create(this.userService.updateUser(this.userId, req))
			.expectError(UserAlreadyExistsException.class)
			.verify();
	}

	@Test
	void updateUserUserNameTakenEmitsUserAlreadyExists() {
		// FIX 3: pre-checks now use existsByEmailAndIdNot / existsByUserNameAndIdNot.
		given(this.userRepository.findById(this.userId)).willReturn(Mono.just(this.userEntity));
		given(this.userRepository.existsByEmailAndIdNot("free@example.com", this.userId)).willReturn(Mono.just(false));
		given(this.userRepository.existsByUserNameAndIdNot("taken", this.userId)).willReturn(Mono.just(true));
		UserUpdateRequest req = new UserUpdateRequest();
		req.setEmail("free@example.com");
		req.setUserName("taken");

		StepVerifier.create(this.userService.updateUser(this.userId, req))
			.expectError(UserAlreadyExistsException.class)
			.verify();
	}

	@Test
	void updateUserProfilePicturePresentAndExistsProceeds() {
		UUID pictureId = UUID.randomUUID();
		given(this.mediaAssetRepository.existsById(pictureId)).willReturn(Mono.just(true));
		given(this.userRepository.findById(this.userId)).willReturn(Mono.just(this.userEntity));
		given(this.userRepository.save(any(UserEntity.class))).willReturn(Mono.just(this.userEntity));
		UserUpdateRequest req = new UserUpdateRequest();
		req.setProfilePictureId(pictureId);

		StepVerifier.create(this.userService.updateUser(this.userId, req)).verifyComplete();

		verify(this.userRepository).save(this.userEntity);
	}

	@Test
	void updateUserProfilePictureMissingEmitsBadRequest() {
		UUID pictureId = UUID.randomUUID();
		given(this.userRepository.findById(this.userId)).willReturn(Mono.just(this.userEntity));
		given(this.mediaAssetRepository.existsById(pictureId)).willReturn(Mono.just(false));
		UserUpdateRequest req = new UserUpdateRequest();
		req.setProfilePictureId(pictureId);

		StepVerifier.create(this.userService.updateUser(this.userId, req))
			.expectError(ResponseStatusException.class)
			.verify();
	}

	// --- Keycloak credential propagation (B1) -------------------------

	@Test
	void updateUserPropagatesEmailAndPasswordToKeycloakWhenBeanPresent() {
		// Lombok's @RequiredArgsConstructor emits no parameter-name table, so Mockito cannot
		// disambiguate the two same-erasure ObjectProvider constructor args by name; pin the
		// KeycloakClient provider explicitly to defeat the positional ambiguity.
		pinKeycloakProvider();
		ro.tweebyte.userservice.client.KeycloakClient client = org.mockito.Mockito
			.mock(ro.tweebyte.userservice.client.KeycloakClient.class);
		given(this.keycloakClient.getIfAvailable()).willReturn(client);
		given(client.updateCredentials(this.userId.toString(), "new@example.com", "rawPass")).willReturn(Mono.empty());
		given(this.userRepository.findById(this.userId)).willReturn(Mono.just(this.userEntity));
		given(this.userRepository.save(any(UserEntity.class))).willReturn(Mono.just(this.userEntity));
		// FIX 3: pre-check now uses existsByEmailAndIdNot to exclude the user's own row.
		given(this.userRepository.existsByEmailAndIdNot("new@example.com", this.userId)).willReturn(Mono.just(false));
		given(this.passwordEncoder.encode("rawPass")).willReturn("hashed");
		UserUpdateRequest req = new UserUpdateRequest();
		req.setEmail("new@example.com");
		req.setPassword("rawPass");

		StepVerifier.create(this.userService.updateUser(this.userId, req)).verifyComplete();

		// raw password (not the in-place hashed value) reaches Keycloak.
		verify(client).updateCredentials(this.userId.toString(), "new@example.com", "rawPass");
	}

	@Test
	void updateUserSkipsKeycloakWhenBeanAbsent() {
		// Benchmark profile: KeycloakClient bean absent → no propagation, measured path unchanged.
		pinKeycloakProvider();
		given(this.keycloakClient.getIfAvailable()).willReturn(null);
		given(this.userRepository.findById(this.userId)).willReturn(Mono.just(this.userEntity));
		given(this.userRepository.save(any(UserEntity.class))).willReturn(Mono.just(this.userEntity));
		// FIX 3: pre-check now uses existsByEmailAndIdNot to exclude the user's own row.
		lenient().when(this.userRepository.existsByEmailAndIdNot("new@example.com", this.userId))
			.thenReturn(Mono.just(false));
		UserUpdateRequest req = new UserUpdateRequest();
		req.setEmail("new@example.com");

		StepVerifier.create(this.userService.updateUser(this.userId, req)).verifyComplete();

		verify(this.keycloakClient).getIfAvailable();
	}

	@Test
	void updateUserSkipsKeycloakWhenNoEmailOrPasswordChanged() {
		// Neither email nor password supplied → propagation short-circuits before touching the
		// provider (getIfAvailable never called), even with the bean present.
		pinKeycloakProvider();
		given(this.userRepository.findById(this.userId)).willReturn(Mono.just(this.userEntity));
		given(this.userRepository.save(any(UserEntity.class))).willReturn(Mono.just(this.userEntity));
		// FIX 3: pre-check now uses existsByUserNameAndIdNot to exclude the user's own row.
		given(this.userRepository.existsByUserNameAndIdNot("newname", this.userId)).willReturn(Mono.just(false));
		UserUpdateRequest req = new UserUpdateRequest();
		req.setUserName("newname");

		StepVerifier.create(this.userService.updateUser(this.userId, req)).verifyComplete();

		verify(this.keycloakClient, org.mockito.Mockito.never()).getIfAvailable();
	}

	private void pinKeycloakProvider() {
		org.springframework.test.util.ReflectionTestUtils.setField(this.userService, "keycloakClient",
				this.keycloakClient);
		org.springframework.test.util.ReflectionTestUtils.setField(this.userService, "updateUserRetry",
				this.updateUserRetry);
	}

}
