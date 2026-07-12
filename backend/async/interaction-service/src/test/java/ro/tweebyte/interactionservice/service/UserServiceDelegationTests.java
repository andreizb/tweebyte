/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ro.tweebyte.interactionservice.client.UserClient;
import ro.tweebyte.interactionservice.model.UserDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Covers the thin UserService delegators (fetchUserSummary, mediaExists) that bypass the
 * {@code @Cacheable} summary read and forward straight to UserClient.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceDelegationTests {

	@Mock
	private UserClient userClient;

	@InjectMocks
	private UserService userService;

	@Test
	void fetchUserSummary_ForwardsToClient() throws Exception {
		UUID userId = UUID.randomUUID();
		UserDto dto = new UserDto();
		dto.setId(userId);
		given(this.userClient.getUserSummary(userId)).willReturn(CompletableFuture.completedFuture(dto));

		assertThat(this.userService.fetchUserSummary(userId).get()).isEqualTo(dto);

		verify(this.userClient).getUserSummary(userId);
	}

	@Test
	void mediaExists_ForwardsToClient() throws Exception {
		UUID mediaId = UUID.randomUUID();
		given(this.userClient.mediaExists(mediaId)).willReturn(CompletableFuture.completedFuture(true));

		assertThat(this.userService.mediaExists(mediaId).get()).isTrue();

		verify(this.userClient).mediaExists(mediaId);
	}

}
