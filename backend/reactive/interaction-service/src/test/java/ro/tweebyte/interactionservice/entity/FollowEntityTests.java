/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R2DBC FollowEntity uses String status (no enum) and a Persistable contract; we exercise
 * the same shape on the reactive side.
 */
class FollowEntityTests {

	@Test
	void noArgsConstructorYieldsNullFields() {
		FollowEntity entity = new FollowEntity();
		assertThat(entity).isNotNull();
		assertThat(entity.getId()).isNull();
		assertThat(entity.getStatus()).isNull();
		assertThat(entity.getFollowerId()).isNull();
		assertThat(entity.getFollowedId()).isNull();
		assertThat(entity.getCreatedAt()).isNull();
		assertThat(entity.isInsertable()).isFalse();
	}

	@Test
	void allArgsConstructorPopulatesAllFields() {
		UUID id = UUID.randomUUID();
		UUID follower = UUID.randomUUID();
		UUID followed = UUID.randomUUID();
		LocalDateTime now = LocalDateTime.now();
		FollowEntity entity = new FollowEntity(id, now, true, follower, followed, "ACCEPTED");
		assertThat(entity.getId()).isEqualTo(id);
		assertThat(entity.getCreatedAt()).isEqualTo(now);
		assertThat(entity.isInsertable()).isTrue();
		assertThat(entity.getFollowerId()).isEqualTo(follower);
		assertThat(entity.getFollowedId()).isEqualTo(followed);
		assertThat(entity.getStatus()).isEqualTo("ACCEPTED");
	}

	@Test
	void builderPopulatesAllFields() {
		UUID id = UUID.randomUUID();
		UUID follower = UUID.randomUUID();
		UUID followed = UUID.randomUUID();
		FollowEntity entity = FollowEntity.builder()
			.id(id)
			.followerId(follower)
			.followedId(followed)
			.status("PENDING")
			.build();
		assertThat(entity.getId()).isEqualTo(id);
		assertThat(entity.getFollowerId()).isEqualTo(follower);
		assertThat(entity.getFollowedId()).isEqualTo(followed);
		assertThat(entity.getStatus()).isEqualTo("PENDING");
	}

	@Test
	void settersUpdateState() {
		FollowEntity entity = new FollowEntity();
		UUID follower = UUID.randomUUID();
		UUID followed = UUID.randomUUID();
		entity.setFollowerId(follower);
		entity.setFollowedId(followed);
		entity.setStatus("REJECTED");
		assertThat(entity.getFollowerId()).isEqualTo(follower);
		assertThat(entity.getFollowedId()).isEqualTo(followed);
		assertThat(entity.getStatus()).isEqualTo("REJECTED");
	}

	@Test
	void isNewReturnsTrueWhenInsertableTrue() {
		FollowEntity entity = new FollowEntity();
		entity.setId(UUID.randomUUID());
		entity.setInsertable(true);
		assertThat(entity.isNew()).isTrue();
	}

	@Test
	void isNewReturnsTrueWhenIdNullAndInsertableFalse() {
		FollowEntity entity = new FollowEntity();
		entity.setInsertable(false);
		assertThat(entity.isNew()).isTrue();
	}

	@Test
	void isNewReturnsFalseWhenIdSetAndNotInsertable() {
		FollowEntity entity = new FollowEntity();
		entity.setId(UUID.randomUUID());
		entity.setInsertable(false);
		assertThat(entity.isNew()).isFalse();
	}

}
