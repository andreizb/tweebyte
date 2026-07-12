/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.model;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashtagDtoTests {

	@Test
	void testGetId() {
		UUID id = UUID.randomUUID();
		HashtagDto hashtagDto = new HashtagDto();
		hashtagDto.setId(id);
		assertThat(hashtagDto.getId()).isEqualTo(id);
	}

	@Test
	void testGetText() {
		String text = "example";
		HashtagDto hashtagDto = new HashtagDto();
		hashtagDto.setText(text);
		assertThat(hashtagDto.getText()).isEqualTo(text);
	}

	@Test
	void testGetCount() {
		Long count = 5L;
		HashtagDto hashtagDto = new HashtagDto();
		hashtagDto.setCount(count);
		assertThat(hashtagDto.getCount()).isEqualTo(count);
	}

	@Test
	void testAllArgsConstructor() {
		UUID id = UUID.randomUUID();
		String text = "example";
		Long count = 5L;

		HashtagDto hashtagDto = new HashtagDto(id, text, count);

		assertThat(hashtagDto.getId()).isEqualTo(id);
		assertThat(hashtagDto.getText()).isEqualTo(text);
		assertThat(hashtagDto.getCount()).isEqualTo(count);
	}

	@Test
	void testBuilder() {
		UUID id = UUID.randomUUID();
		String text = "example";
		Long count = 5L;

		HashtagDto hashtagDto = HashtagDto.builder().id(id).text(text).count(count).build();

		assertThat(hashtagDto.getId()).isEqualTo(id);
		assertThat(hashtagDto.getText()).isEqualTo(text);
		assertThat(hashtagDto.getCount()).isEqualTo(count);
	}

}
