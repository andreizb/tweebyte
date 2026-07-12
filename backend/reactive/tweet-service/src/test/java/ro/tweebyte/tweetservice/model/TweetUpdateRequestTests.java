/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.model;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TweetUpdateRequestTests {

	@Test
	void testAllArgsConstructor() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String content = "This is a test tweet.";

		TweetUpdateRequest request = new TweetUpdateRequest(id, userId, content);

		assertThat(request.getId()).isEqualTo(id);
		assertThat(request.getUserId()).isEqualTo(userId);
		assertThat(request.getContent()).isEqualTo(content);
	}

	@Test
	void testBuilder() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String content = "This is a test tweet.";

		TweetUpdateRequest request = TweetUpdateRequest.builder().id(id).userId(userId).content(content).build();

		assertThat(request.getId()).isEqualTo(id);
		assertThat(request.getUserId()).isEqualTo(userId);
		assertThat(request.getContent()).isEqualTo(content);
	}

	@Test
	void testSettersAndGetters() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String content = "This is a test tweet.";

		TweetUpdateRequest request = new TweetUpdateRequest();
		request.setId(id);
		request.setUserId(userId);
		request.setContent(content);

		assertThat(request.getId()).isEqualTo(id);
		assertThat(request.getUserId()).isEqualTo(userId);
		assertThat(request.getContent()).isEqualTo(content);
	}

	@Test
	void testValidation_ContentTooShort() {
		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		Validator validator = factory.getValidator();

		TweetUpdateRequest request = TweetUpdateRequest.builder()
			.id(UUID.randomUUID())
			.userId(UUID.randomUUID())
			.content("short")
			.build();

		Set<ConstraintViolation<TweetUpdateRequest>> violations = validator.validate(request);

		assertThat(violations).isNotEmpty().hasSize(1);
		assertThat(violations.iterator().next().getMessage()).isEqualTo("Content must be at least 10 characters long");
	}

}
