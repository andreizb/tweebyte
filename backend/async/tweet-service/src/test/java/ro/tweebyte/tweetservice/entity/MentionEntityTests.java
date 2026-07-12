/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.entity;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MentionEntityTests {

	@Test
	void getId() {
		UUID id = UUID.randomUUID();
		MentionEntity mentionEntity = new MentionEntity();
		mentionEntity.setId(id);
		assertThat(mentionEntity.getId()).isEqualTo(id);
	}

	@Test
	void getUserId() {
		UUID userId = UUID.randomUUID();
		MentionEntity mentionEntity = new MentionEntity();
		mentionEntity.setUserId(userId);
		assertThat(mentionEntity.getUserId()).isEqualTo(userId);
	}

	@Test
	void getText() {
		String text = "mentionText";
		MentionEntity mentionEntity = new MentionEntity();
		mentionEntity.setText(text);
		assertThat(mentionEntity.getText()).isEqualTo(text);
	}

	@Test
	void getTweetEntity() {
		TweetEntity tweetEntity = new TweetEntity();
		MentionEntity mentionEntity = new MentionEntity();
		mentionEntity.setTweetEntity(tweetEntity);
		assertThat(mentionEntity.getTweetEntity()).isEqualTo(tweetEntity);
	}

	@Test
	void setId() {
		UUID id = UUID.randomUUID();
		MentionEntity mentionEntity = new MentionEntity();
		assertThat(mentionEntity.getId()).isNull(); // Initially null
		mentionEntity.setId(id);
		assertThat(mentionEntity.getId()).isEqualTo(id);
	}

	@Test
	void setUserId() {
		UUID userId = UUID.randomUUID();
		MentionEntity mentionEntity = new MentionEntity();
		assertThat(mentionEntity.getUserId()).isNull(); // Initially null
		mentionEntity.setUserId(userId);
		assertThat(mentionEntity.getUserId()).isEqualTo(userId);
	}

	@Test
	void setText() {
		String text = "mentionText";
		MentionEntity mentionEntity = new MentionEntity();
		assertThat(mentionEntity.getText()).isNull(); // Initially null
		mentionEntity.setText(text);
		assertThat(mentionEntity.getText()).isEqualTo(text);
	}

	@Test
	void setTweetEntity() {
		TweetEntity tweetEntity = new TweetEntity();
		MentionEntity mentionEntity = new MentionEntity();
		assertThat(mentionEntity.getTweetEntity()).isNull(); // Initially null
		mentionEntity.setTweetEntity(tweetEntity);
		assertThat(mentionEntity.getTweetEntity()).isEqualTo(tweetEntity);
	}

	@Test
	void builderTest() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String text = "@testUser";
		TweetEntity tweetEntity = new TweetEntity();

		MentionEntity mentionEntity = MentionEntity.builder()
			.id(id)
			.userId(userId)
			.text(text)
			.tweetEntity(tweetEntity)
			.build();

		assertThat(mentionEntity).isNotNull();
		assertThat(mentionEntity.getId()).isEqualTo(id);
		assertThat(mentionEntity.getUserId()).isEqualTo(userId);
		assertThat(mentionEntity.getText()).isEqualTo(text);
		assertThat(mentionEntity.getTweetEntity()).isEqualTo(tweetEntity);
	}

	@Test
	void allArgsConstructorTest() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String text = "@testUser";
		TweetEntity tweetEntity = new TweetEntity();

		MentionEntity mentionEntity = new MentionEntity(id, userId, text, tweetEntity);

		assertThat(mentionEntity).isNotNull();
		assertThat(mentionEntity.getId()).isEqualTo(id);
		assertThat(mentionEntity.getUserId()).isEqualTo(userId);
		assertThat(mentionEntity.getText()).isEqualTo(text);
		assertThat(mentionEntity.getTweetEntity()).isEqualTo(tweetEntity);
	}

}
