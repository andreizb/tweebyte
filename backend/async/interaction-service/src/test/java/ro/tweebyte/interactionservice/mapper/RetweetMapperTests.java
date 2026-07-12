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

	private final RetweetMapper retweetMapper = org.mapstruct.factory.Mappers.getMapper(RetweetMapper.class);

	@Test
	void testMapRequestToEntity() {
		RetweetCreateRequest request = new RetweetCreateRequest();
		request.setOriginalTweetId(UUID.randomUUID());
		request.setRetweeterId(UUID.randomUUID());
		request.setContent("This is a retweet.");

		RetweetEntity retweetEntity = this.retweetMapper.mapRequestToEntity(request);

		assertThat(retweetEntity).isNotNull();
		assertThat(retweetEntity.getId()).isNotNull();
		assertThat(retweetEntity.getCreatedAt()).isNotNull();
		assertThat(retweetEntity.getOriginalTweetId()).isEqualTo(request.getOriginalTweetId());
		assertThat(retweetEntity.getRetweeterId()).isEqualTo(request.getRetweeterId());
		assertThat(retweetEntity.getContent()).isEqualTo(request.getContent());
	}

	@Test
	void testMapEntityToDto() {
		RetweetEntity retweetEntity = new RetweetEntity();
		retweetEntity.setId(UUID.randomUUID());
		retweetEntity.setContent("This is a retweet.");
		retweetEntity.setCreatedAt(LocalDateTime.now());

		RetweetDto retweetDto = this.retweetMapper.mapEntityToDto(retweetEntity);

		assertThat(retweetDto).isNotNull();
		assertThat(retweetDto.getId()).isEqualTo(retweetEntity.getId());
		assertThat(retweetDto.getContent()).isEqualTo(retweetEntity.getContent());
		assertThat(retweetDto.getCreatedAt()).isEqualTo(retweetEntity.getCreatedAt());
	}

	@Test
	void testMapEntityToDtoWithUser() {
		RetweetEntity retweetEntity = new RetweetEntity();
		retweetEntity.setId(UUID.randomUUID());
		retweetEntity.setContent("This is a retweet.");
		retweetEntity.setCreatedAt(LocalDateTime.now());

		UserDto user = new UserDto(UUID.randomUUID(), "testuser", true, LocalDateTime.now());

		RetweetDto retweetDto = this.retweetMapper.mapEntityToDto(retweetEntity, user);

		assertThat(retweetDto).isNotNull();
		assertThat(retweetDto.getId()).isEqualTo(retweetEntity.getId());
		assertThat(retweetDto.getContent()).isEqualTo(retweetEntity.getContent());
		assertThat(retweetDto.getCreatedAt()).isEqualTo(retweetEntity.getCreatedAt());
		assertThat(retweetDto.getUser()).isEqualTo(user);
	}

	@Test
	void testMapEntityToDtoWithUserAndTweet() {
		RetweetEntity retweetEntity = new RetweetEntity();
		retweetEntity.setId(UUID.randomUUID());
		retweetEntity.setContent("This is a retweet.");
		retweetEntity.setCreatedAt(LocalDateTime.now());

		UserDto user = new UserDto(UUID.randomUUID(), "testuser", true, LocalDateTime.now());
		TweetDto tweet = new TweetDto(UUID.randomUUID(), UUID.randomUUID(), "Tweet content", null, null, null, null,
				null, null, null);

		RetweetDto retweetDto = this.retweetMapper.mapEntityToDto(retweetEntity, user, tweet);

		assertThat(retweetDto).isNotNull();
		assertThat(retweetDto.getId()).isEqualTo(retweetEntity.getId());
		assertThat(retweetDto.getContent()).isEqualTo(retweetEntity.getContent());
		assertThat(retweetDto.getCreatedAt()).isEqualTo(retweetEntity.getCreatedAt());
		assertThat(retweetDto.getUser()).isEqualTo(user);
		assertThat(retweetDto.getTweet()).isEqualTo(tweet);
	}

	@Test
	void testMapRequestToEntityForUpdate() {
		RetweetUpdateRequest updateRequest = new RetweetUpdateRequest();
		updateRequest.setId(UUID.randomUUID());
		updateRequest.setRetweeterId(UUID.randomUUID());
		updateRequest.setContent("Updated content.");

		RetweetEntity retweetEntity = new RetweetEntity();
		retweetEntity.setId(UUID.randomUUID());
		retweetEntity.setRetweeterId(UUID.randomUUID());
		retweetEntity.setContent("Old content.");

		this.retweetMapper.mapRequestToEntity(updateRequest, retweetEntity);

		assertThat(retweetEntity).isNotNull();
		assertThat(retweetEntity.getRetweeterId()).isEqualTo(updateRequest.getRetweeterId());
		assertThat(retweetEntity.getContent()).isEqualTo(updateRequest.getContent());
	}

	@Test
	void testMapCreationRequestToEntity() {
		RetweetCreateRequest createRequest = new RetweetCreateRequest();
		createRequest.setOriginalTweetId(UUID.randomUUID());
		createRequest.setRetweeterId(UUID.randomUUID());
		createRequest.setContent("Create request content.");

		RetweetEntity retweetEntity = this.retweetMapper.mapCreationRequestToEntity(createRequest);

		assertThat(retweetEntity).isNotNull();
		assertThat(retweetEntity.getOriginalTweetId()).isEqualTo(createRequest.getOriginalTweetId());
		assertThat(retweetEntity.getRetweeterId()).isEqualTo(createRequest.getRetweeterId());
		assertThat(retweetEntity.getContent()).isEqualTo(createRequest.getContent());
	}

	@Test
	void testMapCreationRequestToEntityWithNullRequest() {
		RetweetEntity retweetEntity = this.retweetMapper.mapCreationRequestToEntity(null);

		assertThat(retweetEntity).isNull();
	}

	@Test
	void testMapRequestToEntityWithNullUpdateRequest() {
		// Mirrors reactive
		// RetweetMapperTest#mapRequestToEntity_NullUpdateRequest_ShouldNotModifyEntity —
		// passing a null update request must leave the target entity intact.
		RetweetEntity retweetEntity = new RetweetEntity();
		retweetEntity.setContent("original");

		this.retweetMapper.mapRequestToEntity(null, retweetEntity);

		assertThat(retweetEntity).isNotNull();
		assertThat(retweetEntity.getContent()).isEqualTo("original");
	}

}
