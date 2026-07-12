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

import static org.assertj.core.api.Assertions.assertThat;

class FollowMapperTests {

	private final FollowMapper followMapper = org.mapstruct.factory.Mappers.getMapper(FollowMapper.class);

	@Test
	void testMapRequestToEntity() {
		UUID followerId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();
		FollowEntity.Status status = FollowEntity.Status.ACCEPTED;

		FollowEntity followEntity = this.followMapper.mapRequestToEntity(followerId, followedId, status);

		assertThat(followEntity).isNotNull();
		assertThat(followEntity.getId()).isNotNull();
		assertThat(followEntity.getCreatedAt()).isNotNull();
		assertThat(followEntity.getFollowerId()).isEqualTo(followerId);
		assertThat(followEntity.getFollowedId()).isEqualTo(followedId);
		assertThat(followEntity.getStatus()).isEqualTo(status);
	}

	@Test
	void testMapEntityToDto() {
		FollowEntity followEntity = new FollowEntity();
		followEntity.setId(UUID.randomUUID());
		followEntity.setCreatedAt(LocalDateTime.now());
		followEntity.setFollowerId(UUID.randomUUID());
		followEntity.setFollowedId(UUID.randomUUID());
		followEntity.setStatus(FollowEntity.Status.ACCEPTED);
		followEntity.setCreatedAt(LocalDateTime.now());

		FollowDto followDto = this.followMapper.mapEntityToDto(followEntity);

		assertThat(followDto).isNotNull();
		assertThat(followDto.getId()).isEqualTo(followEntity.getId());
		assertThat(followDto.getFollowerId()).isEqualTo(followEntity.getFollowerId());
		assertThat(followDto.getFollowedId()).isEqualTo(followEntity.getFollowedId());
		assertThat(followDto.getCreatedAt()).isEqualTo(followEntity.getCreatedAt());
		assertThat(followDto.getStatus()).isEqualTo(FollowDto.Status.ACCEPTED);
	}

	@Test
	void testMapEntityToDtoWithNullEntity() {
		FollowDto followDto = this.followMapper.mapEntityToDto(null);

		assertThat(followDto).isNull();
	}

	@Test
	void testMapEntityToDtoWithUserName() {
		FollowEntity followEntity = new FollowEntity();
		followEntity.setId(UUID.randomUUID());
		followEntity.setCreatedAt(LocalDateTime.now());
		followEntity.setFollowerId(UUID.randomUUID());
		followEntity.setFollowedId(UUID.randomUUID());
		followEntity.setStatus(FollowEntity.Status.PENDING);
		followEntity.setCreatedAt(LocalDateTime.now());

		String userName = "testuser";

		FollowDto followDto = this.followMapper.mapEntityToDto(followEntity, userName);

		assertThat(followDto).isNotNull();
		assertThat(followDto.getId()).isEqualTo(followEntity.getId());
		assertThat(followDto.getFollowerId()).isEqualTo(followEntity.getFollowerId());
		assertThat(followDto.getFollowedId()).isEqualTo(followEntity.getFollowedId());
		assertThat(followDto.getCreatedAt()).isEqualTo(followEntity.getCreatedAt());
		assertThat(followDto.getStatus()).isEqualTo(FollowDto.Status.PENDING);
		assertThat(followDto.getUserName()).isEqualTo(userName);
	}

	@Test
	void testMapEntityToDtoWithNullEntityAndUserName() {
		String userName = "testuser";
		FollowDto followDto = this.followMapper.mapEntityToDto(null, userName);

		assertThat(followDto).isNotNull();
		assertThat(followDto.getId()).isNull();
		assertThat(followDto.getUserName()).isEqualTo(userName);
	}

	@Test
	void testMapCreationRequestToEntity() {
		UUID followerId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();
		FollowEntity.Status status = FollowEntity.Status.REJECTED;

		FollowEntity followEntity = this.followMapper.mapCreationRequestToEntity(followerId, followedId, status);

		assertThat(followEntity).isNotNull();
		assertThat(followEntity.getFollowerId()).isEqualTo(followerId);
		assertThat(followEntity.getFollowedId()).isEqualTo(followedId);
		assertThat(followEntity.getStatus()).isEqualTo(status);
	}

}
