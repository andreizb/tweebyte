/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplyUpdateRequestTests {

	@Test
	void testGetterAndSetter() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String content = "Test content";

		ReplyUpdateRequest request = new ReplyUpdateRequest().setId(id).setUserId(userId).setContent(content);

		assertThat(request.getId()).isEqualTo(id);
		assertThat(request.getUserId()).isEqualTo(userId);
		assertThat(request.getContent()).isEqualTo(content);

		// Test setters
		UUID newId = UUID.randomUUID();
		UUID newUserId = UUID.randomUUID();
		String newContent = "New test content";

		request.setId(newId);
		request.setUserId(newUserId);
		request.setContent(newContent);

		assertThat(request.getId()).isEqualTo(newId);
		assertThat(request.getUserId()).isEqualTo(newUserId);
		assertThat(request.getContent()).isEqualTo(newContent);
	}

	@Test
	void testAllArgsConstructor() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String content = "Test content";

		ReplyUpdateRequest request = new ReplyUpdateRequest(id, userId, content);

		assertThat(request.getId()).isEqualTo(id);
		assertThat(request.getUserId()).isEqualTo(userId);
		assertThat(request.getContent()).isEqualTo(content);
	}

	@Test
	void testNoArgsConstructor() {
		ReplyUpdateRequest request = new ReplyUpdateRequest();

		assertThat(request).isNotNull();
	}

}
