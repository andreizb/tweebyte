/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.interactionservice.client.UserClient;
import ro.tweebyte.interactionservice.model.UserDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class UserServiceTests {

	@Mock
	private UserClient userClient;

	@Mock
	private ReactiveRedisTemplate<String, byte[]> redisTemplate;

	@Mock
	private ReactiveValueOperations<String, byte[]> valueOperations;

	@InjectMocks
	private UserService userService;

	private final UUID userId = UUID.randomUUID();

	private final String redisKey = "users::" + this.userId;

	private UserDto mockUser;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		ReflectionTestUtils.setField(this.userService, "cacheTtl", Duration.ofSeconds(60));
		this.mockUser = new UserDto();
		this.mockUser.setId(this.userId);
		this.mockUser.setUserName("testuser");
		given(this.redisTemplate.opsForValue()).willReturn(this.valueOperations);
	}

	@Test
	void getUserSummary_cacheMiss() {
		given(this.valueOperations.get(this.redisKey)).willReturn(Mono.empty());
		given(this.userClient.getUserSummary(this.userId)).willReturn(Mono.just(this.mockUser));
		given(this.valueOperations.set(eq(this.redisKey), any(byte[].class), any(Duration.class)))
			.willReturn(Mono.just(true));

		Mono<UserDto> result = this.userService.getUserSummary(this.userId);

		StepVerifier.create(result).expectNext(this.mockUser).verifyComplete();

		verify(this.valueOperations).get(this.redisKey);
		verify(this.userClient).getUserSummary(this.userId);
		verify(this.valueOperations).set(eq(this.redisKey), any(byte[].class), any(Duration.class));
	}

	@Test
	void getUserSummary_cacheMiss_clientError() {
		given(this.valueOperations.get(this.redisKey)).willReturn(Mono.empty());
		given(this.userClient.getUserSummary(this.userId)).willReturn(Mono.error(new RuntimeException("Client error")));

		Mono<UserDto> result = this.userService.getUserSummary(this.userId);

		StepVerifier.create(result).expectError(RuntimeException.class).verify();

		verify(this.valueOperations).get(this.redisKey);
		verify(this.userClient).getUserSummary(this.userId);
		verify(this.valueOperations, never()).set(anyString(), any(byte[].class), any(Duration.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void getUserSummaries_BatchedFetch_WritesBackInOneEval() {
		// The cold-fill resolves the ids in one downstream call and writes every summary back to
		// its users:: key in ONE EVAL round-trip (a SETEX-each script), not one set() per user.
		UUID otherId = UUID.randomUUID();
		UserDto first = new UserDto();
		first.setId(this.userId);
		first.setUserName("first");
		UserDto second = new UserDto();
		second.setId(otherId);
		second.setUserName("second");
		given(this.userClient.getUserSummaries(List.of(this.userId, otherId)))
			.willReturn(Mono.just(List.of(first, second)));
		given(this.redisTemplate.execute(any(RedisScript.class), anyList(), anyList())).willReturn(Flux.just(2L));

		Mono<Map<UUID, UserDto>> result = this.userService.getUserSummaries(List.of(this.userId, otherId));

		StepVerifier.create(result)
			.assertNext(byId -> assertThat(byId).containsEntry(this.userId, first).containsEntry(otherId, second))
			.verifyComplete();
		// ONE EVAL writeback for both users (not a per-user set()).
		verify(this.redisTemplate).execute(any(RedisScript.class), anyList(), anyList());
		verify(this.valueOperations, never()).set(anyString(), any(byte[].class), any(Duration.class));
	}

	@Test
	void getUserSummaries_EmptyIds_ShortCircuits() {
		StepVerifier.create(this.userService.getUserSummaries(List.of()))
			.assertNext(byId -> assertThat(byId).isEmpty())
			.verifyComplete();

		verify(this.userClient, never()).getUserSummaries(anyList());
	}

	@Test
	@SuppressWarnings("unchecked")
	void getUserSummaries_ClientReturnsNoUsers_SkipsWriteBack() {
		// Non-empty ids, but user-service resolved none (every id absent): cacheUserSummaries must
		// short-circuit on the empty list, returning an empty map without any EVAL writeback.
		given(this.userClient.getUserSummaries(List.of(this.userId))).willReturn(Mono.just(List.of()));

		StepVerifier.create(this.userService.getUserSummaries(List.of(this.userId)))
			.assertNext(byId -> assertThat(byId).isEmpty())
			.verifyComplete();

		verify(this.redisTemplate, never()).execute(any(RedisScript.class), anyList(), anyList());
	}

}
