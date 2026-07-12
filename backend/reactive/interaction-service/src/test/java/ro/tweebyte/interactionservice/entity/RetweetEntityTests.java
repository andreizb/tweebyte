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
	void noArgsConstructorYieldsNullFields() {
		RetweetEntity entity = new RetweetEntity();
		assertThat(entity).isNotNull();
		assertThat(entity.getId()).isNull();
		assertThat(entity.getOriginalTweetId()).isNull();
		assertThat(entity.getRetweeterId()).isNull();
		assertThat(entity.getContent()).isNull();
	}

	@Test
	void allArgsConstructor() {
		UUID id = UUID.randomUUID();
		UUID original = UUID.randomUUID();
		UUID retweeter = UUID.randomUUID();
		LocalDateTime now = LocalDateTime.now();
		UUID[] mediaIds = { UUID.randomUUID() };
		RetweetEntity entity = new RetweetEntity(id, now, original, retweeter, "rt", mediaIds, true);
		assertThat(entity.getId()).isEqualTo(id);
		assertThat(entity.getCreatedAt()).isEqualTo(now);
		assertThat(entity.getOriginalTweetId()).isEqualTo(original);
		assertThat(entity.getRetweeterId()).isEqualTo(retweeter);
		assertThat(entity.getContent()).isEqualTo("rt");
		assertThat(entity.getMediaIds()).isEqualTo(mediaIds);
		assertThat(entity.isInsertable()).isTrue();
	}

	@Test
	void builderPopulatesFields() {
		UUID original = UUID.randomUUID();
		RetweetEntity entity = RetweetEntity.builder().originalTweetId(original).content("hello").build();
		assertThat(entity.getOriginalTweetId()).isEqualTo(original);
		assertThat(entity.getContent()).isEqualTo("hello");
	}

	@Test
	void isNewBranchInsertableTrue() {
		RetweetEntity e = new RetweetEntity();
		e.setId(UUID.randomUUID());
		e.setInsertable(true);
		assertThat(e.isNew()).isTrue();
	}

	@Test
	void isNewBranchIdNull() {
		RetweetEntity e = new RetweetEntity();
		assertThat(e.isNew()).isTrue();
	}

	@Test
	void isNewBranchIdSetAndNotInsertable() {
		RetweetEntity e = new RetweetEntity();
		e.setId(UUID.randomUUID());
		e.setInsertable(false);
		assertThat(e.isNew()).isFalse();
	}

	@Test
	void settersUpdateState() {
		RetweetEntity e = new RetweetEntity();
		UUID id = UUID.randomUUID();
		e.setOriginalTweetId(id);
		e.setRetweeterId(id);
		e.setContent("c");
		assertThat(e.getOriginalTweetId()).isEqualTo(id);
		assertThat(e.getRetweeterId()).isEqualTo(id);
		assertThat(e.getContent()).isEqualTo("c");
	}

}
