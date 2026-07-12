/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikeEntityTests {

	@Test
	void testLikeEntityConstructor() {
		// Given
		UUID userId = UUID.randomUUID();
		UUID likeableId = UUID.randomUUID();
		LikeEntity.LikeableType likeableType = LikeEntity.LikeableType.TWEET;

		// When
		LikeEntity likeEntity = new LikeEntity(userId, likeableId, likeableType);

		// Then
		assertThat(likeEntity).isNotNull();
		assertThat(likeEntity.getUserId()).isEqualTo(userId);
		assertThat(likeEntity.getLikeableId()).isEqualTo(likeableId);
		assertThat(likeEntity.getLikeableType()).isEqualTo(likeableType);
	}

	@Test
	void testLikeEntitySettersAndGetters() {
		// Given
		LikeEntity likeEntity = new LikeEntity();
		UUID id = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();
		UUID userId = UUID.randomUUID();
		UUID likeableId = UUID.randomUUID();
		LikeEntity.LikeableType likeableType = LikeEntity.LikeableType.REPLY;

		// When
		likeEntity.setId(id);
		likeEntity.setCreatedAt(createdAt);
		likeEntity.setUserId(userId);
		likeEntity.setLikeableId(likeableId);
		likeEntity.setLikeableType(likeableType);

		// Then
		assertThat(likeEntity.getId()).isEqualTo(id);
		assertThat(likeEntity.getCreatedAt()).isEqualTo(createdAt);
		assertThat(likeEntity.getUserId()).isEqualTo(userId);
		assertThat(likeEntity.getLikeableId()).isEqualTo(likeableId);
		assertThat(likeEntity.getLikeableType()).isEqualTo(likeableType);
	}

}
