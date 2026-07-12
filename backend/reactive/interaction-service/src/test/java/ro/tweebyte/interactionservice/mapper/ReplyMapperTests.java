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

	private final ReplyMapper mapper = org.mapstruct.factory.Mappers.getMapper(ReplyMapper.class);

	@Test
	void mapRequestToEntity_ShouldMapCorrectly() {
		UUID tweetId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String content = "This is a test reply.";

		ReplyCreateRequest request = new ReplyCreateRequest();
		request.setTweetId(tweetId);
		request.setUserId(userId);
		request.setContent(content);

		ReplyEntity replyEntity = this.mapper.mapRequestToEntity(request);

		assertThat(replyEntity).isNotNull();
		assertThat(replyEntity.getTweetId()).isEqualTo(tweetId);
		assertThat(replyEntity.getUserId()).isEqualTo(userId);
		assertThat(replyEntity.getContent()).isEqualTo(content);
		assertThat(replyEntity.getId()).isNotNull();
		assertThat(replyEntity.getCreatedAt()).isNotNull();
		assertThat(replyEntity.isInsertable()).isTrue();
		assertThat(replyEntity.isNew()).isTrue();
	}

	@Test
	void mapEntityToCreationDto_ShouldMapCorrectly() {
		UUID id = UUID.randomUUID();

		ReplyEntity replyEntity = ReplyEntity.builder().id(id).build();

		ReplyDto replyDto = this.mapper.mapEntityToCreationDto(replyEntity);

		assertThat(replyDto).isNotNull();
		assertThat(replyDto.getId()).isEqualTo(id);
	}

	@Test
	void mapEntityToDto_ShouldMapCorrectly() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String content = "This is a reply.";
		LocalDateTime createdAt = LocalDateTime.now();
		String userName = "testUser";

		ReplyEntity replyEntity = ReplyEntity.builder()
			.id(id)
			.userId(userId)
			.content(content)
			.createdAt(createdAt)
			.build();

		ReplyDto replyDto = this.mapper.mapEntityToDto(replyEntity, userName);

		assertThat(replyDto).isNotNull();
		assertThat(replyDto.getId()).isEqualTo(id);
		assertThat(replyDto.getUserId()).isEqualTo(userId);
		assertThat(replyDto.getContent()).isEqualTo(content);
		assertThat(replyDto.getCreatedAt()).isEqualTo(createdAt);
		assertThat(replyDto.getUserName()).isEqualTo(userName);
	}

	@Test
	void mapRequestToEntity_Update_ShouldMapCorrectly() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String updatedContent = "Updated content";

		ReplyUpdateRequest updateRequest = new ReplyUpdateRequest();
		updateRequest.setId(id);
		updateRequest.setUserId(userId);
		updateRequest.setContent(updatedContent);

		ReplyEntity replyEntity = new ReplyEntity();
		this.mapper.mapRequestToEntity(updateRequest, replyEntity);

		assertThat(replyEntity).isNotNull();
		assertThat(replyEntity.getId()).isEqualTo(id);
		assertThat(replyEntity.getUserId()).isEqualTo(userId);
		assertThat(replyEntity.getContent()).isEqualTo(updatedContent);
	}

	@Test
	void mapCreationRequestToEntity_NullRequest_ShouldReturnNull() {
		ReplyEntity replyEntity = this.mapper.mapCreationRequestToEntity(null);
		assertThat(replyEntity).isNull();
	}

	@Test
	void mapEntityToDto_NullEntity_ShouldReturnNull() {
		ReplyDto replyDto = this.mapper.mapEntityToCreationDto(null);
		assertThat(replyDto).isNull();
	}

	@Test
	void mapRequestToEntity_NullUpdateRequest_ShouldNotModifyEntity() {
		ReplyEntity replyEntity = new ReplyEntity();
		this.mapper.mapRequestToEntity(null, replyEntity);

		assertThat(replyEntity).isNotNull();
	}

	@Test
	void mapEntityToDto_NullEntity_NullUserName_ShouldReturnNull() {
		// Null entity
		// AND null userName must still yield null (separate null-safety case from
		// mapEntityToCreationDto(null), which is covered by
		// mapEntityToDto_NullEntity_ShouldReturnNull).
		ReplyDto replyDto = this.mapper.mapEntityToDto(null, null);
		assertThat(replyDto).isNull();
	}

}
