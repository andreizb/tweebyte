/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class UserExceptionTests {

	@Test
	void testConstructorWithCause() {
		Exception cause = new RuntimeException("Test cause");

		UserException userException = new UserException(cause);

		assertThat(userException).isNotNull();
		assertThat(userException.getCause()).isEqualTo(cause);
		assertThat(userException.getStatus()).isNull();
	}

	@Test
	void testConstructorWithMessage() {
		String message = "Test message";

		UserException userException = new UserException(message);

		assertThat(userException).isNotNull();
		assertThat(userException.getMessage()).isEqualTo(message);
		assertThat(userException.getStatus()).isNull();
	}

	@Test
	void testExceptionConstructorWithMessage() {
		String message = "User not found";

		UserException exception = new UserException(message);

		assertThat(exception).isNotNull();
		assertThat(exception.getMessage()).isEqualTo(message);
	}

	@Test
	void testExceptionConstructorWithCause() {
		Exception cause = new Exception("Database error");

		UserException exception = new UserException(cause);

		assertThat(exception).isNotNull();
		assertThat(exception.getCause()).isEqualTo(cause);
	}

	@Test
	void testConstructorWithStatusAndMessage() {
		UserException exception = new UserException(HttpStatus.BAD_REQUEST, "Bad input");

		assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(exception.getMessage()).isEqualTo("Bad input");
	}

}
