/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplyCreateRequestTests {

	@Test
	void testGetterAndSetter() {
		UUID tweetId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String content = "Test content";

		ReplyCreateRequest replyCreateRequest = new ReplyCreateRequest().setTweetId(tweetId)
			.setUserId(userId)
			.setContent(content);

		assertThat(replyCreateRequest.getTweetId()).isEqualTo(tweetId);
		assertThat(replyCreateRequest.getUserId()).isEqualTo(userId);
		assertThat(replyCreateRequest.getContent()).isEqualTo(content);

		// Test setters
		UUID newTweetId = UUID.randomUUID();
		UUID newUserId = UUID.randomUUID();
		String newContent = "New test content";

		replyCreateRequest.setTweetId(newTweetId);
		replyCreateRequest.setUserId(newUserId);
		replyCreateRequest.setContent(newContent);

		assertThat(replyCreateRequest.getTweetId()).isEqualTo(newTweetId);
		assertThat(replyCreateRequest.getUserId()).isEqualTo(newUserId);
		assertThat(replyCreateRequest.getContent()).isEqualTo(newContent);
	}

	@Test
	void testAllArgsConstructor() {
		UUID tweetId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String content = "Test content";
		UUID[] mediaIds = { UUID.randomUUID() };

		ReplyCreateRequest replyCreateRequest = new ReplyCreateRequest(tweetId, userId, content, mediaIds);

		assertThat(replyCreateRequest.getTweetId()).isEqualTo(tweetId);
		assertThat(replyCreateRequest.getUserId()).isEqualTo(userId);
		assertThat(replyCreateRequest.getContent()).isEqualTo(content);
		assertThat(replyCreateRequest.getMediaIds()).isEqualTo(mediaIds);
	}

	@Test
	void testNoArgsConstructor() {
		ReplyCreateRequest replyCreateRequest = new ReplyCreateRequest();

		assertThat(replyCreateRequest).isNotNull();
	}

}
