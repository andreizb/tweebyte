/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.service;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.tweetservice.client.UserClient;
import ro.tweebyte.tweetservice.model.UserDto;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTests {

	@InjectMocks
	private UserService userService;

	@Mock
	private UserClient userClient;

	@Mock
	private ReactiveRedisTemplate<String, Object> redisTemplate;

	@Mock
	private ReactiveValueOperations<String, Object> valueOperations;

	private UUID userId;

	private UserDto userDto;

	@BeforeEach
	void setup() {
		this.userId = UUID.randomUUID();
		this.userDto = new UserDto();
		this.userDto.setId(this.userId);
		this.userDto.setUserName("sampleUser");
	}

	@Test
	void getUserId_CacheMiss() {
		String key = "userIds::sampleUser";
		java.time.Duration ttl = java.time.Duration.ofMinutes(5);

		given(this.redisTemplate.opsForValue()).willReturn(this.valueOperations);
		given(this.valueOperations.get(any())).willReturn(Mono.empty());
		given(this.userClient.getUserSummary("sampleUser")).willReturn(Mono.just(this.userDto));
		given(this.valueOperations.set(key, this.userId, ttl)).willReturn(Mono.empty());

		StepVerifier.create(this.userService.getUserId("sampleUser")).expectNext(this.userId).verifyComplete();

		verify(this.valueOperations).get(key);
		verify(this.userClient).getUserSummary("sampleUser");
		verify(this.valueOperations).set(key, this.userId, ttl);
	}

	@Test
	void getUserIdLive_resolvesViaClientWithoutTouchingCache() {
		given(this.userClient.getUserSummary("sampleUser")).willReturn(Mono.just(this.userDto));

		StepVerifier.create(this.userService.getUserIdLive("sampleUser")).expectNext(this.userId).verifyComplete();

		verify(this.userClient).getUserSummary("sampleUser");
		// Live resolution never reads or writes the userIds:: cache.
		org.mockito.Mockito.verifyNoInteractions(this.redisTemplate);
	}

	@Test
	void getUserSummaries_resolvesViaClientWithoutTouchingCache() {
		// Batched author resolution for search: a pure, uncached passthrough to the client that
		// never reads or writes Redis.
		given(this.userClient.getUserSummaries(java.util.List.of(this.userId)))
			.willReturn(Mono.just(java.util.List.of(this.userDto)));

		StepVerifier.create(this.userService.getUserSummaries(java.util.List.of(this.userId)))
			.expectNext(java.util.List.of(this.userDto))
			.verifyComplete();

		verify(this.userClient).getUserSummaries(java.util.List.of(this.userId));
		org.mockito.Mockito.verifyNoInteractions(this.redisTemplate);
	}

}
