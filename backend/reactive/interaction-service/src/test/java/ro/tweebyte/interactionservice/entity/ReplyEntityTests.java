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
	void noArgsConstructorYieldsNullFields() {
		ReplyEntity entity = new ReplyEntity();
		assertThat(entity).isNotNull();
		assertThat(entity.getId()).isNull();
		assertThat(entity.getTweetId()).isNull();
		assertThat(entity.getUserId()).isNull();
		assertThat(entity.getContent()).isNull();
	}

	@Test
	void allArgsConstructor() {
		UUID id = UUID.randomUUID();
		UUID tweetId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		LocalDateTime now = LocalDateTime.now();
		UUID[] mediaIds = { UUID.randomUUID() };
		ReplyEntity entity = new ReplyEntity(id, now, tweetId, userId, "hello", mediaIds, true);
		assertThat(entity.getId()).isEqualTo(id);
		assertThat(entity.getCreatedAt()).isEqualTo(now);
		assertThat(entity.getTweetId()).isEqualTo(tweetId);
		assertThat(entity.getUserId()).isEqualTo(userId);
		assertThat(entity.getContent()).isEqualTo("hello");
		assertThat(entity.getMediaIds()).isEqualTo(mediaIds);
		assertThat(entity.isInsertable()).isTrue();
	}

	@Test
	void builderPopulatesFields() {
		UUID id = UUID.randomUUID();
		ReplyEntity entity = ReplyEntity.builder().id(id).content("text").build();
		assertThat(entity.getId()).isEqualTo(id);
		assertThat(entity.getContent()).isEqualTo("text");
	}

	@Test
	void isNewBranchInsertableTrue() {
		ReplyEntity e = new ReplyEntity();
		e.setId(UUID.randomUUID());
		e.setInsertable(true);
		assertThat(e.isNew()).isTrue();
	}

	@Test
	void isNewBranchIdNull() {
		ReplyEntity e = new ReplyEntity();
		assertThat(e.isNew()).isTrue();
	}

	@Test
	void isNewBranchIdSetAndNotInsertable() {
		ReplyEntity e = new ReplyEntity();
		e.setId(UUID.randomUUID());
		e.setInsertable(false);
		assertThat(e.isNew()).isFalse();
	}

	@Test
	void settersUpdateState() {
		ReplyEntity e = new ReplyEntity();
		UUID id = UUID.randomUUID();
		e.setTweetId(id);
		e.setUserId(id);
		e.setContent("c");
		assertThat(e.getTweetId()).isEqualTo(id);
		assertThat(e.getUserId()).isEqualTo(id);
		assertThat(e.getContent()).isEqualTo("c");
	}

}
