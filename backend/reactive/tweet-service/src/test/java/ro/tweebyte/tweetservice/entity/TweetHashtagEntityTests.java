/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.entity;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TweetHashtagEntityTests {

	@Test
	void testBuilder() {
		UUID tweetId = UUID.randomUUID();
		UUID hashtagId = UUID.randomUUID();
		TweetHashtagEntity tweetHashtagEntity = TweetHashtagEntity.builder()
			.tweetId(tweetId)
			.hashtagId(hashtagId)
			.build();

		assertThat(tweetHashtagEntity.getTweetId()).isEqualTo(tweetId);
		assertThat(tweetHashtagEntity.getHashtagId()).isEqualTo(hashtagId);
	}

	@Test
	void testNoArgsConstructor() {
		TweetHashtagEntity tweetHashtagEntity = new TweetHashtagEntity();

		assertThat(tweetHashtagEntity.getTweetId()).isNull();
		assertThat(tweetHashtagEntity.getHashtagId()).isNull();
	}

	@Test
	void testAllArgsConstructor() {
		UUID tweetId = UUID.randomUUID();
		UUID hashtagId = UUID.randomUUID();
		TweetHashtagEntity tweetHashtagEntity = new TweetHashtagEntity(tweetId, hashtagId);

		assertThat(tweetHashtagEntity.getTweetId()).isEqualTo(tweetId);
		assertThat(tweetHashtagEntity.getHashtagId()).isEqualTo(hashtagId);
	}

	@Test
	void testSetters() {
		TweetHashtagEntity tweetHashtagEntity = new TweetHashtagEntity();
		UUID tweetId = UUID.randomUUID();
		UUID hashtagId = UUID.randomUUID();

		tweetHashtagEntity.setTweetId(tweetId);
		tweetHashtagEntity.setHashtagId(hashtagId);

		assertThat(tweetHashtagEntity.getTweetId()).isEqualTo(tweetId);
		assertThat(tweetHashtagEntity.getHashtagId()).isEqualTo(hashtagId);
	}

}
