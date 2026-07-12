/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import ro.tweebyte.tweetservice.client.UserClient;
import ro.tweebyte.tweetservice.model.UserDto;

@Service
@RequiredArgsConstructor
public class UserService {

	// Key prefix + TTL mirror async's @Cacheable(value = "userIds") surface, which Redis
	// writes as "userIds::<key>" with the cacheManager's 5-minute entryTtl. Both stacks
	// share the same key namespace and expiry so the tweet-side username->id cache behaves
	// symmetrically.
	private static final String USER_ID_KEY_PREFIX = "userIds::";

	private static final Duration CACHE_TTL = Duration.ofMinutes(5);

	private final UserClient userClient;

	private final ReactiveRedisTemplate<String, Object> redisTemplate;

	public Mono<UUID> getUserId(String userName) {
		String key = USER_ID_KEY_PREFIX + userName;
		return this.redisTemplate.opsForValue()
			.get(key)
			.map(value -> UUID.fromString(value.toString()))
			.switchIfEmpty(this.userClient.getUserSummary(userName)
				.map(UserDto::getId)
				.flatMap(userId -> this.redisTemplate.opsForValue().set(key, userId, CACHE_TTL).thenReturn(userId)));
	}

	// Live (uncached) username -> id resolution for the tweet-update write path. Unlike
	// getUserId it never reads or writes the userIds:: cache: a tweet edit rewrites the
	// mention every PUT, and resolving live mirrors the follow-create privacy read (write
	// paths read live state) so a persisted mention row never points at a stale cached id.
	// The user-service round-trip is the inter-service latency the reactive update path
	// overlaps. Error behaviour is identical to getUserId (same UserClient call).
	public Mono<UUID> getUserIdLive(String userName) {
		return this.userClient.getUserSummary(userName).map(UserDto::getId);
	}

	// Batched author resolution for tweet search: one POST resolves every distinct author id
	// on the page in a single round-trip, replacing the per-result getUserSummary(UUID) fan-out.
	// Deliberately uncached — the search author lookup is an HTTP batch call with no cache layer
	// — so it passes straight through to the client. A missing id is omitted from the result;
	// the caller reproduces the per-call not-found by detecting the absent id.
	public Mono<List<UserDto>> getUserSummaries(List<UUID> userIds) {
		return this.userClient.getUserSummaries(userIds);
	}

	public Mono<Boolean> mediaExists(UUID mediaId) {
		return this.userClient.mediaExists(mediaId);
	}

}
