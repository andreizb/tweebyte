/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.cache;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit suite for {@link CountCache} — exercises the read-through hit/miss branches, the
 * empty-value filter, batched multi-get with partial misses (including zeros the GROUP BY
 * omits), the empty/null id short-circuit, and the conditional increment/decrement script.
 *
 * @author Andrei Zbarcea
 */
class CountCacheTests {

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
	void get_CacheHit_ReturnsDecodedValueWithoutLoading() {
		String key = "reply_count::tweet";
		given(this.valueOperations.get(key)).willReturn(encode(42L));

		LongSupplier loader = () -> {
			throw new AssertionError("loader must not run on a cache hit");
		};

		assertThat(this.countCache.get(key, loader)).isEqualTo(42L);

		verify(this.valueOperations, never()).set(any(), any(), any());
	}

	@Test
	void get_CacheMiss_LoadsFromDatabaseAndSeeds() {
		String key = "reply_count::tweet";
		given(this.valueOperations.get(key)).willReturn(null);

		assertThat(this.countCache.get(key, () -> 7L)).isEqualTo(7L);

		verify(this.valueOperations).set(eq(key), any(byte[].class), eq(Duration.ofSeconds(60)));
	}

	@Test
	void get_EmptyCachedBytes_TreatedAsMiss() {
		// A zero-length cached value is filtered out (bytes.length > 0 is false), so the
		// loader still runs and reseeds the key.
		String key = "reply_count::tweet";
		given(this.valueOperations.get(key)).willReturn(new byte[0]);

		assertThat(this.countCache.get(key, () -> 3L)).isEqualTo(3L);

		verify(this.valueOperations).set(eq(key), any(byte[].class), eq(Duration.ofSeconds(60)));
	}

	@Test
	void getAll_NullIds_ReturnsEmptyMap() {
		assertThat(this.countCache.getAll(null, id -> "k::" + id, ids -> Map.of())).isEmpty();

		verify(this.valueOperations, never()).multiGet(anyList());
	}

	@Test
	void getAll_EmptyIds_ReturnsEmptyMap() {
		assertThat(this.countCache.getAll(List.of(), id -> "k::" + id, ids -> Map.of())).isEmpty();

		verify(this.valueOperations, never()).multiGet(anyList());
	}

	@Test
	void getAll_AllHits_DoesNotInvokeLoader() {
		UUID idOne = UUID.randomUUID();
		UUID idTwo = UUID.randomUUID();
		Function<UUID, String> keyFor = id -> "reply_count::" + id;
		given(this.valueOperations.multiGet(List.of(keyFor.apply(idOne), keyFor.apply(idTwo))))
			.willReturn(List.of(encode(5L), encode(9L)));

		Function<List<UUID>, Map<UUID, Long>> loader = ids -> {
			throw new AssertionError("loader must not run when all hit");
		};

		assertThat(this.countCache.getAll(List.of(idOne, idTwo), keyFor, loader)).containsEntry(idOne, 5L)
			.containsEntry(idTwo, 9L);

		verify(this.valueOperations, never()).set(any(), any(), any());
	}

	@Test
	void getAll_PartialMisses_LoadsAndWritesBackIncludingZeros() {
		UUID hit = UUID.randomUUID();
		UUID missPresent = UUID.randomUUID();
		UUID missZero = UUID.randomUUID();
		Function<UUID, String> keyFor = id -> "reply_count::" + id;
		List<UUID> ids = List.of(hit, missPresent, missZero);
		// hit -> cached value; the other two miss: one null, one empty-byte.
		given(this.valueOperations.multiGet(ids.stream().map(keyFor).toList()))
			.willReturn(Arrays.asList(encode(4L), null, new byte[0]));

		// The loader only returns the genuinely-present miss; missZero is omitted by the
		// GROUP BY and must be materialised as 0.
		Function<List<UUID>, Map<UUID, Long>> loader = misses -> {
			assertThat(misses).containsExactlyInAnyOrder(missPresent, missZero);
			return Map.of(missPresent, 11L);
		};

		assertThat(this.countCache.getAll(ids, keyFor, loader)).containsEntry(hit, 4L)
			.containsEntry(missPresent, 11L)
			.containsEntry(missZero, 0L);

		// The two misses (missPresent=11, missZero=0) are written back in ONE EVAL round-trip
		// over the shared connection (the hit is not re-written): TTL arg + 2 value args.
		verify(this.redisTemplate).execute(any(RedisScript.class), anyList(), any(byte[].class), any(byte[].class),
				any(byte[].class));
	}

	@Test
	void getAll_AllCachedZero_NoMissesNoWrites() {
		// Every id resolves from cache, so misses is empty and the early return fires.
		UUID idOne = UUID.randomUUID();
		Function<UUID, String> keyFor = id -> "reply_count::" + id;
		given(this.valueOperations.multiGet(List.of(keyFor.apply(idOne)))).willReturn(List.of(encode(0L)));

		Function<List<UUID>, Map<UUID, Long>> loader = ids -> {
			throw new AssertionError("no loader expected");
		};

		assertThat(this.countCache.getAll(List.of(idOne), keyFor, loader)).containsEntry(idOne, 0L);
		verify(this.valueOperations, never()).set(any(), any(), any());
	}

	@Test
	void getAll_NullMultiGetResult_TreatedAsAllMisses() {
		// A null multiGet payload is defensively treated as every id missing, so the loader
		// runs and the misses are written back.
		UUID idOne = UUID.randomUUID();
		Function<UUID, String> keyFor = id -> "reply_count::" + id;
		given(this.valueOperations.multiGet(List.of(keyFor.apply(idOne)))).willReturn(null);

		assertThat(this.countCache.getAll(List.of(idOne), keyFor, ids -> Map.of(idOne, 2L))).containsEntry(idOne, 2L);
		// One miss → ONE EVAL round-trip: TTL arg + 1 value arg.
		verify(this.redisTemplate).execute(any(RedisScript.class), anyList(), any(byte[].class), any(byte[].class));
	}

	@Test
	void getAllMulti_EmptyIds_ReturnsEmptyMapPerFamily() {
		List<Map<UUID, Long>> result = this.countCache.getAllMulti(List.of(),
				List.of(id -> "a:" + id, id -> "b:" + id), List.of(ids -> Map.of(), ids -> Map.of()));

		assertThat(result).hasSize(2);
		assertThat(result.get(0)).isEmpty();
		assertThat(result.get(1)).isEmpty();
		verify(this.valueOperations, never()).multiGet(anyList());
	}

	@Test
	void getAllMulti_AllHits_NoLoaderNoWriteback() {
		UUID idOne = UUID.randomUUID();
		UUID idTwo = UUID.randomUUID();
		Function<UUID, String> likeKey = id -> "like_count::" + id;
		Function<UUID, String> replyKey = id -> "reply_count::" + id;
		// Family-major key order: like(id1, id2) then reply(id1, id2) — all cached.
		given(this.valueOperations.multiGet(
				List.of(likeKey.apply(idOne), likeKey.apply(idTwo), replyKey.apply(idOne), replyKey.apply(idTwo))))
			.willReturn(List.of(encode(5L), encode(6L), encode(7L), encode(8L)));

		Function<List<UUID>, Map<UUID, Long>> failLoader = ids -> {
			throw new AssertionError("loader must not run when all families hit");
		};

		List<Map<UUID, Long>> result = this.countCache.getAllMulti(List.of(idOne, idTwo), List.of(likeKey, replyKey),
				List.of(failLoader, failLoader));

		assertThat(result.get(0)).containsEntry(idOne, 5L).containsEntry(idTwo, 6L);
		assertThat(result.get(1)).containsEntry(idOne, 7L).containsEntry(idTwo, 8L);
		verify(this.redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(byte[].class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void getAllMulti_PerFamilyPartialMisses_LoadsPerFamilyAndOneEvalWriteback() {
		UUID idOne = UUID.randomUUID();
		UUID idTwo = UUID.randomUUID();
		Function<UUID, String> likeKey = id -> "like_count::" + id;
		Function<UUID, String> replyKey = id -> "reply_count::" + id;
		Function<UUID, String> retweetKey = id -> "retweet_count::" + id;
		// Independent per-family hit/miss for the SAME ids: like misses id2, reply misses id1,
		// retweet hits both. Exercises the partition + the family with no misses skipping its loader.
		given(this.valueOperations.multiGet(List.of(likeKey.apply(idOne), likeKey.apply(idTwo), replyKey.apply(idOne),
				replyKey.apply(idTwo), retweetKey.apply(idOne), retweetKey.apply(idTwo))))
			.willReturn(Arrays.asList(encode(5L), null, null, encode(9L), encode(1L), encode(2L)));

		Function<List<UUID>, Map<UUID, Long>> likeLoader = misses -> {
			assertThat(misses).containsExactly(idTwo);
			return Map.of(idTwo, 50L);
		};
		Function<List<UUID>, Map<UUID, Long>> replyLoader = misses -> {
			assertThat(misses).containsExactly(idOne);
			return Map.of(idOne, 60L);
		};
		Function<List<UUID>, Map<UUID, Long>> retweetLoader = misses -> {
			throw new AssertionError("retweet loader must not run — both ids hit");
		};

		List<Map<UUID, Long>> result = this.countCache.getAllMulti(List.of(idOne, idTwo),
				List.of(likeKey, replyKey, retweetKey), List.of(likeLoader, replyLoader, retweetLoader));

		assertThat(result.get(0)).containsEntry(idOne, 5L).containsEntry(idTwo, 50L);
		assertThat(result.get(1)).containsEntry(idOne, 60L).containsEntry(idTwo, 9L);
		assertThat(result.get(2)).containsEntry(idOne, 1L).containsEntry(idTwo, 2L);

		// Both misses (like::id2 and reply::id1) write back in ONE EVAL round-trip: TTL arg + 2 values.
		verify(this.redisTemplate).execute(any(RedisScript.class), anyList(), any(byte[].class), any(byte[].class),
				any(byte[].class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void increment_RunsConditionalScriptWithPositiveDelta() {
		String key = "followers_count::user";

		this.countCache.increment(key);

		verify(this.redisTemplate).execute(any(RedisScript.class), eq(List.of(key)), any(byte[].class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void decrement_RunsConditionalScriptWithNegativeDelta() {
		String key = "followers_count::user";

		this.countCache.decrement(key);

		verify(this.redisTemplate).execute(any(RedisScript.class), eq(List.of(key)), any(byte[].class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void incrementBoth_AppliesPositiveDeltaToBothKeysInOneRoundTrip() {
		String keyA = "following_count::actor";
		String keyB = "followers_count::target";

		this.countCache.incrementBoth(keyA, keyB);

		// ONE EVAL over both keys instead of two serial INCRs.
		verify(this.redisTemplate).execute(any(RedisScript.class), eq(List.of(keyA, keyB)), any(byte[].class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void decrementBoth_AppliesNegativeDeltaToBothKeysInOneRoundTrip() {
		String keyA = "following_count::actor";
		String keyB = "followers_count::target";

		this.countCache.decrementBoth(keyA, keyB);

		verify(this.redisTemplate).execute(any(RedisScript.class), eq(List.of(keyA, keyB)), any(byte[].class));
	}

}
