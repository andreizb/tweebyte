/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;

import ro.tweebyte.tweetservice.client.UserClient;
import ro.tweebyte.tweetservice.model.UserDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest
class UserServiceTests {

	@Mock
	private UserClient userClient;

	@InjectMocks
	private UserService userService;

	@Test
	void testGetUserId() {
		String userName = "testUser";
		UUID userId = UUID.randomUUID();
		UserDto userDto = new UserDto();
		userDto.setId(userId);

		given(this.userClient.getUserSummary(any(String.class))).willReturn(CompletableFuture.completedFuture(userDto));

		UUID result = this.userService.getUserId(userName).join();

		assertThat(result).isEqualTo(userId);
	}

	@Test
	void testGetUserIdLive() {
		// Live (uncached) resolution used by the tweet-update write path: resolves straight
		// through the client, no @Cacheable.
		String userName = "testUser";
		UUID userId = UUID.randomUUID();
		UserDto userDto = new UserDto();
		userDto.setId(userId);

		given(this.userClient.getUserSummary(any(String.class))).willReturn(CompletableFuture.completedFuture(userDto));

		UUID result = this.userService.getUserIdLive(userName).join();

		assertThat(result).isEqualTo(userId);
	}

	@Test
	void testGetUserSummaries() {
		// Batched author resolution for search: a pure, uncached passthrough to the client.
		UUID userId = UUID.randomUUID();
		UserDto userDto = new UserDto();
		userDto.setId(userId);

		given(this.userClient.getUserSummaries(java.util.List.of(userId)))
			.willReturn(CompletableFuture.completedFuture(java.util.List.of(userDto)));

		java.util.List<UserDto> result = this.userService.getUserSummaries(java.util.List.of(userId)).join();

		assertThat(result).containsExactly(userDto);
	}

}
