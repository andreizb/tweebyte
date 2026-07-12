/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.model;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MentionDtoTests {

	@Test
	void testGetId() {
		UUID id = UUID.randomUUID();
		MentionDto mentionDto = new MentionDto();
		mentionDto.setId(id);
		assertThat(mentionDto.getId()).isEqualTo(id);
	}

	@Test
	void testGetUserId() {
		UUID userId = UUID.randomUUID();
		MentionDto mentionDto = new MentionDto();
		mentionDto.setUserId(userId);
		assertThat(mentionDto.getUserId()).isEqualTo(userId);
	}

	@Test
	void testGetText() {
		String text = "example";
		MentionDto mentionDto = new MentionDto();
		mentionDto.setText(text);
		assertThat(mentionDto.getText()).isEqualTo(text);
	}

	@Test
	void testAllArgsConstructor() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String text = "example";

		MentionDto mentionDto = new MentionDto(id, userId, text);

		assertThat(mentionDto.getId()).isEqualTo(id);
		assertThat(mentionDto.getUserId()).isEqualTo(userId);
		assertThat(mentionDto.getText()).isEqualTo(text);
	}

	@Test
	void testBuilder() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String text = "example";

		MentionDto mentionDto = MentionDto.builder().id(id).userId(userId).text(text).build();

		assertThat(mentionDto.getId()).isEqualTo(id);
		assertThat(mentionDto.getUserId()).isEqualTo(userId);
		assertThat(mentionDto.getText()).isEqualTo(text);
	}

}
