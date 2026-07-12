/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.service;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import ro.tweebyte.userservice.entity.MediaAssetEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers MediaCache's LRU eviction: the over-cap scan drops the least-recently-used
 * holders from both the id and checksum indexes, while the under-cap call is a no-op.
 */
class MediaCacheTests {

	private static MediaAssetEntity asset(long checksum) {
		return MediaAssetEntity.builder().id(UUID.randomUUID()).checksum(checksum).build();
	}

	@Test
	void evictToMostRecentlyUsedDropsLeastRecentlyUsed() {
		MediaCache cache = new MediaCache();
		MediaAssetEntity a = asset(1L);
		MediaAssetEntity b = asset(2L);
		MediaAssetEntity c = asset(3L);
		cache.put(a);
		cache.put(b);
		cache.put(c);

		// Touch a then c so b is the least-recently-used and the eviction target.
		cache.getById(a.getId());
		cache.getById(c.getId());

		cache.evictToMostRecentlyUsed(2);

		assertThat(cache.size()).isEqualTo(2);
		assertThat(cache.getById(b.getId())).isNull();
		assertThat(cache.getByChecksum(2L)).isNull();
		assertThat(cache.getById(a.getId())).isSameAs(a);
		assertThat(cache.getById(c.getId())).isSameAs(c);
	}

	@Test
	void evictToMostRecentlyUsedUnderCapIsNoOp() {
		MediaCache cache = new MediaCache();
		cache.put(asset(1L));
		cache.put(asset(2L));

		cache.evictToMostRecentlyUsed(5);

		assertThat(cache.size()).isEqualTo(2);
	}

}
