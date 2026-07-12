/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.entity;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashtagEntityTests {

	@Test
	void testBuilder() {
		UUID id = UUID.randomUUID();
		HashtagEntity hashtagEntity = HashtagEntity.builder().id(id).text("#example").isInsertable(true).build();

		assertThat(hashtagEntity.getId()).isEqualTo(id);
		assertThat(hashtagEntity.getText()).isEqualTo("#example");
		assertThat(hashtagEntity.isNew()).isTrue();
	}

	@Test
	void testNoArgsConstructor() {
		HashtagEntity hashtagEntity = new HashtagEntity();

		assertThat(hashtagEntity.getId()).isNull();
		assertThat(hashtagEntity.getText()).isNull();
		assertThat(hashtagEntity.isInsertable()).isFalse();
	}

	@Test
	void testAllArgsConstructor() {
		UUID id = UUID.randomUUID();
		HashtagEntity hashtagEntity = new HashtagEntity(id, "#example", true);

		assertThat(hashtagEntity.getId()).isEqualTo(id);
		assertThat(hashtagEntity.getText()).isEqualTo("#example");
		assertThat(hashtagEntity.isInsertable()).isTrue();
	}

	@Test
	void testSettersAndGetters() {
		HashtagEntity hashtagEntity = new HashtagEntity();
		UUID id = UUID.randomUUID();
		hashtagEntity.setId(id);
		hashtagEntity.setText("#example");
		hashtagEntity.setInsertable(true);

		assertThat(hashtagEntity.getId()).isEqualTo(id);
		assertThat(hashtagEntity.getText()).isEqualTo("#example");
		assertThat(hashtagEntity.isInsertable()).isTrue();
	}

	@Test
	void testIsNewWithNullId() {
		HashtagEntity hashtagEntity = new HashtagEntity();
		hashtagEntity.setInsertable(false);
		assertThat(hashtagEntity.isNew()).isTrue();
	}

	@Test
	void testIsNewWithNonNullId() {
		UUID id = UUID.randomUUID();
		HashtagEntity hashtagEntity = new HashtagEntity();
		hashtagEntity.setId(id);
		hashtagEntity.setInsertable(false);

		assertThat(hashtagEntity.isNew()).isFalse();
	}

}
