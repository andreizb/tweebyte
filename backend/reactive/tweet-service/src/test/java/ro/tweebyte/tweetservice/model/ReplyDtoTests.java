/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplyDtoTests {

	@Test
	void testLombokGeneratedMethods() {
		UUID replyId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();
		List<LikeDto> likes = List.of(new LikeDto(UUID.randomUUID()));

		ReplyDto replyDto1 = ReplyDto.builder()
			.id(replyId)
			.userId(userId)
			.userName("test_user")
			.content("This is a reply")
			.createdAt(createdAt)
			.likesCount(5L)
			.likes(likes)
			.build();

		ReplyDto replyDto2 = ReplyDto.builder()
			.id(replyId)
			.userId(userId)
			.userName("test_user")
			.content("This is a reply")
			.createdAt(createdAt)
			.likesCount(5L)
			.likes(likes)
			.build();

		assertThat(replyDto2).isEqualTo(replyDto1).hasSameHashCodeAs(replyDto1);
		assertThat(replyDto1.toString()).isNotNull();
		assertThat(replyDto1.toString()).contains("ReplyDto");

		assertThat(replyDto1.getId()).isEqualTo(replyId);
		assertThat(replyDto1.getUserId()).isEqualTo(userId);
		assertThat(replyDto1.getUserName()).isEqualTo("test_user");
		assertThat(replyDto1.getContent()).isEqualTo("This is a reply");
		assertThat(replyDto1.getCreatedAt()).isEqualTo(createdAt);
		assertThat(replyDto1.getLikesCount()).isEqualTo(5L);
		assertThat(replyDto1.getLikes()).isEqualTo(likes);
	}

	@Test
	void testBuilderPattern() {
		UUID replyId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();
		List<LikeDto> likes = List.of(new LikeDto(UUID.randomUUID()));

		ReplyDto replyDto = ReplyDto.builder()
			.id(replyId)
			.userId(userId)
			.userName("test_user")
			.content("This is a reply")
			.createdAt(createdAt)
			.likesCount(10L)
			.likes(likes)
			.build();

		assertThat(replyDto).isNotNull();
		assertThat(replyDto.getId()).isEqualTo(replyId);
		assertThat(replyDto.getUserId()).isEqualTo(userId);
		assertThat(replyDto.getUserName()).isEqualTo("test_user");
		assertThat(replyDto.getContent()).isEqualTo("This is a reply");
		assertThat(replyDto.getCreatedAt()).isEqualTo(createdAt);
		assertThat(replyDto.getLikesCount()).isEqualTo(10L);
		assertThat(replyDto.getLikes()).isEqualTo(likes);
	}

	@Test
	void testNullValues() {
		ReplyDto replyDto = new ReplyDto();

		assertThat(replyDto.getId()).isNull();
		assertThat(replyDto.getUserId()).isNull();
		assertThat(replyDto.getUserName()).isNull();
		assertThat(replyDto.getContent()).isNull();
		assertThat(replyDto.getCreatedAt()).isNull();
		assertThat(replyDto.getLikesCount()).isNull();
		assertThat(replyDto.getLikes()).isNull();
	}

}
