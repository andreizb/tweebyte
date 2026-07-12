/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.cache;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Read-through cache for per-entity interaction counts (likes, replies, retweets,
 * followers, following). The single-id and batched reads share one per-entity key, so a
 * value populated by either path is served to the other, and a write applies an exact
 * delta with a conditional Redis script instead of evicting — which avoids rebuild
 * stampedes on hot keys. Counts self-heal: the conditional increment is a no-op once the
 * key has expired, and the next read reseeds from the database.
 *
 * @author Andrei Zbarcea
 */
@Component
public class CountCache {

	// Apply ARGV[1] to KEYS[1] only when the counter is already cached: a cold key is left
	// absent so the next read seeds it from the database (which already reflects the
	// just-persisted write), which prevents under/over-counting. A delta that would drive
	// the counter below zero deletes the key to force a clean reseed.
	private static final String INCR_IF_PRESENT = "if redis.call('exists', KEYS[1]) == 0 then return -1 end\n"
			+ "local value = redis.call('incrby', KEYS[1], ARGV[1])\n"
			+ "if value < 0 then redis.call('del', KEYS[1]); return 0 end\n" + "return value";

	// The same conditional increment as INCR_IF_PRESENT, applied to EVERY key with the shared
	// delta ARGV[1] in ONE round-trip: a follow/unfollow touches the actor's following counter and
	// the target's followers counter together, so batching the two deltas into one EVAL halves the
	// Redis hops on the write path. Per-key semantics are unchanged — a cold key is skipped (left
	// for the next read to reseed) and a delta that would go below zero deletes the key.
	private static final String INCR_EACH_IF_PRESENT = "for i = 1, #KEYS do\n"
			+ "  if redis.call('exists', KEYS[i]) == 1 then\n"
			+ "    local value = redis.call('incrby', KEYS[i], ARGV[1])\n"
			+ "    if value < 0 then redis.call('del', KEYS[i]) end\n" + "  end\n" + "end\n" + "return #KEYS";

	// Batch the cold-miss writes into ONE round-trip: SETEX every KEYS[i] to ARGV[i+1] with
	// the shared TTL in ARGV[1]. EVAL runs as one command over Lettuce's shared connection.
	private static final String FILL_MISSES = "for i = 1, #KEYS do\n"
			+ "  redis.call('SETEX', KEYS[i], ARGV[1], ARGV[i + 1])\n" + "end\n" + "return #KEYS";

	private final ReactiveRedisTemplate<String, byte[]> redisTemplate;

	private final RedisScript<Long> incrIfPresent;

	private final RedisScript<Long> incrEachIfPresent;

	private final RedisScript<Long> fillMissesScript;

	private final Duration cacheTtl;

	public CountCache(ReactiveRedisTemplate<String, byte[]> redisTemplate,
			@Value("${spring.cache.redis.time-to-live}") Duration cacheTtl) {
		this.redisTemplate = redisTemplate;
		this.cacheTtl = cacheTtl;
		this.incrIfPresent = RedisScript.of(INCR_IF_PRESENT, Long.class);
		this.incrEachIfPresent = RedisScript.of(INCR_EACH_IF_PRESENT, Long.class);
		this.fillMissesScript = RedisScript.of(FILL_MISSES, Long.class);
	}

	public Mono<Long> get(String key, Mono<Long> databaseLoader) {
		return CacheReads.offload(this.redisTemplate.opsForValue().get(key))
			.filter(bytes -> bytes.length > 0)
			.map(CountCache::decode)
			.switchIfEmpty(Mono.defer(() -> databaseLoader.flatMap(count -> this.redisTemplate.opsForValue()
				.set(key, encode(count), this.cacheTtl)
				.thenReturn(count))));
	}

	public Mono<Map<UUID, Long>> getAll(List<UUID> ids, Function<UUID, String> keyFor,
			Function<List<UUID>, Mono<Map<UUID, Long>>> databaseLoader) {
		if (ids == null || ids.isEmpty()) {
			return Mono.just(Map.of());
		}
		List<String> keys = ids.stream().map(keyFor).toList();
		return CacheReads.offload(this.redisTemplate.opsForValue().multiGet(keys)).flatMap(cached -> {
			Map<UUID, Long> counts = new HashMap<>();
			List<UUID> misses = new ArrayList<>();
			for (int index = 0; index < ids.size(); index++) {
				byte[] bytes = cached.get(index);
				if (bytes != null && bytes.length > 0) {
					counts.put(ids.get(index), decode(bytes));
				}
				else {
					misses.add(ids.get(index));
				}
			}
			if (misses.isEmpty()) {
				return Mono.just(counts);
			}
			return databaseLoader.apply(misses).flatMap(fromDatabase -> fillMisses(counts, misses, keyFor, fromDatabase));
		});
	}

	// Collapsed read for several count families over the SAME id list: ONE MGET across every
	// family's keys (family-major: [fam0 ids..., fam1 ids..., ...]) instead of one MGET per
	// family, and ONE EVAL writeback for all misses across families. Family f uses keyFns.get(f)
	// to key an id and loaders.get(f) to resolve its missed ids on a miss; the loaders share
	// whatever upstream they close over (e.g. one cached combined query), so a fully-cached page
	// issues no load. result.get(f) is family f's id→count map (hits + loaded misses, an id the
	// loader omitted defaulting to 0) — identical per-family semantics to getAll.
	public Mono<List<Map<UUID, Long>>> getAllMulti(List<UUID> ids, List<Function<UUID, String>> keyFns,
			List<Function<List<UUID>, Mono<Map<UUID, Long>>>> loaders) {
		int families = keyFns.size();
		if (ids == null || ids.isEmpty()) {
			List<Map<UUID, Long>> empty = new ArrayList<>(families);
			for (int f = 0; f < families; f++) {
				empty.add(Map.of());
			}
			return Mono.just(empty);
		}
		List<String> allKeys = new ArrayList<>(families * ids.size());
		for (Function<UUID, String> keyFn : keyFns) {
			for (UUID id : ids) {
				allKeys.add(keyFn.apply(id));
			}
		}
		return CacheReads.offload(this.redisTemplate.opsForValue().multiGet(allKeys)).flatMap(cached -> {
			List<Map<UUID, Long>> hits = new ArrayList<>(families);
			List<List<UUID>> missesPerFamily = new ArrayList<>(families);
			for (int f = 0; f < families; f++) {
				Map<UUID, Long> familyHits = new HashMap<>();
				List<UUID> familyMisses = new ArrayList<>();
				int base = f * ids.size();
				for (int index = 0; index < ids.size(); index++) {
					byte[] bytes = cached.get(base + index);
					if (bytes != null && bytes.length > 0) {
						familyHits.put(ids.get(index), decode(bytes));
					}
					else {
						familyMisses.add(ids.get(index));
					}
				}
				hits.add(familyHits);
				missesPerFamily.add(familyMisses);
			}
			if (missesPerFamily.stream().allMatch(List::isEmpty)) {
				return Mono.just(hits);
			}
			// Only the families that missed subscribe their loader; the loaders share their cached
			// upstream so the combined query still fires at most once.
			return Flux.range(0, families)
				.filter(f -> !missesPerFamily.get(f).isEmpty())
				.flatMap(f -> loaders.get(f).apply(missesPerFamily.get(f)).map(loaded -> Map.entry(f, loaded)))
				.collectMap(Map.Entry::getKey, Map.Entry::getValue)
				.flatMap(loadedByFamily -> {
					List<String> writeKeys = new ArrayList<>();
					List<byte[]> writeValues = new ArrayList<>();
					for (int f = 0; f < families; f++) {
						Map<UUID, Long> fromDatabase = loadedByFamily.getOrDefault(f, Map.of());
						Function<UUID, String> keyFn = keyFns.get(f);
						Map<UUID, Long> familyHits = hits.get(f);
						for (UUID id : missesPerFamily.get(f)) {
							long count = fromDatabase.getOrDefault(id, 0L);
							familyHits.put(id, count);
							writeKeys.add(keyFn.apply(id));
							writeValues.add(encode(count));
						}
					}
					return writeBatch(writeKeys, writeValues).thenReturn(hits);
				});
		});
	}

	// Collapsed two-key read for the follower+following counts: ONE MGET for both keys, and on
	// a miss ONE combined DB load + ONE EVAL writeback — 3 ops instead of 2 GET + 2 COUNT + 2
	// SET. The databaseLoader returns both counts in key order [keyA, keyB]; on a partial hit
	// the cached side is kept and only the missing key is written back.
	public Mono<long[]> getPair(String keyA, String keyB, Mono<long[]> databaseLoader) {
		return CacheReads.offload(this.redisTemplate.opsForValue().multiGet(List.of(keyA, keyB))).flatMap(cached -> {
			byte[] a = cached.get(0);
			byte[] b = cached.get(1);
			boolean aHit = a != null && a.length > 0;
			boolean bHit = b != null && b.length > 0;
			if (aHit && bHit) {
				return Mono.just(new long[] { decode(a), decode(b) });
			}
			return databaseLoader.flatMap(fromDatabase -> {
				long aValue = aHit ? decode(a) : fromDatabase[0];
				long bValue = bHit ? decode(b) : fromDatabase[1];
				List<String> missedKeys = new ArrayList<>(2);
				List<byte[]> missedValues = new ArrayList<>(2);
				if (!aHit) {
					missedKeys.add(keyA);
					missedValues.add(encode(aValue));
				}
				if (!bHit) {
					missedKeys.add(keyB);
					missedValues.add(encode(bValue));
				}
				return writeBatch(missedKeys, missedValues).thenReturn(new long[] { aValue, bValue });
			});
		});
	}

	// Materialise every miss — including ids the GROUP BY omitted because their count is
	// zero — so a genuinely-zero count is cached instead of being re-queried forever.
	private Mono<Map<UUID, Long>> fillMisses(Map<UUID, Long> counts, List<UUID> misses, Function<UUID, String> keyFor,
			Map<UUID, Long> fromDatabase) {
		List<String> keys = new ArrayList<>(misses.size());
		List<byte[]> values = new ArrayList<>(misses.size());
		for (UUID id : misses) {
			long count = fromDatabase.getOrDefault(id, 0L);
			counts.put(id, count);
			keys.add(keyFor.apply(id));
			values.add(encode(count));
		}
		return writeBatch(keys, values).thenReturn(counts);
	}

	// One EVAL over the shared connection: ARGV[1] = TTL seconds, ARGV[i+1] = value for KEYS[i].
	private Mono<Void> writeBatch(List<String> keys, List<byte[]> values) {
		if (keys.isEmpty()) {
			return Mono.empty();
		}
		List<Object> args = new ArrayList<>(values.size() + 1);
		args.add(encode(this.cacheTtl.toSeconds()));
		for (int index = 0; index < values.size(); index++) {
			args.add(values.get(index));
		}
		return this.redisTemplate.execute(this.fillMissesScript, keys, args).then();
	}

	public Mono<Void> increment(String key) {
		return apply(key, 1L);
	}

	public Mono<Void> decrement(String key) {
		return apply(key, -1L);
	}

	private Mono<Void> apply(String key, long delta) {
		return this.redisTemplate.execute(this.incrIfPresent, List.of(key), List.of(encode(delta))).then();
	}

	// Increment both counters in ONE round-trip (the follow write path: actor following + target
	// followers). Same conditional per-key semantics as increment, batched.
	public Mono<Void> incrementBoth(String keyA, String keyB) {
		return applyBoth(keyA, keyB, 1L);
	}

	public Mono<Void> decrementBoth(String keyA, String keyB) {
		return applyBoth(keyA, keyB, -1L);
	}

	private Mono<Void> applyBoth(String keyA, String keyB, long delta) {
		return this.redisTemplate.execute(this.incrEachIfPresent, List.of(keyA, keyB), List.of(encode(delta))).then();
	}

	private static byte[] encode(long value) {
		return Long.toString(value).getBytes(StandardCharsets.UTF_8);
	}

	private static long decode(byte[] bytes) {
		return Long.parseLong(new String(bytes, StandardCharsets.UTF_8));
	}

}
