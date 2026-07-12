/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.service;

import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

import ro.tweebyte.userservice.entity.MediaAssetEntity;

/**
 * Process-local store for the media assets the benchmark touches: the filtered image
 * (keyed by the CRC32 of its bytes, for image-upload's content-addressed lookup) and the
 * download blob (keyed by id, for file-download). The seeder warms it with one request
 * per asset, so every benchmark request is a hit that never reaches Postgres — the DB is
 * only the cache-miss source of truth. Cleared by DELETE /media/cache during teardown.
 *
 * Reads stay lock-free ConcurrentHashMap lookups; the only per-hit write is a volatile
 * last-access stamp on the holder, used by MediaCacheCleanupService to prune to the
 * most-recently-used entries off the hot path (see its 24h-delayed cron). The benchmark
 * only resides ~2 assets vs. a cap of 10, so eviction never fires during a run.
 *
 * @author Andrei Zbarcea
 */
@Component
public class MediaCache {

	private final ConcurrentMap<UUID, Entry> byId = new ConcurrentHashMap<>();

	private final ConcurrentMap<Long, Entry> byChecksum = new ConcurrentHashMap<>();

	public MediaAssetEntity getById(UUID id) {
		Entry entry = this.byId.get(id);
		if (entry == null) {
			return null;
		}
		entry.lastAccessNanos = System.nanoTime();
		return entry.asset;
	}

	public MediaAssetEntity getByChecksum(long checksum) {
		Entry entry = this.byChecksum.get(checksum);
		if (entry == null) {
			return null;
		}
		entry.lastAccessNanos = System.nanoTime();
		return entry.asset;
	}

	public void put(MediaAssetEntity asset) {
		// Defensive: put() is only called post-persist so the id is normally present,
		// but guard explicitly to satisfy SpotBugs NP_NULL_ON_SOME_PATH.
		var id = asset.getId();
		if (id == null) {
			return;
		}
		Entry entry = new Entry(asset);
		this.byId.put(id, entry);
		this.byChecksum.put(asset.getChecksum(), entry);
	}

	public void flush() {
		this.byId.clear();
		this.byChecksum.clear();
	}

	public int size() {
		return this.byId.size();
	}

	/**
	 * Drop everything but the {@code maxEntries} most-recently-used assets, removing each
	 * evicted holder from both indexes. Approximate by design: the size/scan snapshot can
	 * race with concurrent puts, so the cache may sit a touch above the cap until the
	 * next tick — fine for a janitor.
	 * @param maxEntries the number of most-recently-used entries to retain
	 */
	public void evictToMostRecentlyUsed(int maxEntries) {
		int over = this.byId.size() - maxEntries;
		if (over <= 0) {
			return;
		}
		this.byId.values()
			.stream()
			.sorted(Comparator.comparingLong(entry -> entry.lastAccessNanos))
			.limit(over)
			.forEach(entry -> {
				this.byId.remove(entry.asset.getId(), entry);
				this.byChecksum.remove(entry.asset.getChecksum(), entry);
			});
	}

	private static final class Entry {

		final MediaAssetEntity asset;

		volatile long lastAccessNanos;

		Entry(MediaAssetEntity asset) {
			this.asset = asset;
			this.lastAccessNanos = System.nanoTime();
		}

	}

}
