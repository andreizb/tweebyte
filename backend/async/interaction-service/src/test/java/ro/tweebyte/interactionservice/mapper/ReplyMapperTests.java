/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.mapper;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.model.ReplyCreateRequest;
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.ReplyUpdateRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ReplyMapperTests {

	private final ReplyMapper replyMapper = org.mapstruct.factory.Mappers.getMapper(ReplyMapper.class);

	@Test
	void testMapRequestToEntity() {
		ReplyCreateRequest request = new ReplyCreateRequest();
		request.setTweetId(UUID.randomUUID());
		request.setUserId(UUID.randomUUID());
		request.setContent("This is a reply.");

		ReplyEntity replyEntity = this.replyMapper.mapRequestToEntity(request);

		assertThat(replyEntity).isNotNull();
		assertThat(replyEntity.getId()).isNotNull();
		assertThat(replyEntity.getCreatedAt()).isNotNull();
		assertThat(replyEntity.getTweetId()).isEqualTo(request.getTweetId());
		assertThat(replyEntity.getUserId()).isEqualTo(request.getUserId());
		assertThat(replyEntity.getContent()).isEqualTo(request.getContent());
	}

	@Test
	void testMapEntityToCreationDto() {
		ReplyEntity replyEntity = new ReplyEntity();
		replyEntity.setId(UUID.randomUUID());
		replyEntity.setCreatedAt(LocalDateTime.now());
		replyEntity.setTweetId(UUID.randomUUID());
		replyEntity.setUserId(UUID.randomUUID());
		replyEntity.setContent("This is a reply.");

		ReplyDto replyDto = this.replyMapper.mapEntityToCreationDto(replyEntity);

		assertThat(replyDto).isNotNull();
		assertThat(replyDto.getId()).isEqualTo(replyEntity.getId());
	}

	@Test
	void testMapEntityToCreationDtoWithNullEntity() {
		ReplyDto replyDto = this.replyMapper.mapEntityToCreationDto(null);

		assertThat(replyDto).isNull();
	}

	@Test
	void testMapEntityToDtoWithUserName() {
		ReplyEntity replyEntity = new ReplyEntity();
		replyEntity.setId(UUID.randomUUID());
		replyEntity.setCreatedAt(LocalDateTime.now());
		replyEntity.setTweetId(UUID.randomUUID());
		replyEntity.setUserId(UUID.randomUUID());
		replyEntity.setContent("This is a reply.");

		String userName = "testUser";

		ReplyDto replyDto = this.replyMapper.mapEntityToDto(replyEntity, userName);

		assertThat(replyDto).isNotNull();
		assertThat(replyDto.getId()).isEqualTo(replyEntity.getId());
		assertThat(replyDto.getUserId()).isEqualTo(replyEntity.getUserId());
		assertThat(replyDto.getContent()).isEqualTo(replyEntity.getContent());
		assertThat(replyDto.getCreatedAt()).isEqualTo(replyEntity.getCreatedAt());
		assertThat(replyDto.getUserName()).isEqualTo(userName);
	}

	@Test
	void testMapEntityToDtoWithNullValues() {
		ReplyDto replyDto = this.replyMapper.mapEntityToDto(null, null);

		assertThat(replyDto).isNull();
	}

	@Test
	void testMapRequestToEntityForUpdate() {
		ReplyUpdateRequest updateRequest = new ReplyUpdateRequest();
		updateRequest.setId(UUID.randomUUID());
		updateRequest.setUserId(UUID.randomUUID());
		updateRequest.setContent("Updated content.");

		ReplyEntity replyEntity = new ReplyEntity();
		replyEntity.setId(UUID.randomUUID());
		replyEntity.setUserId(UUID.randomUUID());
		replyEntity.setContent("Old content.");

		this.replyMapper.mapRequestToEntity(updateRequest, replyEntity);

		assertThat(replyEntity).isNotNull();
		assertThat(replyEntity.getUserId()).isEqualTo(updateRequest.getUserId());
		assertThat(replyEntity.getContent()).isEqualTo(updateRequest.getContent());
	}

	@Test
	void testMapCreationRequestToEntity() {
		ReplyCreateRequest createRequest = new ReplyCreateRequest();
		createRequest.setTweetId(UUID.randomUUID());
		createRequest.setUserId(UUID.randomUUID());
		createRequest.setContent("Create request content.");

		ReplyEntity replyEntity = this.replyMapper.mapCreationRequestToEntity(createRequest);

		assertThat(replyEntity).isNotNull();
		assertThat(replyEntity.getTweetId()).isEqualTo(createRequest.getTweetId());
		assertThat(replyEntity.getUserId()).isEqualTo(createRequest.getUserId());
		assertThat(replyEntity.getContent()).isEqualTo(createRequest.getContent());
	}

	@Test
	void testMapCreationRequestToEntityWithNullRequest() {
		ReplyEntity replyEntity = this.replyMapper.mapCreationRequestToEntity(null);

		assertThat(replyEntity).isNull();
	}

}
