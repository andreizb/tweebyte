/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplyEntityTests {

	@Test
	void testConstructorAndGetters() {
		// Given
		UUID tweetId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String content = "This is a reply content.";
		UUID[] mediaIds = { UUID.randomUUID() };

		// When
		ReplyEntity replyEntity = new ReplyEntity(tweetId, userId, content, mediaIds);

		// Then
		assertThat(replyEntity).isNotNull();
		assertThat(replyEntity.getTweetId()).isEqualTo(tweetId);
		assertThat(replyEntity.getUserId()).isEqualTo(userId);
		assertThat(replyEntity.getContent()).isEqualTo(content);
		assertThat(replyEntity.getMediaIds()).isEqualTo(mediaIds);
	}

	@Test
	void testSetters() {
		// Given
		ReplyEntity replyEntity = new ReplyEntity();

		// When
		LocalDateTime createdAt = LocalDateTime.now();
		UUID id = UUID.randomUUID();
		UUID tweetId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String content = "This is a reply content.";

		replyEntity.setId(id);
		replyEntity.setCreatedAt(createdAt);
		replyEntity.setTweetId(tweetId);
		replyEntity.setUserId(userId);
		replyEntity.setContent(content);

		// Then
		assertThat(replyEntity).isNotNull();
		assertThat(replyEntity.getId()).isEqualTo(id);
		assertThat(replyEntity.getCreatedAt()).isEqualTo(createdAt);
		assertThat(replyEntity.getTweetId()).isEqualTo(tweetId);
		assertThat(replyEntity.getUserId()).isEqualTo(userId);
		assertThat(replyEntity.getContent()).isEqualTo(content);
	}

	@Test
	void testBuilder() {
		// Given
		UUID tweetId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String content = "This is a reply content.";

		// When
		ReplyEntity replyEntity = ReplyEntity.builder().tweetId(tweetId).userId(userId).content(content).build();

		// Then
		assertThat(replyEntity).isNotNull();
		assertThat(replyEntity.getTweetId()).isEqualTo(tweetId);
		assertThat(replyEntity.getUserId()).isEqualTo(userId);
		assertThat(replyEntity.getContent()).isEqualTo(content);
	}

}
