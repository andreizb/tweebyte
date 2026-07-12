/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import ro.tweebyte.interactionservice.client.UserClient;
import ro.tweebyte.interactionservice.exception.InteractionException;
import ro.tweebyte.interactionservice.model.UserDto;

@Service
@RequiredArgsConstructor
public class UserService {

	private static final String USERS_CACHE = "users";

	// Spring's RedisCache keys the users cache as users::<id>; the batched write-back below targets
	// the same key the @Cacheable read populates so a subsequent MGET (or single read) hits.
	private static final String USER_SUMMARY_KEY_PREFIX = USERS_CACHE + "::";

	// SETEX every KEYS[i] to ARGV[i+1] with the shared TTL in ARGV[1] in one EVAL — the cold-fill
	// write-back for getUserSummaries. Mirrors CountCache.FILL_MISSES: an ordinary command over
	// Lettuce's shared connection (not executePipelined, which churns dedicated connections), and
	// the only MSET form that keeps a per-key TTL.
	private static final String FILL_MISSES = """
			for i = 1, #KEYS do
			redis.call('SETEX', KEYS[i], ARGV[1], ARGV[i + 1])
			end
			return #KEYS""";

	// Matches the users RedisCache TTL (spring.cache.redis.time-to-live) so the batched write-back
	// expires on the same schedule as a @Cacheable put.
	@Value("${spring.cache.redis.time-to-live}")
	private Duration cacheTtl;

	private final UserClient userClient;

	private final RedisTemplate<String, byte[]> redisTemplate;

	// The shared auto-configured ObjectMapper — the same instance the users RedisCache serializer
	// is built from, so the bytes written here are identical to a @Cacheable put and round-trip
	// through the single read unchanged.
	private final ObjectMapper objectMapper;

	private final RedisScript<Long> fillMissesScript = new DefaultRedisScript<>(FILL_MISSES, Long.class);

	@Cacheable(value = USERS_CACHE, key = "#userId", unless = "#result == null")
	public CompletableFuture<UserDto> getUserSummary(UUID userId) {
		return this.userClient.getUserSummary(userId);
	}

	// Batched read-through for the following-cache miss-path fill: the cold ids resolve in ONE
	// downstream call (getUserSummaries), and every fetched summary is written back to its users::
	// key in ONE round-trip — a SETEX-each EVAL over the shared connection, so a cold fill of N
	// users costs one Redis write instead of N. Same key, serializer and TTL as the single
	// read-through, so a subsequent fill's MGET hits. Returns an id-keyed map; an id user-service
	// didn't return is absent, leaving the caller to fall back per-id.
	public CompletableFuture<Map<UUID, UserDto>> getUserSummaries(List<UUID> userIds) {
		if (userIds.isEmpty()) {
			return CompletableFuture.completedFuture(Map.of());
		}
		return this.userClient.getUserSummaries(userIds).thenApply(users -> {
			cacheUserSummaries(users);
			Map<UUID, UserDto> byId = new LinkedHashMap<>();
			for (UserDto user : users) {
				byId.put(user.getId(), user);
			}
			return byId;
		});
	}

	// One EVAL SETEX per resolved user: KEYS[i] = users::<id>, ARGV[1] = TTL seconds, ARGV[i+1] =
	// the user's JSON. Serialized with the shared ObjectMapper so the bytes match a @Cacheable put.
	private void cacheUserSummaries(List<UserDto> users) {
		if (users.isEmpty()) {
			return;
		}
		List<String> keys = new ArrayList<>(users.size());
		List<Object> args = new ArrayList<>(users.size() + 1);
		args.add(Long.toString(this.cacheTtl.toSeconds()).getBytes(StandardCharsets.UTF_8));
		try {
			for (UserDto user : users) {
				keys.add(USER_SUMMARY_KEY_PREFIX + user.getId());
				args.add(this.objectMapper.writeValueAsBytes(user));
			}
		}
		catch (JsonProcessingException ex) {
			throw new InteractionException(ex);
		}
		this.redisTemplate.execute(this.fillMissesScript, keys, args.toArray());
	}

	public CompletableFuture<UserDto> fetchUserSummary(UUID userId) {
		return this.userClient.getUserSummary(userId);
	}

	public CompletableFuture<Boolean> mediaExists(UUID mediaId) {
		return this.userClient.mediaExists(mediaId);
	}

}
