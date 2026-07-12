/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.entity;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FollowEntityTests {

	@Test
	void testFollowEntityConstructor() {
		// Given
		UUID followerId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();
		FollowEntity.Status status = FollowEntity.Status.PENDING;

		// When
		FollowEntity followEntity = new FollowEntity(followerId, followedId, status, false);

		// Then
		assertThat(followEntity).isNotNull();
		assertThat(followEntity.getFollowerId()).isEqualTo(followerId);
		assertThat(followEntity.getFollowedId()).isEqualTo(followedId);
		assertThat(followEntity.getStatus()).isEqualTo(status);
		assertThat(followEntity.isInsertable()).isFalse();
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

	@Test
	void testFollowEntitySettersAndGetters() {
		// Given
		FollowEntity followEntity = new FollowEntity();
		UUID followerId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();
		FollowEntity.Status status = FollowEntity.Status.ACCEPTED;

		// When
		followEntity.setFollowerId(followerId);
		followEntity.setFollowedId(followedId);
		followEntity.setStatus(status);

		// Then
		assertThat(followEntity.getFollowerId()).isEqualTo(followerId);
		assertThat(followEntity.getFollowedId()).isEqualTo(followedId);
		assertThat(followEntity.getStatus()).isEqualTo(status);
	}

	@Test
	void testFollowEntityStatusEnum() {
		// Given
		FollowEntity.Status pendingStatus = FollowEntity.Status.PENDING;
		FollowEntity.Status acceptedStatus = FollowEntity.Status.ACCEPTED;

		// Then
		assertThat(pendingStatus.name()).isEqualTo("PENDING");
		assertThat(acceptedStatus.name()).isEqualTo("ACCEPTED");
	}

}
