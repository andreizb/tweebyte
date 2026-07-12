/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import ro.tweebyte.userservice.client.InteractionClient;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTests {

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
	private UserUpdateRetryDelegate userUpdateRetryDelegate;

	@Mock
	private org.springframework.beans.factory.ObjectProvider<ro.tweebyte.userservice.client.KeycloakClient> keycloakClient;

	@Mock
	private ExecutorService executorService;

	@InjectMocks
	private UserService userService;

	@BeforeEach
	void setup() {
		ReflectionTestUtils.setField(this.userService, "executorService", Executors.newFixedThreadPool(1));
	}

	@Test
	void getUserProfile() {
		UUID userId = UUID.randomUUID();
		UserEntity mockUserEntity = new UserEntity();
		UserDto mockUserDto = new UserDto();
		long followersCount = 100L;
		long followingCount = 50L;

		// tweet-service returns the page UN-enriched; the profile read merges the interactions
		// from the combined call onto these tweets before mapping.
		TweetDto tweet = new TweetDto();
		tweet.setId(UUID.randomUUID());
		List<TweetDto> mockTweetPage = List.of(tweet);

		TweetDto.ReplyDto topReply = TweetDto.ReplyDto.builder().id(UUID.randomUUID()).content("top reply").build();
		TweetInteractionsEntryDto entry = new TweetInteractionsEntryDto(tweet.getId(), 5L, 3L, 2L, topReply);

		given(this.userRepository.findById(userId)).willReturn(Optional.of(mockUserEntity));
		// hop1: un-enriched tweet page. hop2: ONE combined call returning both the follow counts
		// and the per-tweet interactions for that page.
		given(this.tweetClient.getUserProfileTweets(userId))
			.willReturn(CompletableFuture.completedFuture(mockTweetPage));
		given(this.interactionClient.getProfileInteractions(eq(userId), any(List.class)))
			.willReturn(CompletableFuture.completedFuture(
					new ProfileInteractionsDto(new FollowCountsDto(followersCount, followingCount), List.of(entry))));

		ArgumentCaptor<List<TweetDto>> tweetsCaptor = ArgumentCaptor.forClass(List.class);
		given(this.userMapper.mapToProfileDto(any(UserEntity.class), anyLong(), anyLong(), tweetsCaptor.capture()))
			.willReturn(mockUserDto);

		CompletableFuture<UserDto> result = this.userService.getUserProfile(userId);
		UserDto resultDto = result.join();

		assertThat(resultDto).isNotNull();
		// The interactions were merged onto the un-enriched tweets before the mapper ran.
		TweetDto merged = tweetsCaptor.getValue().get(0);
		assertThat(merged.getLikesCount()).isEqualTo(5L);
		assertThat(merged.getRepliesCount()).isEqualTo(3L);
		assertThat(merged.getRetweetsCount()).isEqualTo(2L);
		assertThat(merged.getTopReply()).isEqualTo(topReply);
		verify(this.userRepository).findById(userId);
		verify(this.tweetClient).getUserProfileTweets(userId);
		verify(this.interactionClient).getProfileInteractions(eq(userId), any(List.class));
		verify(this.userMapper).mapToProfileDto(any(UserEntity.class), eq(followingCount), eq(followersCount),
				any(List.class));
	}

	@Test
	void getUserSummary() {
		UUID userId = UUID.randomUUID();
		UserEntity mockUserEntity = new UserEntity();
		UserDto mockUserDto = new UserDto();

		given(this.userRepository.findById(userId)).willReturn(Optional.of(mockUserEntity));
		given(this.userMapper.mapToSummaryDto(mockUserEntity)).willReturn(mockUserDto);

		CompletableFuture<UserDto> result = this.userService.getUserSummary(userId);
		UserDto resultDto = result.join();

		assertThat(resultDto).isNotNull();
		verify(this.userRepository).findById(userId);
		verify(this.userMapper).mapToSummaryDto(mockUserEntity);
	}

	@Test
	void getUserSummaryByUserName() {
		String userName = "testUser";
		UserEntity mockUserEntity = new UserEntity();
		UserDto mockUserDto = new UserDto();

		given(this.userRepository.findByUserName(userName)).willReturn(Optional.of(mockUserEntity));
		given(this.userMapper.mapToSummaryDto(mockUserEntity)).willReturn(mockUserDto);

		CompletableFuture<UserDto> result = this.userService.getUserSummaryByUserName(userName);
		UserDto resultDto = result.join();

		assertThat(resultDto).isNotNull();
		verify(this.userRepository).findByUserName(userName);
		verify(this.userMapper).mapToSummaryDto(mockUserEntity);
	}

	@Test
	void searchUser() {
		String searchTerm = "search";
		List<UserEntity> mockUserPage = List.of();

		given(this.userRepository.searchUsers(any(), anyInt(), anyInt())).willReturn(mockUserPage);

		CompletableFuture<List<UserDto>> result = this.userService.searchUser(searchTerm, 0, 10);
		List<UserDto> resultPage = result.join();

		assertThat(resultPage).isNotNull();
		verify(this.userRepository).searchUsers('%' + searchTerm + '%', 10, 0);
	}

	@Test
	void updateUser() {
		UUID userId = UUID.randomUUID();
		UserUpdateRequest mockUserUpdateRequest = new UserUpdateRequest();
		UserEntity mockUserEntity = new UserEntity();

		given(this.userRepository.findById(userId)).willReturn(Optional.of(mockUserEntity));
		// updateUser now routes save through UserUpdateRetryDelegate (the @Retryable
		// wrapper). The test verifies the delegate is called; the delegate's own unit
		// test verifies it forwards to userRepository.save under the @Retryable proxy.
		given(this.userUpdateRetryDelegate.save(any(UserEntity.class))).willReturn(mockUserEntity);

		CompletableFuture<Void> result = this.userService.updateUser(userId, mockUserUpdateRequest);
		result.join();

		verify(this.userRepository).findById(userId);
		verify(this.userUpdateRetryDelegate).save(mockUserEntity);
	}

	@Test
	void deleteUser() {
		UUID userId = UUID.randomUUID();
		UserEntity mockUserEntity = new UserEntity();

		given(this.userRepository.findById(userId)).willReturn(Optional.of(mockUserEntity));

		CompletableFuture<Void> result = this.userService.deleteUser(userId);
		result.join();

		verify(this.userRepository).findById(userId);
		verify(this.userRepository).delete(mockUserEntity);
	}

	@Test
	void deleteUserNotFound() {
		UUID userId = UUID.randomUUID();

		given(this.userRepository.findById(userId)).willReturn(Optional.empty());

		// @Async is bypassed in this un-proxied unit context, so the not-found
		// guard surfaces synchronously; the proxy wraps it into a failed future in prod.
		assertThatThrownBy(() -> this.userService.deleteUser(userId))
			.isInstanceOf(ro.tweebyte.userservice.exception.UserNotFoundException.class);
		verify(this.userRepository, never()).delete(any(UserEntity.class));
	}

}
