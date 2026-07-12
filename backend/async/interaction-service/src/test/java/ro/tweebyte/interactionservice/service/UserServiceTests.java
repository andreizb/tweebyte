/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import ro.tweebyte.interactionservice.client.UserClient;
import ro.tweebyte.interactionservice.exception.InteractionException;
import ro.tweebyte.interactionservice.model.UserDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
class UserServiceTests {

	@Mock
	private UserClient userClient;

	@Mock
	private RedisTemplate<String, byte[]> redisTemplate;

	@Mock
	private ValueOperations<String, byte[]> valueOperations;

	@InjectMocks
	private UserService userService;

	@BeforeEach
	void setUp() {
		// The batched write-back serializes with the shared ObjectMapper; inject a real one (and
		// the cache TTL) so getUserSummaries produces production-shaped bytes.
		ReflectionTestUtils.setField(this.userService, "objectMapper", new ObjectMapper().findAndRegisterModules());
		ReflectionTestUtils.setField(this.userService, "cacheTtl", Duration.ofSeconds(60));
	}

	@Test
	void testGetUserSummary() throws Exception {
		UUID userId = UUID.randomUUID();
		UserDto expected = new UserDto();

		given(this.userClient.getUserSummary(userId)).willReturn(CompletableFuture.completedFuture(expected));

		UserDto result = this.userService.getUserSummary(userId).get();

		assertThat(result).isEqualTo(expected);
		verify(this.userClient).getUserSummary(userId);
	}

	@Test
	@SuppressWarnings("unchecked")
	void getUserSummaries_BatchedFetch_WritesBackInOneEval() throws Exception {
		// The cold-fill resolves the ids in one downstream call and writes every summary back to
		// its users:: key in ONE EVAL round-trip (a SETEX-each script), not one put() per user.
		UUID firstId = UUID.randomUUID();
		UUID secondId = UUID.randomUUID();
		UserDto first = new UserDto();
		first.setId(firstId);
		first.setUserName("first");
		UserDto second = new UserDto();
		second.setId(secondId);
		second.setUserName("second");
		given(this.userClient.getUserSummaries(List.of(firstId, secondId)))
			.willReturn(CompletableFuture.completedFuture(List.of(first, second)));

		Map<UUID, UserDto> byId = this.userService.getUserSummaries(List.of(firstId, secondId)).get();

		assertThat(byId).containsEntry(firstId, first).containsEntry(secondId, second);
		// ONE EVAL writeback for both users (not a per-user put()): TTL arg + the two user blobs.
		verify(this.redisTemplate).execute(any(RedisScript.class), anyList(), any(byte[].class), any(byte[].class),
				any(byte[].class));
		verify(this.valueOperations, never()).set(any(), any(), any());
	}

	@Test
	void getUserSummaries_EmptyIds_ShortCircuits() throws Exception {
		assertThat(this.userService.getUserSummaries(List.of()).get()).isEmpty();

		verify(this.userClient, never()).getUserSummaries(anyList());
	}

	@Test
	@SuppressWarnings("unchecked")
	void getUserSummaries_ClientReturnsEmptyList_SkipsWriteback() throws Exception {
		// A non-empty id list whose downstream call resolves to NO users skips the EVAL
		// writeback entirely (cacheUserSummaries' empty-list early return) and yields an empty
		// map.
		UUID coldId = UUID.randomUUID();
		given(this.userClient.getUserSummaries(List.of(coldId)))
			.willReturn(CompletableFuture.completedFuture(List.of()));

		Map<UUID, UserDto> byId = this.userService.getUserSummaries(List.of(coldId)).get();

		assertThat(byId).isEmpty();
		verify(this.redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(byte[].class));
	}

	@Test
	void getUserSummaries_SerializerFails_propagatesInteractionException() {
		// A user whose JSON serialization fails wraps as InteractionException (the
		// JsonProcessingException arm of cacheUserSummaries).
		UUID id = UUID.randomUUID();
		UserDto user = new UserDto();
		user.setId(id);
		given(this.userClient.getUserSummaries(List.of(id)))
			.willReturn(CompletableFuture.completedFuture(List.of(user)));

		ObjectMapper throwingMapper = mock(ObjectMapper.class);
		try {
			given(throwingMapper.writeValueAsBytes(any())).willThrow(new JsonProcessingException("boom") {
			});
		}
		catch (JsonProcessingException ignored) {
			// declared on the stubbed method; never thrown by the stub setup itself.
		}
		ReflectionTestUtils.setField(this.userService, "objectMapper", throwingMapper);

		Throwable ex = catchThrowable(() -> this.userService.getUserSummaries(List.of(id)).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(InteractionException.class);
	}

	@Test
	void testGetUserSummaryClientError() {
		// Mirrors reactive UserServiceTest#getUserSummary_cacheMiss_clientError —
		// upstream client failure must propagate (no value cached, exception bubbles).
		UUID userId = UUID.randomUUID();
		RuntimeException clientError = new RuntimeException("Client error");

		given(this.userClient.getUserSummary(userId)).willReturn(CompletableFuture.failedFuture(clientError));

		Throwable thrown = catchThrowable(() -> this.userService.getUserSummary(userId).get());
		assertThat(thrown).isInstanceOf(ExecutionException.class);
		assertThat(thrown.getCause()).isEqualTo(clientError);
		verify(this.userClient).getUserSummary(userId);
	}

}
