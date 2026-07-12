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

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import ro.tweebyte.interactionservice.cache.CacheReads;
import ro.tweebyte.interactionservice.client.UserClient;
import ro.tweebyte.interactionservice.model.UserDto;

@Service
@RequiredArgsConstructor
public class UserService {

	private static final String USER_SUMMARY_KEY_PREFIX = "users::";

	// SETEX every KEYS[i] to ARGV[i+1] with the shared TTL in ARGV[1] in one EVAL — the cold-fill
	// write-back for getUserSummaries. Mirrors CountCache.FILL_MISSES: an ordinary command over
	// Lettuce's shared connection (not a dedicated-connection pipeline), and the only MSET form
	// that keeps a per-key TTL.
	private static final String FILL_MISSES = "for i = 1, #KEYS do\n"
			+ "  redis.call('SETEX', KEYS[i], ARGV[1], ARGV[i + 1])\n" + "end\n" + "return #KEYS";

	@Value("${spring.cache.redis.time-to-live}")
	private Duration cacheTtl;

	private final UserClient userClient;

	private final ReactiveRedisTemplate<String, byte[]> redisTemplate;

	// register JSR310 module (LocalDateTime support). See TweetService note.
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	private final RedisScript<Long> fillMissesScript = RedisScript.of(FILL_MISSES, Long.class);

	public Mono<UserDto> getUserSummary(UUID userId) {
		String key = USER_SUMMARY_KEY_PREFIX + userId;
		return CacheReads.offload(this.redisTemplate.opsForValue().get(key)).flatMap(bytes -> {
			try {
				return Mono.just(this.objectMapper.readValue(bytes, UserDto.class));
			}
			catch (Exception ex) {
				return Mono.error(ex);
			}
		}).switchIfEmpty(this.userClient.getUserSummary(userId).flatMap(userDto -> {
			try {
				byte[] bytes = this.objectMapper.writeValueAsBytes(userDto);
				return this.redisTemplate.opsForValue().set(key, bytes, this.cacheTtl).thenReturn(userDto);
			}
			catch (Exception ex) {
				return Mono.error(ex);
			}
		}));
	}

	// Batched read-through for the following-cache miss-path fill: the cold ids resolve in ONE
	// downstream call (getUserSummaries), and every fetched summary is written back to its users::
	// key in ONE round-trip — a SETEX-each EVAL over the shared connection, so a cold fill of N
	// users costs one Redis write instead of N. Same key and TTL as the single read-through, so a
	// subsequent fill's MGET hits. Returns an id-keyed map; an id user-service didn't return is
	// absent, leaving the caller to fall back per-id.
	public Mono<Map<UUID, UserDto>> getUserSummaries(List<UUID> userIds) {
		if (userIds.isEmpty()) {
			return Mono.just(Map.of());
		}
		return this.userClient.getUserSummaries(userIds).flatMap(users -> {
			Map<UUID, UserDto> byId = new LinkedHashMap<>();
			for (UserDto userDto : users) {
				byId.put(userDto.getId(), userDto);
			}
			return cacheUserSummaries(users).thenReturn(byId);
		});
	}

	// One EVAL SETEX per resolved user: KEYS[i] = users::<id>, ARGV[1] = TTL seconds, ARGV[i+1] =
	// the user's JSON. Plain MSET cannot carry a per-key TTL, so the SETEX-loop script keeps the
	// 60s expiry while collapsing the N writes onto Lettuce's shared connection.
	private Mono<Void> cacheUserSummaries(List<UserDto> users) {
		if (users.isEmpty()) {
			return Mono.empty();
		}
		List<String> keys = new ArrayList<>(users.size());
		List<Object> args = new ArrayList<>(users.size() + 1);
		args.add(encodeTtl());
		try {
			for (UserDto userDto : users) {
				keys.add(USER_SUMMARY_KEY_PREFIX + userDto.getId());
				args.add(this.objectMapper.writeValueAsBytes(userDto));
			}
		}
		catch (Exception ex) {
			return Mono.error(ex);
		}
		return this.redisTemplate.execute(this.fillMissesScript, keys, args).then();
	}

	private byte[] encodeTtl() {
		return Long.toString(this.cacheTtl.toSeconds()).getBytes(StandardCharsets.UTF_8);
	}

	public Mono<UserDto> fetchUserSummary(UUID userId) {
		return this.userClient.getUserSummary(userId);
	}

	public Mono<Boolean> mediaExists(UUID mediaId) {
		return this.userClient.mediaExists(mediaId);
	}

}
