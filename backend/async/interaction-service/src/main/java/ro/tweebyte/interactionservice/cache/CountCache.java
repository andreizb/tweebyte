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
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

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
	private static final String INCR_IF_PRESENT = """
			if redis.call('exists', KEYS[1]) == 0 then return -1 end
			local value = redis.call('incrby', KEYS[1], ARGV[1])
			if value < 0 then redis.call('del', KEYS[1]); return 0 end
			return value""";

	// The same conditional increment as INCR_IF_PRESENT, applied to EVERY key with the shared
	// delta ARGV[1] in ONE round-trip: a follow/unfollow touches the actor's following counter and
	// the target's followers counter together, so batching the two deltas into one EVAL halves the
	// Redis hops on the write path. Per-key semantics are unchanged — a cold key is skipped (left
	// for the next read to reseed) and a delta that would go below zero deletes the key.
	private static final String INCR_EACH_IF_PRESENT = """
			for i = 1, #KEYS do
			if redis.call('exists', KEYS[i]) == 1 then
			local value = redis.call('incrby', KEYS[i], ARGV[1])
			if value < 0 then redis.call('del', KEYS[i]) end
			end
			end
			return #KEYS""";

	// Batch the cold-miss writes into ONE round-trip: SETEX every KEYS[i] to ARGV[i+1] with
	// the shared TTL in ARGV[1]. EVAL runs as an ordinary command over Lettuce's SHARED
	// multiplexed connection — unlike executePipelined, which forces a DEDICATED connection
	// per call and churns connections into ephemeral-port exhaustion under a cold-cache burst.
	private static final String FILL_MISSES = """
			for i = 1, #KEYS do
			redis.call('SETEX', KEYS[i], ARGV[1], ARGV[i + 1])
			end
			return #KEYS""";

	private final RedisTemplate<String, byte[]> redisTemplate;

	private final RedisScript<Long> incrIfPresent;

	private final RedisScript<Long> incrEachIfPresent;

	private final RedisScript<Long> fillMissesScript;

	private final Duration cacheTtl;

	public CountCache(RedisTemplate<String, byte[]> redisTemplate,
			@Value("${spring.cache.redis.time-to-live}") Duration cacheTtl) {
		this.redisTemplate = redisTemplate;
		this.cacheTtl = cacheTtl;
		this.incrIfPresent = new DefaultRedisScript<>(INCR_IF_PRESENT, Long.class);
		this.incrEachIfPresent = new DefaultRedisScript<>(INCR_EACH_IF_PRESENT, Long.class);
		this.fillMissesScript = new DefaultRedisScript<>(FILL_MISSES, Long.class);
	}

	public long get(String key, LongSupplier databaseLoader) {
		byte[] cached = this.redisTemplate.opsForValue().get(key);
		if (cached != null && cached.length > 0) {
			return decode(cached);
		}
		long count = databaseLoader.getAsLong();
		this.redisTemplate.opsForValue().set(key, encode(count), this.cacheTtl);
		return count;
	}

	public Map<UUID, Long> getAll(List<UUID> ids, Function<UUID, String> keyFor,
			Function<List<UUID>, Map<UUID, Long>> databaseLoader) {
		if (ids == null || ids.isEmpty()) {
			return Map.of();
		}
		List<String> keys = ids.stream().map(keyFor).toList();
		List<byte[]> cached = this.redisTemplate.opsForValue().multiGet(keys);
		Map<UUID, Long> counts = new HashMap<>();
		List<UUID> misses = new ArrayList<>();
		for (int index = 0; index < ids.size(); index++) {
			byte[] bytes = (cached != null) ? cached.get(index) : null;
			if (bytes != null && bytes.length > 0) {
				counts.put(ids.get(index), decode(bytes));
			}
			else {
				misses.add(ids.get(index));
			}
		}
		if (misses.isEmpty()) {
			return counts;
		}
		fillMisses(counts, misses, keyFor, databaseLoader.apply(misses));
		return counts;
	}

	// Collapsed read for several count families over the SAME id list: ONE MGET across every
	// family's keys (family-major: [fam0 ids..., fam1 ids..., ...]) instead of one MGET per
	// family, and ONE EVAL writeback for all misses across families. Family f uses keyFns.get(f)
	// to key an id and loaders.get(f) to resolve its missed ids on a miss. result.get(f) is family
	// f's id→count map (hits + loaded misses, an id the loader omitted defaulting to 0) — identical
	// per-family semantics to getAll.
	public List<Map<UUID, Long>> getAllMulti(List<UUID> ids, List<Function<UUID, String>> keyFns,
			List<Function<List<UUID>, Map<UUID, Long>>> loaders) {
		int families = keyFns.size();
		if (ids == null || ids.isEmpty()) {
			List<Map<UUID, Long>> empty = new ArrayList<>(families);
			for (int f = 0; f < families; f++) {
				empty.add(Map.of());
			}
			return empty;
		}
		List<String> allKeys = new ArrayList<>(families * ids.size());
		for (Function<UUID, String> keyFn : keyFns) {
			for (UUID id : ids) {
				allKeys.add(keyFn.apply(id));
			}
		}
		List<byte[]> cached = this.redisTemplate.opsForValue().multiGet(allKeys);
		List<Map<UUID, Long>> hits = new ArrayList<>(families);
		List<List<UUID>> missesPerFamily = new ArrayList<>(families);
		for (int f = 0; f < families; f++) {
			Map<UUID, Long> familyHits = new HashMap<>();
			List<UUID> familyMisses = new ArrayList<>();
			int base = f * ids.size();
			for (int index = 0; index < ids.size(); index++) {
				byte[] bytes = (cached != null) ? cached.get(base + index) : null;
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
		// One EVAL writeback for every family's misses; a family that hits in full skips its loader.
		List<String> writeKeys = new ArrayList<>();
		List<byte[]> writeValues = new ArrayList<>();
		for (int f = 0; f < families; f++) {
			List<UUID> familyMisses = missesPerFamily.get(f);
			if (familyMisses.isEmpty()) {
				continue;
			}
			Map<UUID, Long> fromDatabase = loaders.get(f).apply(familyMisses);
			Function<UUID, String> keyFn = keyFns.get(f);
			Map<UUID, Long> familyHits = hits.get(f);
			for (UUID id : familyMisses) {
				long count = fromDatabase.getOrDefault(id, 0L);
				familyHits.put(id, count);
				writeKeys.add(keyFn.apply(id));
				writeValues.add(encode(count));
			}
		}
		writeBatch(writeKeys, writeValues);
		return hits;
	}

	// Collapsed two-key read for the follower+following counts: ONE MGET for both keys, and on a
	// miss ONE combined DB load + ONE EVAL writeback — 3 ops instead of 2 GET + 2 COUNT + 2 SET.
	// The databaseLoader returns both counts in key order [keyA, keyB]; on a partial hit the
	// cached side is kept and only the missing key is written back.
	public long[] getPair(String keyA, String keyB, Supplier<long[]> databaseLoader) {
		List<byte[]> cached = this.redisTemplate.opsForValue().multiGet(List.of(keyA, keyB));
		byte[] a = (cached != null) ? cached.get(0) : null;
		byte[] b = (cached != null) ? cached.get(1) : null;
		boolean aHit = a != null && a.length > 0;
		boolean bHit = b != null && b.length > 0;
		if (aHit && bHit) {
			return new long[] { decode(a), decode(b) };
		}
		long[] fromDatabase = databaseLoader.get();
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
		writeBatch(missedKeys, missedValues);
		return new long[] { aValue, bValue };
	}

	// Materialise every miss — including ids the GROUP BY omitted because their count is
	// zero — so a genuinely-zero count is cached instead of being re-queried forever.
	private void fillMisses(Map<UUID, Long> counts, List<UUID> misses, Function<UUID, String> keyFor,
			Map<UUID, Long> fromDatabase) {
		// Build parallel key/value lists (the Lua script aligns KEYS[i] with ARGV[i+1]) and
		// write them all in one EVAL round-trip — a cold page of N count keys otherwise costs
		// N sequential blocking round-trips. The script (not executePipelined) keeps the write
		// on Lettuce's shared multiplexed connection, so it never churns dedicated connections.
		List<String> keys = new ArrayList<>(misses.size());
		List<byte[]> values = new ArrayList<>(misses.size());
		for (UUID id : misses) {
			long count = fromDatabase.getOrDefault(id, 0L);
			counts.put(id, count);
			keys.add(keyFor.apply(id));
			values.add(encode(count));
		}
		writeBatch(keys, values);
	}

	// One EVAL over the shared connection: ARGV[1] = TTL seconds, ARGV[i+1] = value for KEYS[i].
	private void writeBatch(List<String> keys, List<byte[]> values) {
		if (keys.isEmpty()) {
			return;
		}
		Object[] args = new Object[values.size() + 1];
		args[0] = encode(this.cacheTtl.toSeconds());
		for (int index = 0; index < values.size(); index++) {
			args[index + 1] = values.get(index);
		}
		this.redisTemplate.execute(this.fillMissesScript, keys, args);
	}

	public void increment(String key) {
		apply(key, 1L);
	}

	public void decrement(String key) {
		apply(key, -1L);
	}

	private void apply(String key, long delta) {
		this.redisTemplate.execute(this.incrIfPresent, List.of(key), (Object) encode(delta));
	}

	// Increment both counters in ONE round-trip (the follow write path: actor following + target
	// followers). Same conditional per-key semantics as increment, batched.
	public void incrementBoth(String keyA, String keyB) {
		applyBoth(keyA, keyB, 1L);
	}

	public void decrementBoth(String keyA, String keyB) {
		applyBoth(keyA, keyB, -1L);
	}

	private void applyBoth(String keyA, String keyB, long delta) {
		this.redisTemplate.execute(this.incrEachIfPresent, List.of(keyA, keyB), (Object) encode(delta));
	}

	private static byte[] encode(long value) {
		return Long.toString(value).getBytes(StandardCharsets.UTF_8);
	}

	private static long decode(byte[] bytes) {
		return Long.parseLong(new String(bytes, StandardCharsets.UTF_8));
	}

}
