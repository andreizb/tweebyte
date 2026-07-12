/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplyDtoTests {

	@Test
	void testGetterAndSetter() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String userName = "TestUser";
		String content = "Test content";
		LocalDateTime createdAt = LocalDateTime.now();
		Long likesCount = 42L;

		List<LikeDto> likes = new ArrayList<>();
		likes.add(new LikeDto());

		ReplyDto replyDto = new ReplyDto().setId(id)
			.setUserId(userId)
			.setUserName(userName)
			.setContent(content)
			.setCreatedAt(createdAt)
			.setLikesCount(likesCount)
			.setLikes(likes);

		assertThat(replyDto.getId()).isEqualTo(id);
		assertThat(replyDto.getUserId()).isEqualTo(userId);
		assertThat(replyDto.getUserName()).isEqualTo(userName);
		assertThat(replyDto.getContent()).isEqualTo(content);
		assertThat(replyDto.getCreatedAt()).isEqualTo(createdAt);
		assertThat(replyDto.getLikesCount()).isEqualTo(likesCount);
		assertThat(replyDto.getLikes()).isEqualTo(likes);

		// Test setters
		UUID newId = UUID.randomUUID();
		UUID newUserId = UUID.randomUUID();
		String newUserName = "NewUser";
		String newContent = "New test content";
		LocalDateTime newCreatedAt = LocalDateTime.now().minusHours(1);
		Long newLikesCount = 100L;

		List<LikeDto> newLikes = new ArrayList<>();
		newLikes.add(new LikeDto());

		replyDto.setId(newId);
		replyDto.setUserId(newUserId);
		replyDto.setUserName(newUserName);
		replyDto.setContent(newContent);
		replyDto.setCreatedAt(newCreatedAt);
		replyDto.setLikesCount(newLikesCount);
		replyDto.setLikes(newLikes);

		assertThat(replyDto.getId()).isEqualTo(newId);
		assertThat(replyDto.getUserId()).isEqualTo(newUserId);
		assertThat(replyDto.getUserName()).isEqualTo(newUserName);
		assertThat(replyDto.getContent()).isEqualTo(newContent);
		assertThat(replyDto.getCreatedAt()).isEqualTo(newCreatedAt);
		assertThat(replyDto.getLikesCount()).isEqualTo(newLikesCount);
		assertThat(replyDto.getLikes()).isEqualTo(newLikes);
	}

	@Test
	void testAllArgsConstructor() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String content = "Test content";
		LocalDateTime createdAt = LocalDateTime.now();
		Long likesCount = 42L;

		ReplyDto replyDto = new ReplyDto(id, userId, content, createdAt, likesCount);

		assertThat(replyDto.getId()).isEqualTo(id);
		assertThat(replyDto.getUserId()).isEqualTo(userId);
		assertThat(replyDto.getContent()).isEqualTo(content);
		assertThat(replyDto.getCreatedAt()).isEqualTo(createdAt);
		assertThat(replyDto.getLikesCount()).isEqualTo(likesCount);
	}

	@Test
	void testNoArgsConstructor() {
		ReplyDto replyDto = new ReplyDto();

		assertThat(replyDto).isNotNull();
	}

}
