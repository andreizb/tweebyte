/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetweetEntityTests {

	@Test
	void testConstructorAndGetters() {
		// Given
		UUID originalTweetId = UUID.randomUUID();
		UUID retweeterId = UUID.randomUUID();
		String content = "This is a retweet content.";
		UUID[] mediaIds = { UUID.randomUUID() };

		// When
		RetweetEntity retweetEntity = new RetweetEntity(originalTweetId, retweeterId, content, mediaIds);

		// Then
		assertThat(retweetEntity).isNotNull();
		assertThat(retweetEntity.getOriginalTweetId()).isEqualTo(originalTweetId);
		assertThat(retweetEntity.getRetweeterId()).isEqualTo(retweeterId);
		assertThat(retweetEntity.getContent()).isEqualTo(content);
		assertThat(retweetEntity.getMediaIds()).isEqualTo(mediaIds);
	}

	@Test
	void testSetters() {
		// Given
		RetweetEntity retweetEntity = new RetweetEntity();

		// When
		LocalDateTime createdAt = LocalDateTime.now();
		UUID id = UUID.randomUUID();
		UUID originalTweetId = UUID.randomUUID();
		UUID retweeterId = UUID.randomUUID();
		String content = "This is a retweet content.";

		retweetEntity.setId(id);
		retweetEntity.setCreatedAt(createdAt);
		retweetEntity.setOriginalTweetId(originalTweetId);
		retweetEntity.setRetweeterId(retweeterId);
		retweetEntity.setContent(content);

		// Then
		assertThat(retweetEntity).isNotNull();
		assertThat(retweetEntity.getId()).isEqualTo(id);
		assertThat(retweetEntity.getCreatedAt()).isEqualTo(createdAt);
		assertThat(retweetEntity.getOriginalTweetId()).isEqualTo(originalTweetId);
		assertThat(retweetEntity.getRetweeterId()).isEqualTo(retweeterId);
		assertThat(retweetEntity.getContent()).isEqualTo(content);
	}

	@Test
	void testBuilder() {
		// Given
		UUID originalTweetId = UUID.randomUUID();
		UUID retweeterId = UUID.randomUUID();
		String content = "This is a retweet content.";

		// When
		RetweetEntity retweetEntity = RetweetEntity.builder()
			.originalTweetId(originalTweetId)
			.retweeterId(retweeterId)
			.content(content)
			.build();

		// Then
		assertThat(retweetEntity).isNotNull();
		assertThat(retweetEntity.getOriginalTweetId()).isEqualTo(originalTweetId);
		assertThat(retweetEntity.getRetweeterId()).isEqualTo(retweeterId);
		assertThat(retweetEntity.getContent()).isEqualTo(content);
	}

}
