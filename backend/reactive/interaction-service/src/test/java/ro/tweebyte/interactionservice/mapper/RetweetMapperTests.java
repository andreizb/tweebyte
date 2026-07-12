/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.mapper;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.model.RetweetCreateRequest;
import ro.tweebyte.interactionservice.model.RetweetDto;
import ro.tweebyte.interactionservice.model.RetweetUpdateRequest;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.UserDto;

import static org.assertj.core.api.Assertions.assertThat;

class RetweetMapperTests {

	private final RetweetMapper mapper = org.mapstruct.factory.Mappers.getMapper(RetweetMapper.class);

	@Test
	void mapRequestToEntity_ShouldMapCorrectly() {
		UUID originalTweetId = UUID.randomUUID();
		UUID retweeterId = UUID.randomUUID();
		String content = "Retweet content";

		RetweetCreateRequest request = new RetweetCreateRequest();
		request.setOriginalTweetId(originalTweetId);
		request.setRetweeterId(retweeterId);
		request.setContent(content);

		RetweetEntity retweetEntity = this.mapper.mapRequestToEntity(request);

		assertThat(retweetEntity).isNotNull();
		assertThat(retweetEntity.getOriginalTweetId()).isEqualTo(originalTweetId);
		assertThat(retweetEntity.getRetweeterId()).isEqualTo(retweeterId);
		assertThat(retweetEntity.getContent()).isEqualTo(content);
		assertThat(retweetEntity.getId()).isNotNull();
		assertThat(retweetEntity.getCreatedAt()).isNotNull();
		assertThat(retweetEntity.isInsertable()).isTrue();
		assertThat(retweetEntity.isNew()).isTrue();
	}

	@Test
	void mapEntityToDto_ShouldMapCorrectly() {
		UUID id = UUID.randomUUID();
		String content = "Retweet content";
		LocalDateTime createdAt = LocalDateTime.now();

		RetweetEntity retweetEntity = RetweetEntity.builder().id(id).content(content).createdAt(createdAt).build();

		RetweetDto retweetDto = this.mapper.mapEntityToDto(retweetEntity);

		assertThat(retweetDto).isNotNull();
		assertThat(retweetDto.getId()).isEqualTo(id);
		assertThat(retweetDto.getContent()).isEqualTo(content);
		assertThat(retweetDto.getCreatedAt()).isEqualTo(createdAt);
	}

	@Test
	void mapEntityToDto_WithUser_ShouldMapCorrectly() {
		UUID id = UUID.randomUUID();
		String content = "Retweet content";
		LocalDateTime createdAt = LocalDateTime.now();
		UserDto userDto = new UserDto();
		userDto.setId(UUID.randomUUID());

		RetweetEntity retweetEntity = RetweetEntity.builder().id(id).content(content).createdAt(createdAt).build();

		RetweetDto retweetDto = this.mapper.mapEntityToDto(retweetEntity, userDto);

		assertThat(retweetDto).isNotNull();
		assertThat(retweetDto.getId()).isEqualTo(id);
		assertThat(retweetDto.getContent()).isEqualTo(content);
		assertThat(retweetDto.getCreatedAt()).isEqualTo(createdAt);
		assertThat(retweetDto.getUser()).isEqualTo(userDto);
	}

	@Test
	void mapEntityToDto_WithUserAndTweet_ShouldMapCorrectly() {
		UUID id = UUID.randomUUID();
		String content = "Retweet content";
		LocalDateTime createdAt = LocalDateTime.now();
		UserDto userDto = new UserDto();
		userDto.setId(UUID.randomUUID());
		TweetDto tweetDto = new TweetDto();
		tweetDto.setId(UUID.randomUUID());

		RetweetEntity retweetEntity = RetweetEntity.builder().id(id).content(content).createdAt(createdAt).build();

		RetweetDto retweetDto = this.mapper.mapEntityToDto(retweetEntity, userDto, tweetDto);

		assertThat(retweetDto).isNotNull();
		assertThat(retweetDto.getId()).isEqualTo(id);
		assertThat(retweetDto.getContent()).isEqualTo(content);
		assertThat(retweetDto.getCreatedAt()).isEqualTo(createdAt);
		assertThat(retweetDto.getUser()).isEqualTo(userDto);
		assertThat(retweetDto.getTweet()).isEqualTo(tweetDto);
	}

	@Test
	void mapRequestToEntity_Update_ShouldMapCorrectly() {
		UUID id = UUID.randomUUID();
		UUID retweeterId = UUID.randomUUID();
		String updatedContent = "Updated retweet content";

		RetweetUpdateRequest updateRequest = new RetweetUpdateRequest();
		updateRequest.setId(id);
		updateRequest.setRetweeterId(retweeterId);
		updateRequest.setContent(updatedContent);

		RetweetEntity retweetEntity = new RetweetEntity();
		this.mapper.mapRequestToEntity(updateRequest, retweetEntity);

		assertThat(retweetEntity).isNotNull();
		assertThat(retweetEntity.getId()).isEqualTo(id);
		assertThat(retweetEntity.getRetweeterId()).isEqualTo(retweeterId);
		assertThat(retweetEntity.getContent()).isEqualTo(updatedContent);
	}

	@Test
	void mapCreationRequestToEntity_NullRequest_ShouldReturnNull() {
		RetweetEntity retweetEntity = this.mapper.mapCreationRequestToEntity(null);
		assertThat(retweetEntity).isNull();
	}

	@Test
	void mapEntityToDto_NullEntity_ShouldReturnNull() {
		RetweetDto retweetDto = this.mapper.mapEntityToDto(null);
		assertThat(retweetDto).isNull();
	}

	@Test
	void mapRequestToEntity_NullUpdateRequest_ShouldNotModifyEntity() {
		RetweetEntity retweetEntity = new RetweetEntity();
		this.mapper.mapRequestToEntity(null, retweetEntity);

		assertThat(retweetEntity).isNotNull();
	}

}
