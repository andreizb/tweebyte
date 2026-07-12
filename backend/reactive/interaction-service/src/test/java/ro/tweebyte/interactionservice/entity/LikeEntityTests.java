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
	void noArgsConstructorYieldsNullFields() {
		LikeEntity entity = new LikeEntity();
		assertThat(entity).isNotNull();
		assertThat(entity.getId()).isNull();
		assertThat(entity.getUserId()).isNull();
		assertThat(entity.getLikeableId()).isNull();
		assertThat(entity.getLikeableType()).isNull();
	}

	@Test
	void allArgsConstructor() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID likeableId = UUID.randomUUID();
		LocalDateTime now = LocalDateTime.now();
		LikeEntity entity = new LikeEntity(id, now, true, userId, likeableId, "TWEET");
		assertThat(entity.getId()).isEqualTo(id);
		assertThat(entity.getCreatedAt()).isEqualTo(now);
		assertThat(entity.isInsertable()).isTrue();
		assertThat(entity.getUserId()).isEqualTo(userId);
		assertThat(entity.getLikeableId()).isEqualTo(likeableId);
		assertThat(entity.getLikeableType()).isEqualTo("TWEET");
	}

	@Test
	void builderPopulatesFields() {
		UUID userId = UUID.randomUUID();
		UUID likeableId = UUID.randomUUID();
		LikeEntity entity = LikeEntity.builder().userId(userId).likeableId(likeableId).likeableType("REPLY").build();
		assertThat(entity.getUserId()).isEqualTo(userId);
		assertThat(entity.getLikeableId()).isEqualTo(likeableId);
		assertThat(entity.getLikeableType()).isEqualTo("REPLY");
	}

	@Test
	void isNewBranchInsertableTrue() {
		LikeEntity e = new LikeEntity();
		e.setId(UUID.randomUUID());
		e.setInsertable(true);
		assertThat(e.isNew()).isTrue();
	}

	@Test
	void isNewBranchIdNull() {
		LikeEntity e = new LikeEntity();
		e.setInsertable(false);
		assertThat(e.isNew()).isTrue();
	}

	@Test
	void isNewBranchIdSetAndNotInsertable() {
		LikeEntity e = new LikeEntity();
		e.setId(UUID.randomUUID());
		e.setInsertable(false);
		assertThat(e.isNew()).isFalse();
	}

	@Test
	void settersUpdateState() {
		LikeEntity e = new LikeEntity();
		UUID id = UUID.randomUUID();
		e.setId(id);
		e.setUserId(id);
		e.setLikeableId(id);
		e.setLikeableType("TWEET");
		assertThat(e.getId()).isEqualTo(id);
		assertThat(e.getUserId()).isEqualTo(id);
		assertThat(e.getLikeableId()).isEqualTo(id);
		assertThat(e.getLikeableType()).isEqualTo("TWEET");
	}

}
