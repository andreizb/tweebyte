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

	private final LikeMapper likeMapper = org.mapstruct.factory.Mappers.getMapper(LikeMapper.class);

	@Test
	void testMapRequestToEntity() {
		UUID userId = UUID.randomUUID();
		UUID likeableId = UUID.randomUUID();
		LikeEntity.LikeableType likeableType = LikeEntity.LikeableType.TWEET;

		LikeEntity likeEntity = this.likeMapper.mapRequestToEntity(userId, likeableId, likeableType);

		assertThat(likeEntity).isNotNull();
		assertThat(likeEntity.getId()).isNotNull();
		assertThat(likeEntity.getCreatedAt()).isNotNull();
		assertThat(likeEntity.getUserId()).isEqualTo(userId);
		assertThat(likeEntity.getLikeableId()).isEqualTo(likeableId);
		assertThat(likeEntity.getLikeableType()).isEqualTo(likeableType);
	}

	@Test
	void testMapEntityToDto() {
		LikeEntity likeEntity = new LikeEntity();
		likeEntity.setId(UUID.randomUUID());
		likeEntity.setCreatedAt(LocalDateTime.now());

		LikeDto likeDto = this.likeMapper.mapEntityToDto(likeEntity);

		assertThat(likeDto).isNotNull();
		assertThat(likeDto.getId()).isEqualTo(likeEntity.getId());
		assertThat(likeDto.getCreatedAt()).isEqualTo(likeEntity.getCreatedAt());
	}

	@Test
	void testMapEntityToDtoWithNullEntity() {
		LikeDto likeDto = this.likeMapper.mapEntityToDto(null);

		assertThat(likeDto).isNull();
	}

	@Test
	void testMapToDtoWithUserDto() {
		LikeEntity likeEntity = new LikeEntity();
		likeEntity.setId(UUID.randomUUID());
		likeEntity.setCreatedAt(LocalDateTime.now());

		UserDto userDto = new UserDto(UUID.randomUUID(), "username", true, LocalDateTime.now());

		LikeDto likeDto = this.likeMapper.mapToDto(likeEntity, userDto);

		assertThat(likeDto).isNotNull();
		assertThat(likeDto.getId()).isEqualTo(likeEntity.getId());
		assertThat(likeDto.getCreatedAt()).isEqualTo(likeEntity.getCreatedAt());
		assertThat(likeDto.getUser()).isEqualTo(userDto);
	}

	@Test
	void testMapToDtoWithTweetDto() {
		LikeEntity likeEntity = new LikeEntity();
		likeEntity.setId(UUID.randomUUID());
		likeEntity.setCreatedAt(LocalDateTime.now());

		TweetDto tweetDto = new TweetDto();
		tweetDto.setId(UUID.randomUUID());
		tweetDto.setContent("Test Tweet");

		LikeDto likeDto = this.likeMapper.mapToDto(likeEntity, tweetDto);

		assertThat(likeDto).isNotNull();
		assertThat(likeDto.getId()).isEqualTo(likeEntity.getId());
		assertThat(likeDto.getCreatedAt()).isEqualTo(likeEntity.getCreatedAt());
		assertThat(likeDto.getTweet()).isEqualTo(tweetDto);
	}

	@Test
	void testMapCreationRequestToEntity() {
		UUID userId = UUID.randomUUID();
		UUID likeableId = UUID.randomUUID();
		LikeEntity.LikeableType likeableType = LikeEntity.LikeableType.TWEET;

		LikeEntity likeEntity = this.likeMapper.mapCreationRequestToEntity(userId, likeableId, likeableType);

		assertThat(likeEntity).isNotNull();
		assertThat(likeEntity.getUserId()).isEqualTo(userId);
		assertThat(likeEntity.getLikeableId()).isEqualTo(likeableId);
		assertThat(likeEntity.getLikeableType()).isEqualTo(likeableType);
	}

	@Test
	void testMapCreationRequestToEntityWithNullValues() {
		LikeEntity likeEntity = this.likeMapper.mapCreationRequestToEntity(null, null, null);

		assertThat(likeEntity).isNull();
	}

}
