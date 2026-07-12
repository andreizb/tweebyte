/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.mapper;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.model.FollowDto;
import ro.tweebyte.interactionservice.model.Status;

import static org.assertj.core.api.Assertions.assertThat;

class FollowMapperTests {

	private final FollowMapper mapper = org.mapstruct.factory.Mappers.getMapper(FollowMapper.class);

	@Test
	void mapRequestToEntity_ShouldMapCorrectly() {
		UUID followerId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();
		String status = "ACCEPTED";

		FollowEntity followEntity = this.mapper.mapRequestToEntity(followerId, followedId, status);

		assertThat(followEntity).isNotNull();
		assertThat(followEntity.getFollowerId()).isEqualTo(followerId);
		assertThat(followEntity.getFollowedId()).isEqualTo(followedId);
		assertThat(followEntity.getStatus()).isEqualTo(status);
		assertThat(followEntity.getId()).isNotNull();
		assertThat(followEntity.getCreatedAt()).isNotNull();
		assertThat(followEntity.isInsertable()).isTrue();
	}

	@Test
	void mapEntityToDto_ShouldMapCorrectly() {
		UUID id = UUID.randomUUID();
		UUID followerId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();
		String status = "ACCEPTED";

		FollowEntity followEntity = FollowEntity.builder()
			.id(id)
			.followerId(followerId)
			.followedId(followedId)
			.createdAt(createdAt)
			.status(status)
			.build();

		FollowDto followDto = this.mapper.mapEntityToDto(followEntity);

		assertThat(followDto).isNotNull();
		assertThat(followDto.getId()).isEqualTo(id);
		assertThat(followDto.getFollowerId()).isEqualTo(followerId);
		assertThat(followDto.getFollowedId()).isEqualTo(followedId);
		assertThat(followDto.getCreatedAt()).isEqualTo(createdAt);
		assertThat(followDto.getStatus()).isEqualTo(Status.ACCEPTED);
		assertThat(followEntity.isNew()).isFalse();
	}

	@Test
	void mapEntityToDto_WithUserName_ShouldMapCorrectly() {
		UUID id = UUID.randomUUID();
		UUID followerId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();
		String status = "ACCEPTED";
		String userName = "testUser";

		FollowEntity followEntity = FollowEntity.builder()
			.id(id)
			.followerId(followerId)
			.followedId(followedId)
			.createdAt(createdAt)
			.status(status)
			.build();

		FollowDto followDto = this.mapper.mapEntityToDto(followEntity, userName);

		assertThat(followDto).isNotNull();
		assertThat(followDto.getId()).isEqualTo(id);
		assertThat(followDto.getFollowerId()).isEqualTo(followerId);
		assertThat(followDto.getFollowedId()).isEqualTo(followedId);
		assertThat(followDto.getCreatedAt()).isEqualTo(createdAt);
		assertThat(followDto.getStatus()).isEqualTo(Status.ACCEPTED);
		assertThat(followDto.getUserName()).isEqualTo(userName);
	}

	@Test
	void mapEntityToDto_NullFollowEntity_ShouldReturnNull() {
		FollowDto followDto = this.mapper.mapEntityToDto(null);
		assertThat(followDto).isNull();
	}

	@Test
	void mapEntityToDto_WithUserName_NullFollowEntityAndUserName_ShouldReturnNull() {
		FollowDto followDto = this.mapper.mapEntityToDto(null, null);
		assertThat(followDto).isNull();
	}

	@Test
	void mapCreationRequestToEntity_ShouldMapCorrectly() {
		// Exercises the
		// protected mapCreationRequestToEntity directly (same package as mapper).
		UUID followerId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();
		String status = "REJECTED";

		FollowEntity followEntity = this.mapper.mapCreationRequestToEntity(followerId, followedId, status);

		assertThat(followEntity).isNotNull();
		assertThat(followEntity.getFollowerId()).isEqualTo(followerId);
		assertThat(followEntity.getFollowedId()).isEqualTo(followedId);
		assertThat(followEntity.getStatus()).isEqualTo(status);
	}

}
