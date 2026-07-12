/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetweetCreateRequestTests {

	@Test
	void testGetterAndSetter() {
		UUID originalTweetId = UUID.randomUUID();
		UUID retweeterId = UUID.randomUUID();
		String content = "Test content";

		RetweetCreateRequest request = new RetweetCreateRequest().setOriginalTweetId(originalTweetId)
			.setRetweeterId(retweeterId)
			.setContent(content);

		assertThat(request.getOriginalTweetId()).isEqualTo(originalTweetId);
		assertThat(request.getRetweeterId()).isEqualTo(retweeterId);
		assertThat(request.getContent()).isEqualTo(content);

		// Test setters
		UUID newOriginalTweetId = UUID.randomUUID();
		UUID newRetweeterId = UUID.randomUUID();
		String newContent = "New test content";

		request.setOriginalTweetId(newOriginalTweetId);
		request.setRetweeterId(newRetweeterId);
		request.setContent(newContent);

		assertThat(request.getOriginalTweetId()).isEqualTo(newOriginalTweetId);
		assertThat(request.getRetweeterId()).isEqualTo(newRetweeterId);
		assertThat(request.getContent()).isEqualTo(newContent);
	}

	@Test
	void testAllArgsConstructor() {
		UUID originalTweetId = UUID.randomUUID();
		UUID retweeterId = UUID.randomUUID();
		String content = "Test content";
		UUID[] mediaIds = { UUID.randomUUID() };

		RetweetCreateRequest request = new RetweetCreateRequest(originalTweetId, retweeterId, content, mediaIds);

		assertThat(request.getOriginalTweetId()).isEqualTo(originalTweetId);
		assertThat(request.getRetweeterId()).isEqualTo(retweeterId);
		assertThat(request.getContent()).isEqualTo(content);
		assertThat(request.getMediaIds()).isEqualTo(mediaIds);
	}

	@Test
	void testNoArgsConstructor() {
		RetweetCreateRequest request = new RetweetCreateRequest();

		assertThat(request).isNotNull();
	}

}
