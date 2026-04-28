package ro.tweebyte.tweetservice.model;

import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TweetUpdateRequestTest {

	@Test
	void testAllArgsConstructor() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String content = "This is a test tweet.";

		TweetUpdateRequest request = new TweetUpdateRequest(id, userId, content);

		assertEquals(id, request.getId());
		assertEquals(userId, request.getUserId());
		assertEquals(content, request.getContent());
	}

	@Test
	void testBuilder() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String content = "This is a test tweet.";

		TweetUpdateRequest request = TweetUpdateRequest.builder()
			.id(id)
			.userId(userId)
			.content(content)
			.build();

		assertEquals(id, request.getId());
		assertEquals(userId, request.getUserId());
		assertEquals(content, request.getContent());
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

		assertEquals(id, request.getId());
		assertEquals(userId, request.getUserId());
		assertEquals(content, request.getContent());
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

		assertFalse(violations.isEmpty());
		assertEquals(1, violations.size());
		assertEquals("Content must be at least 10 characters long", violations.iterator().next().getMessage());
	}

}
