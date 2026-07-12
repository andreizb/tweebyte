/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetweetUpdateRequestTests {

	@Test
	void testGetterAndSetter() {
		UUID id = UUID.randomUUID();
		UUID retweeterId = UUID.randomUUID();
		String content = "Test content";

		RetweetUpdateRequest retweetUpdateRequest = new RetweetUpdateRequest().setId(id)
			.setRetweeterId(retweeterId)
			.setContent(content);

		assertThat(retweetUpdateRequest.getId()).isEqualTo(id);
		assertThat(retweetUpdateRequest.getRetweeterId()).isEqualTo(retweeterId);
		assertThat(retweetUpdateRequest.getContent()).isEqualTo(content);

		// Test setters
		UUID newId = UUID.randomUUID();
		UUID newRetweeterId = UUID.randomUUID();
		String newContent = "New test content";

		retweetUpdateRequest.setId(newId);
		retweetUpdateRequest.setRetweeterId(newRetweeterId);
		retweetUpdateRequest.setContent(newContent);

		assertThat(retweetUpdateRequest.getId()).isEqualTo(newId);
		assertThat(retweetUpdateRequest.getRetweeterId()).isEqualTo(newRetweeterId);
		assertThat(retweetUpdateRequest.getContent()).isEqualTo(newContent);
	}

	@Test
	void testAllArgsConstructor() {
		UUID id = UUID.randomUUID();
		UUID retweeterId = UUID.randomUUID();
		String content = "Test content";

		RetweetUpdateRequest retweetUpdateRequest = new RetweetUpdateRequest(id, retweeterId, content);

		assertThat(retweetUpdateRequest.getId()).isEqualTo(id);
		assertThat(retweetUpdateRequest.getRetweeterId()).isEqualTo(retweeterId);
		assertThat(retweetUpdateRequest.getContent()).isEqualTo(content);
	}

	@Test
	void testNoArgsConstructor() {
		RetweetUpdateRequest retweetUpdateRequest = new RetweetUpdateRequest();

		assertThat(retweetUpdateRequest).isNotNull();
	}

}
