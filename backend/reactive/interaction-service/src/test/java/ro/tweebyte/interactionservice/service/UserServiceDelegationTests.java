/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.interactionservice.client.UserClient;
import ro.tweebyte.interactionservice.model.UserDto;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Covers the thin UserService delegators (fetchUserSummary, mediaExists) that bypass the
 * cache and forward straight to UserClient.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceDelegationTests {

	@Mock
	private UserClient userClient;

	@Mock
	private ReactiveRedisTemplate<String, byte[]> redisTemplate;

	@InjectMocks
	private UserService userService;

	private final UUID userId = UUID.randomUUID();

	@Test
	void fetchUserSummary_ForwardsToClientWithoutTouchingCache() {
		UserDto dto = new UserDto();
		dto.setId(this.userId);
		given(this.userClient.getUserSummary(this.userId)).willReturn(Mono.just(dto));

		StepVerifier.create(this.userService.fetchUserSummary(this.userId)).expectNext(dto).verifyComplete();

		verifyNoInteractions(this.redisTemplate);
	}

	@Test
	void mediaExists_ForwardsToClient() {
		UUID mediaId = UUID.randomUUID();
		given(this.userClient.mediaExists(mediaId)).willReturn(Mono.just(true));

		StepVerifier.create(this.userService.mediaExists(mediaId)).expectNext(true).verifyComplete();

		verifyNoInteractions(this.redisTemplate);
	}

}
