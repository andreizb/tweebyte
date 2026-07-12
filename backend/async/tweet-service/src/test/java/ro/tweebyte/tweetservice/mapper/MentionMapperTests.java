/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.mapper;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;

import static org.assertj.core.api.Assertions.assertThat;

class MentionMapperTests {

	private final MentionMapper mentionMapper = Mappers.getMapper(MentionMapper.class);

	@Test
	void testMapFieldsToEntity() {
		// Given
		UUID userId = UUID.randomUUID();
		String text = "mentionText";
		TweetEntity tweetEntity = new TweetEntity();

		// When
		MentionEntity mentionEntity = this.mentionMapper.mapFieldsToEntity(userId, text, tweetEntity);

		// Then
		assertThat(mentionEntity.getUserId()).isEqualTo(userId);
		assertThat(mentionEntity.getText()).isEqualTo(text);
		assertThat(mentionEntity.getTweetEntity()).isEqualTo(tweetEntity);
	}

	@Test
	void testMapFieldsToEntityWithoutTweetEntity() {
		// Given
		UUID userId = UUID.randomUUID();
		String text = "mentionText";

		// When
		MentionEntity mentionEntity = this.mentionMapper.mapFieldsToEntity(userId, text);

		// Then
		assertThat(mentionEntity.getUserId()).isEqualTo(userId);
		assertThat(mentionEntity.getText()).isEqualTo(text);
		assertThat(mentionEntity.getTweetEntity()).isNull();
	}

}
