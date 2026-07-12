/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.mapper;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.model.LikeDto;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.UserDto;

import static org.assertj.core.api.Assertions.assertThat;

class LikeMapperTests {

	private final LikeMapper mapper = org.mapstruct.factory.Mappers.getMapper(LikeMapper.class);

	@Test
	void mapRequestToEntity_ShouldMapCorrectly() {
		UUID userId = UUID.randomUUID();
		UUID likeableId = UUID.randomUUID();
		String likeableType = "TWEET";

		LikeEntity likeEntity = this.mapper.mapRequestToEntity(userId, likeableId, likeableType);

		assertThat(likeEntity).isNotNull();
		assertThat(likeEntity.getUserId()).isEqualTo(userId);
		assertThat(likeEntity.getLikeableId()).isEqualTo(likeableId);
		assertThat(likeEntity.getLikeableType()).isEqualTo(likeableType);
		assertThat(likeEntity.getId()).isNotNull();
		assertThat(likeEntity.getCreatedAt()).isNotNull();
		assertThat(likeEntity.isInsertable()).isTrue();
		assertThat(likeEntity.isNew()).isTrue();
	}

	@Test
	void mapEntityToDto_ShouldMapCorrectly() {
		UUID id = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();

		LikeEntity likeEntity = LikeEntity.builder().id(id).createdAt(createdAt).build();

		LikeDto likeDto = this.mapper.mapEntityToDto(likeEntity);

		assertThat(likeDto).isNotNull();
		assertThat(likeDto.getId()).isEqualTo(id);
		assertThat(likeDto.getCreatedAt()).isEqualTo(createdAt);
	}

	@Test
	void mapToDto_WithUserDto_ShouldMapCorrectly() {
		UUID id = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();
		UserDto userDto = new UserDto();
		userDto.setId(UUID.randomUUID());

		LikeEntity likeEntity = LikeEntity.builder().id(id).createdAt(createdAt).build();

		LikeDto likeDto = this.mapper.mapToDto(likeEntity, userDto);

		assertThat(likeDto).isNotNull();
		assertThat(likeDto.getId()).isEqualTo(id);
		assertThat(likeDto.getCreatedAt()).isEqualTo(createdAt);
		assertThat(likeDto.getUser()).isEqualTo(userDto);
	}

	@Test
	void mapToDto_WithTweetDto_ShouldMapCorrectly() {
		UUID id = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();
		TweetDto tweetDto = new TweetDto();
		tweetDto.setId(UUID.randomUUID());

		LikeEntity likeEntity = LikeEntity.builder().id(id).createdAt(createdAt).build();

		LikeDto likeDto = this.mapper.mapToDto(likeEntity, tweetDto);

		assertThat(likeDto).isNotNull();
		assertThat(likeDto.getId()).isEqualTo(id);
		assertThat(likeDto.getCreatedAt()).isEqualTo(createdAt);
		assertThat(likeDto.getTweet()).isEqualTo(tweetDto);
	}

	@Test
	void mapEntityToDto_NullEntity_ShouldReturnNull() {
		LikeDto likeDto = this.mapper.mapEntityToDto(null);
		assertThat(likeDto).isNull();
	}

	@Test
	void mapRequestToEntity_NullInput_ShouldReturnNull() {
		LikeEntity likeEntity = this.mapper.mapCreationRequestToEntity(null, null, null);
		assertThat(likeEntity).isNull();
	}

	@Test
	void mapCreationRequestToEntity_ShouldMapCorrectly() {
		// Exercises the
		// non-null path of the protected mapCreationRequestToEntity directly.
		UUID userId = UUID.randomUUID();
		UUID likeableId = UUID.randomUUID();
		String likeableType = "TWEET";

		LikeEntity likeEntity = this.mapper.mapCreationRequestToEntity(userId, likeableId, likeableType);

		assertThat(likeEntity).isNotNull();
		assertThat(likeEntity.getUserId()).isEqualTo(userId);
		assertThat(likeEntity.getLikeableId()).isEqualTo(likeableId);
		assertThat(likeEntity.getLikeableType()).isEqualTo(likeableType);
	}

}
