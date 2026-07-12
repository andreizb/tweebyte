/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.mapper;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import ro.tweebyte.tweetservice.entity.HashtagEntity;

import static org.assertj.core.api.Assertions.assertThat;

class HashtagMapperTests {

	private final HashtagMapper hashtagMapper = Mappers.getMapper(HashtagMapper.class);

	@Test
	void testMapTextToEntity() {
		// Given
		String hashtagText = "example";

		// When
		HashtagEntity hashtagEntity = this.hashtagMapper.mapTextToEntity(hashtagText);

		// Then
		assertThat(hashtagEntity.getText()).isEqualTo(hashtagText);
	}

	@Test
	void testMapTextToEntityWithNullText() {
		// Given
		String hashtagText = null;

		// When
		HashtagEntity hashtagEntity = this.hashtagMapper.mapTextToEntity(hashtagText);

		// Then
		assertThat(hashtagEntity).isNull();
	}

	@Test
	void testMapTextToEntityWithEmptyText() {
		String hashtagText = "";

		HashtagEntity hashtagEntity = this.hashtagMapper.mapTextToEntity(hashtagText);

		assertThat(hashtagEntity).isNotNull();
		assertThat(hashtagEntity.getText()).isEmpty();
	}

}
