/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.model;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TweetCreationRequestTests {

	@Test
	void getId() {
		UUID id = UUID.randomUUID();
		TweetCreationRequest request = new TweetCreationRequest();
		request.setId(id);
		assertThat(request.getId()).isEqualTo(id);
	}

	@Test
	void getUserId() {
		UUID userId = UUID.randomUUID();
		TweetCreationRequest request = new TweetCreationRequest();
		request.setUserId(userId);
		assertThat(request.getUserId()).isEqualTo(userId);
	}

	@Test
	void getContent() {
		String content = "Test tweet content";
		TweetCreationRequest request = new TweetCreationRequest();
		request.setContent(content);
		assertThat(request.getContent()).isEqualTo(content);
	}

	@Test
	void setId() {
		UUID id = UUID.randomUUID();
		TweetCreationRequest request = new TweetCreationRequest();
		assertThat(request.getId()).isNull(); // Initially null
		request.setId(id);
		assertThat(request.getId()).isEqualTo(id);
	}

	@Test
	void setUserId() {
		UUID userId = UUID.randomUUID();
		TweetCreationRequest request = new TweetCreationRequest();
		assertThat(request.getUserId()).isNull(); // Initially null
		request.setUserId(userId);
		assertThat(request.getUserId()).isEqualTo(userId);
	}

	@Test
	void setContent() {
		String content = "Test tweet content";
		TweetCreationRequest request = new TweetCreationRequest();
		assertThat(request.getContent()).isNull(); // Initially null
		request.setContent(content);
		assertThat(request.getContent()).isEqualTo(content);
	}

}
