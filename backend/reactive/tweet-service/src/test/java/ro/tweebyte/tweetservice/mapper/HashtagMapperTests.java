/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.mapper;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.model.HashtagDto;

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
		assertThat(hashtagEntity).isNotNull();
		assertThat(hashtagEntity.getText()).isEqualTo(hashtagText);
	}

	@Test
	void testMapTextToEntityWithNullText() {
		// Given
		String hashtagText = null;

		// When
		HashtagEntity hashtagEntity = this.hashtagMapper.mapTextToEntity(hashtagText);

		// Then
		assertThat(hashtagEntity).isNotNull();
		assertThat(hashtagEntity.getText()).isNull();
	}

	@Test
	void testMapTextToEntityWithEmptyText() {
		// Given
		String hashtagText = "";

		// When
		HashtagEntity hashtagEntity = this.hashtagMapper.mapTextToEntity(hashtagText);

		// Then
		assertThat(hashtagEntity).isNotNull();
		assertThat(hashtagEntity.getText()).isEmpty();
	}

	@Test
	void testMapEntityToDto() {
		// Given
		UUID id = UUID.randomUUID();
		String text = "example";
		HashtagEntity hashtagEntity = new HashtagEntity();
		hashtagEntity.setId(id);
		hashtagEntity.setText(text);

		// When
		HashtagDto hashtagDto = this.hashtagMapper.mapEntityToDto(hashtagEntity);

		// Then
		assertThat(hashtagDto).isNotNull();
		assertThat(hashtagDto.getId()).isEqualTo(id);
		assertThat(hashtagDto.getText()).isEqualTo(text);
	}

	@Test
	void testMapEntityToDtoWithNullEntity() {
		// Given
		HashtagEntity hashtagEntity = null;

		// When
		HashtagDto hashtagDto = this.hashtagMapper.mapEntityToDto(hashtagEntity);

		// Then
		assertThat(hashtagDto).isNull();
	}

	@Test
	void testMapEntityToDtoWithEmptyEntity() {
		// Given
		HashtagEntity hashtagEntity = new HashtagEntity();

		// When
		HashtagDto hashtagDto = this.hashtagMapper.mapEntityToDto(hashtagEntity);

		// Then
		assertThat(hashtagDto).isNotNull();
		assertThat(hashtagDto.getId()).isNull();
		assertThat(hashtagDto.getText()).isNull();
	}

}
