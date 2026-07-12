/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.cache;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

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
 */
class CountCacheTests {

	private ReactiveRedisTemplate<String, byte[]> redisTemplate;

	private ReactiveValueOperations<String, byte[]> valueOperations;

	private CountCache countCache;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		this.redisTemplate = mock(ReactiveRedisTemplate.class);
		this.valueOperations = mock(ReactiveValueOperations.class);
		given(this.redisTemplate.opsForValue()).willReturn(this.valueOperations);
		this.countCache = new CountCache(this.redisTemplate, Duration.ofSeconds(60));
	}

	private static byte[] encode(long value) {
		return Long.toString(value).getBytes(StandardCharsets.UTF_8);
	}

	@Test
	void get_CacheHit_ReturnsDecodedValueWithoutLoading() {
		String key = "reply_count::tweet";
		given(this.valueOperations.get(key)).willReturn(Mono.just(encode(42L)));

		Mono<Long> loader = Mono.error(new AssertionError("loader must not run on a cache hit"));

		StepVerifier.create(this.countCache.get(key, loader)).expectNext(42L).verifyComplete();

		verify(this.valueOperations, never()).set(any(), any(), any());
	}

	@Test
	void get_CacheMiss_LoadsFromDatabaseAndSeeds() {
		String key = "reply_count::tweet";
		given(this.valueOperations.get(key)).willReturn(Mono.empty());
		given(this.valueOperations.set(eq(key), any(byte[].class), eq(Duration.ofSeconds(60))))
			.willReturn(Mono.just(true));

		StepVerifier.create(this.countCache.get(key, Mono.just(7L))).expectNext(7L).verifyComplete();

		verify(this.valueOperations).set(eq(key), any(byte[].class), eq(Duration.ofSeconds(60)));
	}

	@Test
	void get_EmptyCachedBytes_TreatedAsMiss() {
		// A zero-length cached value is filtered out (bytes.length > 0 is false), so the
		// loader still runs and reseeds the key.
		String key = "reply_count::tweet";
		given(this.valueOperations.get(key)).willReturn(Mono.just(new byte[0]));
		given(this.valueOperations.set(eq(key), any(byte[].class), eq(Duration.ofSeconds(60))))
			.willReturn(Mono.just(true));

		StepVerifier.create(this.countCache.get(key, Mono.just(3L))).expectNext(3L).verifyComplete();

		verify(this.valueOperations).set(eq(key), any(byte[].class), eq(Duration.ofSeconds(60)));
	}

	@Test
	void getAll_NullIds_ReturnsEmptyMap() {
		StepVerifier.create(this.countCache.getAll(null, id -> "k::" + id, ids -> Mono.just(Map.of())))
			.expectNext(Map.of())
			.verifyComplete();

		verify(this.valueOperations, never()).multiGet(anyList());
	}

	@Test
	void getAll_EmptyIds_ReturnsEmptyMap() {
		StepVerifier.create(this.countCache.getAll(List.of(), id -> "k::" + id, ids -> Mono.just(Map.of())))
			.expectNext(Map.of())
			.verifyComplete();

		verify(this.valueOperations, never()).multiGet(anyList());
	}

	@Test
	void getAll_AllHits_DoesNotInvokeLoader() {
		UUID idOne = UUID.randomUUID();
		UUID idTwo = UUID.randomUUID();
		Function<UUID, String> keyFor = id -> "reply_count::" + id;
		given(this.valueOperations.multiGet(List.of(keyFor.apply(idOne), keyFor.apply(idTwo))))
			.willReturn(Mono.just(List.of(encode(5L), encode(9L))));

		Function<List<UUID>, Mono<Map<UUID, Long>>> loader = ids -> Mono
			.error(new AssertionError("loader must not run when all hit"));

		StepVerifier.create(this.countCache.getAll(List.of(idOne, idTwo), keyFor, loader))
			.assertNext(counts -> assertThat(counts).containsEntry(idOne, 5L).containsEntry(idTwo, 9L))
			.verifyComplete();

		verify(this.valueOperations, never()).set(any(), any(), any());
	}

	@Test
	@SuppressWarnings("unchecked")
	void getAll_PartialMisses_LoadsAndWritesBackIncludingZeros() {
		UUID hit = UUID.randomUUID();
		UUID missPresent = UUID.randomUUID();
		UUID missZero = UUID.randomUUID();
		Function<UUID, String> keyFor = id -> "reply_count::" + id;
		List<UUID> ids = List.of(hit, missPresent, missZero);
		// hit -> cached value; the other two miss: one null, one empty-byte.
		given(this.valueOperations.multiGet(ids.stream().map(keyFor).toList()))
			.willReturn(Mono.just(java.util.Arrays.asList(encode(4L), null, new byte[0])));
		// The loader only returns the genuinely-present miss; missZero is omitted by the
		// GROUP BY and must be materialised as 0.
		given(this.redisTemplate.execute(any(RedisScript.class), anyList(), anyList())).willReturn(Flux.just(2L));

		Function<List<UUID>, Mono<Map<UUID, Long>>> loader = misses -> {
			assertThat(misses).containsExactlyInAnyOrder(missPresent, missZero);
			return Mono.just(Map.of(missPresent, 11L));
		};

		StepVerifier.create(this.countCache.getAll(ids, keyFor, loader))
			.assertNext(counts -> assertThat(counts).containsEntry(hit, 4L)
				.containsEntry(missPresent, 11L)
				.containsEntry(missZero, 0L))
			.verifyComplete();

		// Two write-backs in ONE EVAL round-trip: TTL arg + 2 value args.
		verify(this.redisTemplate).execute(any(RedisScript.class), anyList(), anyList());
	}

	@Test
	void getAllMulti_NullIds_ReturnsEmptyMapPerFamily() {
		// Branch: ids == null → first condition of OR is true → short-circuit → returns empties
		StepVerifier
			.create(this.countCache.getAllMulti(null, List.of(id -> "a:" + id, id -> "b:" + id),
					List.of(ids -> Mono.just(Map.of()), ids -> Mono.just(Map.of()))))
			.assertNext(result -> {
				assertThat(result).hasSize(2);
				assertThat(result.get(0)).isEmpty();
				assertThat(result.get(1)).isEmpty();
			})
			.verifyComplete();
	}

	@Test
	void getAllMulti_EmptyIds_ReturnsEmptyMapPerFamily() {
		StepVerifier
			.create(this.countCache.getAllMulti(List.of(), List.of(id -> "a:" + id, id -> "b:" + id),
					List.of(ids -> Mono.just(Map.of()), ids -> Mono.just(Map.of()))))
			.assertNext(result -> {
				assertThat(result).hasSize(2);
				assertThat(result.get(0)).isEmpty();
				assertThat(result.get(1)).isEmpty();
			})
			.verifyComplete();

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
			.willReturn(Mono.just(List.of(encode(5L), encode(6L), encode(7L), encode(8L))));

		Function<List<UUID>, Mono<Map<UUID, Long>>> failLoader = ids -> Mono
			.error(new AssertionError("loader must not run when all families hit"));

		StepVerifier
			.create(this.countCache.getAllMulti(List.of(idOne, idTwo), List.of(likeKey, replyKey),
					List.of(failLoader, failLoader)))
			.assertNext(result -> {
				assertThat(result.get(0)).containsEntry(idOne, 5L).containsEntry(idTwo, 6L);
				assertThat(result.get(1)).containsEntry(idOne, 7L).containsEntry(idTwo, 8L);
			})
			.verifyComplete();

		verify(this.redisTemplate, never()).execute(any(RedisScript.class), anyList(), anyList());
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
		given(this.valueOperations
			.multiGet(List.of(likeKey.apply(idOne), likeKey.apply(idTwo), replyKey.apply(idOne), replyKey.apply(idTwo),
					retweetKey.apply(idOne), retweetKey.apply(idTwo))))
			.willReturn(Mono.just(java.util.Arrays.asList(encode(5L), null, null, encode(9L), encode(1L), encode(2L))));
		given(this.redisTemplate.execute(any(RedisScript.class), anyList(), anyList())).willReturn(Flux.just(2L));

		Function<List<UUID>, Mono<Map<UUID, Long>>> likeLoader = misses -> {
			assertThat(misses).containsExactly(idTwo);
			return Mono.just(Map.of(idTwo, 50L));
		};
		Function<List<UUID>, Mono<Map<UUID, Long>>> replyLoader = misses -> {
			assertThat(misses).containsExactly(idOne);
			return Mono.just(Map.of(idOne, 60L));
		};
		Function<List<UUID>, Mono<Map<UUID, Long>>> retweetLoader = misses -> Mono
			.error(new AssertionError("retweet loader must not run — both ids hit"));

		StepVerifier
			.create(this.countCache.getAllMulti(List.of(idOne, idTwo), List.of(likeKey, replyKey, retweetKey),
					List.of(likeLoader, replyLoader, retweetLoader)))
			.assertNext(result -> {
				assertThat(result.get(0)).containsEntry(idOne, 5L).containsEntry(idTwo, 50L);
				assertThat(result.get(1)).containsEntry(idOne, 60L).containsEntry(idTwo, 9L);
				assertThat(result.get(2)).containsEntry(idOne, 1L).containsEntry(idTwo, 2L);
			})
			.verifyComplete();

		// Both misses (like::id2 and reply::id1) write back in ONE EVAL round-trip.
		verify(this.redisTemplate).execute(any(RedisScript.class), anyList(), anyList());
	}

	@Test
	void getAll_AllCachedButLoaderlessZero_NoMissesNoWrites() {
		// Every id resolves from cache, so misses is empty and the early return fires.
		UUID idOne = UUID.randomUUID();
		Function<UUID, String> keyFor = id -> "reply_count::" + id;
		given(this.valueOperations.multiGet(List.of(keyFor.apply(idOne))))
			.willReturn(Mono.just(List.of(encode(0L))));

		StepVerifier
			.create(this.countCache.getAll(List.of(idOne), keyFor,
					ids -> Mono.error(new AssertionError("no loader expected"))))
			.assertNext(counts -> assertThat(counts).containsEntry(idOne, 0L))
			.verifyComplete();
	}

	@Test
	void getPair_BothHit_DecodesBothWithoutLoading() {
		String keyA = "followers_count::user";
		String keyB = "following_count::user";
		given(this.valueOperations.multiGet(List.of(keyA, keyB)))
			.willReturn(Mono.just(List.of(encode(12L), encode(34L))));

		Mono<long[]> loader = Mono.error(new AssertionError("loader must not run when both keys hit"));

		StepVerifier.create(this.countCache.getPair(keyA, keyB, loader))
			.assertNext(pair -> {
				assertThat(pair[0]).isEqualTo(12L);
				assertThat(pair[1]).isEqualTo(34L);
			})
			.verifyComplete();

		verify(this.redisTemplate, never()).execute(any(RedisScript.class), anyList(), anyList());
	}

	@Test
	@SuppressWarnings("unchecked")
	void getPair_BothMiss_LoadsBothAndWritesBackInOneEval() {
		String keyA = "followers_count::user";
		String keyB = "following_count::user";
		given(this.valueOperations.multiGet(List.of(keyA, keyB)))
			.willReturn(Mono.just(java.util.Arrays.asList(null, null)));
		given(this.redisTemplate.execute(any(RedisScript.class), anyList(), anyList())).willReturn(Flux.just(2L));

		StepVerifier.create(this.countCache.getPair(keyA, keyB, Mono.just(new long[] { 5L, 8L })))
			.assertNext(pair -> {
				assertThat(pair[0]).isEqualTo(5L);
				assertThat(pair[1]).isEqualTo(8L);
			})
			.verifyComplete();

		// Both missed keys written back in ONE EVAL round-trip.
		verify(this.redisTemplate).execute(any(RedisScript.class), anyList(), anyList());
	}

	@Test
	@SuppressWarnings("unchecked")
	void getPair_PartialHit_KeepsCachedSideAndWritesOnlyTheMiss() {
		// keyA hits in cache, keyB misses: the cached A is kept, only B's loaded value is written
		// back, and the returned pair mixes the cached and loaded values.
		String keyA = "followers_count::user";
		String keyB = "following_count::user";
		given(this.valueOperations.multiGet(List.of(keyA, keyB)))
			.willReturn(Mono.just(java.util.Arrays.asList(encode(40L), null)));
		given(this.redisTemplate.execute(any(RedisScript.class), anyList(), anyList())).willReturn(Flux.just(1L));

		// The loader supplies both, but only the missed key (B) is taken from it.
		StepVerifier.create(this.countCache.getPair(keyA, keyB, Mono.just(new long[] { 999L, 8L })))
			.assertNext(pair -> {
				assertThat(pair[0]).isEqualTo(40L);
				assertThat(pair[1]).isEqualTo(8L);
			})
			.verifyComplete();

		verify(this.redisTemplate).execute(any(RedisScript.class), anyList(), anyList());
	}

	@Test
	@SuppressWarnings("unchecked")
	void increment_RunsConditionalScriptWithPositiveDelta() {
		String key = "followers_count::user";
		given(this.redisTemplate.execute(any(RedisScript.class), eq(List.of(key)), any(List.class)))
			.willReturn(Flux.just(1L));

		StepVerifier.create(this.countCache.increment(key)).verifyComplete();

		verify(this.redisTemplate).execute(any(RedisScript.class), eq(List.of(key)), any(List.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void decrement_RunsConditionalScriptWithNegativeDelta() {
		String key = "followers_count::user";
		given(this.redisTemplate.execute(any(RedisScript.class), eq(List.of(key)), any(List.class)))
			.willReturn(Flux.just(0L));

		StepVerifier.create(this.countCache.decrement(key)).verifyComplete();

		verify(this.redisTemplate).execute(any(RedisScript.class), eq(List.of(key)), any(List.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void incrementBoth_AppliesPositiveDeltaToBothKeysInOneRoundTrip() {
		String keyA = "following_count::actor";
		String keyB = "followers_count::target";
		given(this.redisTemplate.execute(any(RedisScript.class), eq(List.of(keyA, keyB)), any(List.class)))
			.willReturn(Flux.just(2L));

		StepVerifier.create(this.countCache.incrementBoth(keyA, keyB)).verifyComplete();

		// ONE EVAL over both keys instead of two serial INCRs.
		verify(this.redisTemplate).execute(any(RedisScript.class), eq(List.of(keyA, keyB)), any(List.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void decrementBoth_AppliesNegativeDeltaToBothKeysInOneRoundTrip() {
		String keyA = "following_count::actor";
		String keyB = "followers_count::target";
		given(this.redisTemplate.execute(any(RedisScript.class), eq(List.of(keyA, keyB)), any(List.class)))
			.willReturn(Flux.just(2L));

		StepVerifier.create(this.countCache.decrementBoth(keyA, keyB)).verifyComplete();

		verify(this.redisTemplate).execute(any(RedisScript.class), eq(List.of(keyA, keyB)), any(List.class));
	}

}
