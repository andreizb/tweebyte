/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.cache;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit suite for {@link CountCache#getPair} — the collapsed follower+following read: ONE
 * MGET for both keys, and on a miss ONE combined DB load + ONE EVAL writeback (3 ops instead
 * of 2 GET + 2 COUNT + 2 SET). Exercises the both-hit short-circuit, the both-miss load +
 * writeback, each partial-hit arm (only the missing key is written), and the defensive
 * null-multiGet path (treated as both missing). Also covers getAllMulti's null-multiGet
 * branch.
 *
 * @author Andrei Zbarcea
 */
class CountCachePairTests {

	private RedisTemplate<String, byte[]> redisTemplate;

	private ValueOperations<String, byte[]> valueOperations;

	private CountCache countCache;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		this.redisTemplate = mock(RedisTemplate.class);
		this.valueOperations = mock(ValueOperations.class);
		given(this.redisTemplate.opsForValue()).willReturn(this.valueOperations);
		this.countCache = new CountCache(this.redisTemplate, Duration.ofSeconds(60));
	}

	private static byte[] encode(long value) {
		return Long.toString(value).getBytes(StandardCharsets.UTF_8);
	}

	@Test
	void getPair_BothHit_ReturnsCachedWithoutLoading() {
		String keyA = "followers_count::user";
		String keyB = "following_count::user";
		given(this.valueOperations.multiGet(List.of(keyA, keyB))).willReturn(List.of(encode(12L), encode(34L)));

		Supplier<long[]> loader = () -> {
			throw new AssertionError("loader must not run when both keys hit");
		};

		assertThat(this.countCache.getPair(keyA, keyB, loader)).containsExactly(12L, 34L);
		// Neither key is re-written on a full hit.
		verify(this.redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(byte[].class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void getPair_BothMiss_LoadsBothAndWritesBackInOneEval() {
		String keyA = "followers_count::user";
		String keyB = "following_count::user";
		// Both miss: one null, one empty-byte.
		given(this.valueOperations.multiGet(List.of(keyA, keyB))).willReturn(Arrays.asList(null, new byte[0]));

		long[] result = this.countCache.getPair(keyA, keyB, () -> new long[] { 7L, 9L });

		assertThat(result).containsExactly(7L, 9L);
		// Both misses write back in ONE EVAL round-trip: TTL arg + two value args.
		verify(this.redisTemplate).execute(any(RedisScript.class), anyList(), any(byte[].class), any(byte[].class),
				any(byte[].class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void getPair_OnlyFirstHit_WritesBackOnlySecond() {
		String keyA = "followers_count::user";
		String keyB = "following_count::user";
		// A hits from cache, B misses — the cached A is kept, only B is loaded + written.
		given(this.valueOperations.multiGet(List.of(keyA, keyB))).willReturn(Arrays.asList(encode(50L), null));

		long[] result = this.countCache.getPair(keyA, keyB, () -> new long[] { -1L, 88L });

		// A keeps its cached value; B takes the loaded value.
		assertThat(result).containsExactly(50L, 88L);
		// One miss (B) written back: TTL arg + one value arg.
		verify(this.redisTemplate).execute(any(RedisScript.class), anyList(), any(byte[].class), any(byte[].class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void getPair_OnlySecondHit_WritesBackOnlyFirst() {
		String keyA = "followers_count::user";
		String keyB = "following_count::user";
		// B hits, A misses (empty-byte) — only A is loaded + written.
		given(this.valueOperations.multiGet(List.of(keyA, keyB))).willReturn(Arrays.asList(new byte[0], encode(60L)));

		long[] result = this.countCache.getPair(keyA, keyB, () -> new long[] { 77L, -1L });

		assertThat(result).containsExactly(77L, 60L);
		verify(this.redisTemplate).execute(any(RedisScript.class), anyList(), any(byte[].class), any(byte[].class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void getPair_NullMultiGet_TreatedAsBothMissing() {
		String keyA = "followers_count::user";
		String keyB = "following_count::user";
		// A null multiGet payload is defensively treated as both keys missing.
		given(this.valueOperations.multiGet(List.of(keyA, keyB))).willReturn(null);

		long[] result = this.countCache.getPair(keyA, keyB, () -> new long[] { 3L, 4L });

		assertThat(result).containsExactly(3L, 4L);
		verify(this.redisTemplate).execute(any(RedisScript.class), anyList(), any(byte[].class), any(byte[].class),
				any(byte[].class));
	}

	@Test
	void getAllMulti_NullMultiGet_TreatedAsAllMisses() {
		// A null multiGet over the family-major key list is treated as every key missing, so each
		// family runs its loader and the misses write back in one EVAL.
		UUID idOne = UUID.randomUUID();
		Function<UUID, String> likeKey = id -> "like_count::" + id;
		given(this.valueOperations.multiGet(List.of(likeKey.apply(idOne)))).willReturn(null);

		var result = this.countCache.getAllMulti(List.of(idOne), List.of(likeKey),
				List.of(misses -> java.util.Map.of(idOne, 5L)));

		assertThat(result.get(0)).containsEntry(idOne, 5L);
	}

}
