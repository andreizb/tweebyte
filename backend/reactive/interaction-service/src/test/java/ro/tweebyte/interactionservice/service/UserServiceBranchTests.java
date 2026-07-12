/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.interactionservice.client.UserClient;
import ro.tweebyte.interactionservice.model.UserDto;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;

/**
 * Coverage for the UserService cache-hit and cache-hit-error branches that
 * UserServiceTests never exercises (it only drives the cache-miss path). Targets lines
 * 28–32: the .flatMap(bytes -> objectMapper.readValue(...)) branch and its catch block.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceBranchTests {

	@Mock
	private UserClient userClient;

	@Mock
	private ReactiveRedisTemplate<String, byte[]> redisTemplate;

	@Mock
	private ReactiveValueOperations<String, byte[]> valueOps;

	@InjectMocks
	private UserService userService;

	private final UUID userId = UUID.randomUUID();

	private final String redisKey = "users::" + this.userId;

	@BeforeEach
	void setUp() {
		lenient().when(this.redisTemplate.opsForValue()).thenReturn(this.valueOps);
		// Defensive stub: switchIfEmpty(...) builds its inner pipeline eagerly,
		// so UserClient.getUserSummary is invoked at construction time even
		// when the cache hit short-circuits the outer Mono.
		lenient().when(this.userClient.getUserSummary(this.userId)).thenReturn(Mono.empty());
	}

	@Test
	void getUserSummary_CacheHit_DeserializesFromRedis() throws Exception {
		// Drives the cache-hit flatMap on getUserSummary: cached bytes
		// round-trip through the ObjectMapper and emit the UserDto without
		// ever calling UserClient.getUserSummary.
		UserDto cached = new UserDto();
		cached.setId(this.userId);
		cached.setUserName("cached-user");
		byte[] payload = new ObjectMapper().findAndRegisterModules().writeValueAsBytes(cached);

		given(this.valueOps.get(this.redisKey)).willReturn(Mono.just(payload));

		StepVerifier.create(this.userService.getUserSummary(this.userId))
			.expectNextMatches(u -> this.userId.equals(u.getId()) && "cached-user".equals(u.getUserName()))
			.verifyComplete();
	}

	@Test
	void getUserSummary_CacheHit_CorruptBytes_ErrorsOut() {
		// Drives the catch arm on getUserSummary: malformed cached bytes
		// surface a Mono.error so the caller observes the JsonProcessingException
		// wrapped in a RuntimeException-style failure signal.
		given(this.valueOps.get(this.redisKey)).willReturn(Mono.just("not-json".getBytes()));

		StepVerifier.create(this.userService.getUserSummary(this.userId)).expectError().verify();
	}

	@Test
	void getUserSummary_CacheMiss_WriteBackSerializationFails_ErrorsOut() throws Exception {
		// Drives the write-back catch arm: the cache misses, the client resolves the user, but
		// serialising it for the SET fails — the error must surface instead of caching.
		ReflectionTestUtils.setField(this.userService, "cacheTtl", Duration.ofSeconds(60));
		installThrowingMapper();

		UserDto resolved = new UserDto();
		resolved.setId(this.userId);
		given(this.valueOps.get(this.redisKey)).willReturn(Mono.empty());
		given(this.userClient.getUserSummary(this.userId)).willReturn(Mono.just(resolved));

		StepVerifier.create(this.userService.getUserSummary(this.userId))
			.expectError(JsonProcessingException.class)
			.verify();
	}

	@Test
	void getUserSummaries_WriteBackSerializationFails_ErrorsOut() throws Exception {
		// Drives the cacheUserSummaries catch arm: a resolved batch whose JSON serialisation throws
		// must surface the error rather than silently skip the write-back.
		ReflectionTestUtils.setField(this.userService, "cacheTtl", Duration.ofSeconds(60));
		installThrowingMapper();

		UserDto resolved = new UserDto();
		resolved.setId(this.userId);
		given(this.userClient.getUserSummaries(List.of(this.userId))).willReturn(Mono.just(List.of(resolved)));

		StepVerifier.create(this.userService.getUserSummaries(List.of(this.userId)))
			.expectError(JsonProcessingException.class)
			.verify();
	}

	// Swap in an ObjectMapper whose writeValueAsBytes always throws, so the serialization catch
	// arms are reached. JsonProcessingException's constructor is protected, hence the subclass.
	private void installThrowingMapper() throws JsonProcessingException {
		JsonProcessingException failure = new JsonProcessingException("boom") {
		};
		ObjectMapper throwingMapper = spy(new ObjectMapper().findAndRegisterModules());
		willThrow(failure).given(throwingMapper).writeValueAsBytes(any());
		ReflectionTestUtils.setField(this.userService, "objectMapper", throwingMapper);
	}

}
