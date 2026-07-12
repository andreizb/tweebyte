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
import ro.tweebyte.userservice.model.UserDto;
import ro.tweebyte.userservice.model.UserUpdateRequest;
import ro.tweebyte.userservice.repository.MediaAssetRepository;
import ro.tweebyte.userservice.repository.UserRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTests {

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

	private UserEntity userEntity = new UserEntity();

	private UserDto userDto = new UserDto();

	private TweetDto tweetDto = new TweetDto();

	@BeforeEach
	void setUp() {
		this.userEntity.setId(this.userId);
		this.userEntity.setEmail("test@example.com");
		this.userEntity.setPassword("password");

		lenient().when(this.userRepository.findById(eq(this.userId))).thenReturn(Mono.just(this.userEntity));
		lenient().when(this.userRepository.findById(eq(UUID.randomUUID()))).thenReturn(Mono.empty());
		lenient().when(this.userRepository.findByUserName(any(String.class))).thenReturn(Mono.just(this.userEntity));
		lenient().when(this.userRepository.searchUsers(any(String.class), anyInt(), anyInt()))
			.thenReturn(Flux.just(this.userEntity));
		lenient().when(this.tweetClient.getUserProfileTweets(eq(this.userId))).thenReturn(Flux.just(this.tweetDto));
		lenient().when(this.interactionClient.getProfileInteractions(eq(this.userId), any(List.class)))
			.thenReturn(Mono.just(new ProfileInteractionsDto(new FollowCountsDto(20L, 10L), List.of())));
		lenient()
			.when(this.userMapper.mapToProfileDto(eq(this.userEntity), any(Long.class), any(Long.class),
					any(List.class)))
			.thenReturn(this.userDto);
		lenient().when(this.userMapper.mapToSummaryDto(any(UserEntity.class))).thenReturn(this.userDto);
	}

	@Test
	void getUserProfileTest() {
		UUID profileId = UUID.randomUUID();

		UserEntity mockUserEntity = new UserEntity();
		mockUserEntity.setId(profileId);
		mockUserEntity.setUserName("testUser");

		Long followingCount = 10L;
		Long followersCount = 20L;

		// tweet-service returns the page UN-enriched (zero counts / no top reply); the profile
		// read merges the interactions onto these tweets after the combined call.
		TweetDto tweet1 = new TweetDto();
		tweet1.setId(UUID.randomUUID());
		tweet1.setContent("Tweet 1");

		TweetDto tweet2 = new TweetDto();
		tweet2.setId(UUID.randomUUID());
		tweet2.setContent("Tweet 2");

		List<TweetDto> tweetList = List.of(tweet1, tweet2);

		TweetDto.ReplyDto topReply = TweetDto.ReplyDto.builder().id(UUID.randomUUID()).content("top reply").build();
		ro.tweebyte.userservice.model.TweetInteractionsEntryDto entry1 = new ro.tweebyte.userservice.model.TweetInteractionsEntryDto(
				tweet1.getId(), 5L, 3L, 2L, topReply);
		ro.tweebyte.userservice.model.TweetInteractionsEntryDto entry2 = new ro.tweebyte.userservice.model.TweetInteractionsEntryDto(
				tweet2.getId(), 1L, 0L, 0L, null);

		UserDto expectedUserDto = new UserDto();
		expectedUserDto.setId(profileId);
		expectedUserDto.setUserName("testUser");
		expectedUserDto.setFollowing(followingCount);
		expectedUserDto.setFollowers(followersCount);
		expectedUserDto.setTweets(tweetList);

		given(this.userRepository.findById(profileId)).willReturn(Mono.just(mockUserEntity));

		// hop1: un-enriched tweet page. hop2: ONE combined call returning both the follow counts
		// and the per-tweet interactions for that page.
		given(this.tweetClient.getUserProfileTweets(profileId)).willReturn(Flux.fromIterable(tweetList));
		given(this.interactionClient.getProfileInteractions(eq(profileId), any(List.class))).willReturn(Mono.just(
				new ProfileInteractionsDto(new FollowCountsDto(followersCount, followingCount), List.of(entry1, entry2))));

		ArgumentCaptor<List<TweetDto>> tweetsCaptor = ArgumentCaptor.forClass(List.class);
		given(this.userMapper.mapToProfileDto(eq(mockUserEntity), eq(followingCount), eq(followersCount),
				tweetsCaptor.capture()))
			.willReturn(expectedUserDto);

		StepVerifier.create(this.userService.getUserProfile(profileId)).expectNext(expectedUserDto).verifyComplete();

		// The interactions were merged onto the un-enriched tweets before the mapper ran.
		List<TweetDto> merged = tweetsCaptor.getValue();
		org.assertj.core.api.Assertions.assertThat(merged.get(0).getLikesCount()).isEqualTo(5L);
		org.assertj.core.api.Assertions.assertThat(merged.get(0).getRepliesCount()).isEqualTo(3L);
		org.assertj.core.api.Assertions.assertThat(merged.get(0).getRetweetsCount()).isEqualTo(2L);
		org.assertj.core.api.Assertions.assertThat(merged.get(0).getTopReply()).isEqualTo(topReply);
		org.assertj.core.api.Assertions.assertThat(merged.get(1).getLikesCount()).isEqualTo(1L);

		verify(this.userRepository).findById(profileId);
		verify(this.tweetClient).getUserProfileTweets(profileId);
		verify(this.interactionClient).getProfileInteractions(eq(profileId), any(List.class));
	}

	@Test
	void getUserSummaryTest() {
		StepVerifier.create(this.userService.getUserSummary(this.userId)).expectNext(this.userDto).verifyComplete();
	}

	@Test
	void getUserSummaryByUserNameTest() {
		StepVerifier.create(this.userService.getUserSummaryByUserName("username"))
			.expectNext(this.userDto)
			.verifyComplete();
	}

	@Test
	void searchUserTest() {
		StepVerifier.create(this.userService.searchUser("searchTerm", 0, 10)).expectNext(this.userDto).verifyComplete();
	}

	@Test
	void updateUserTest() {
		given(this.userRepository.save(any(UserEntity.class))).willReturn(Mono.just(this.userEntity));
		UserUpdateRequest request = new UserUpdateRequest();

		StepVerifier.create(this.userService.updateUser(this.userId, request)).verifyComplete();
	}

	@Test
	void deleteUserTest() {
		given(this.userRepository.deleteById(this.userId)).willReturn(Mono.empty());

		StepVerifier.create(this.userService.deleteUser(this.userId)).verifyComplete();

		verify(this.userRepository).deleteById(this.userId);
	}

	@Test
	void deleteUserNotFoundTest() {
		UUID missingId = UUID.randomUUID();
		given(this.userRepository.findById(missingId)).willReturn(Mono.empty());

		StepVerifier.create(this.userService.deleteUser(missingId))
			.expectError(ro.tweebyte.userservice.exception.UserNotFoundException.class)
			.verify();
	}

}
