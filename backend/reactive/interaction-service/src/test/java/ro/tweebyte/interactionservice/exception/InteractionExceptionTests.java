/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class InteractionExceptionTests {

	@Test
	void testInteractionExceptionWithCause() {
		Throwable cause = new Throwable("Test cause");
		InteractionException exception = new InteractionException(cause);

		assertThat(exception.getCause()).isNotNull();
		assertThat(exception.getCause()).isEqualTo(cause);
		assertThat(exception.getStatus()).isNull();
	}

	@Test
	void testInteractionExceptionWithMessage() {
		String message = "Test message";
		InteractionException exception = new InteractionException(message);

		assertThat(exception.getMessage()).isNotNull();
		assertThat(exception.getMessage()).isEqualTo(message);
		assertThat(exception.getStatus()).isNull();
	}

	@Test
	void testInteractionExceptionWithStatusAndMessage() {
		InteractionException exception = new InteractionException(HttpStatus.BAD_REQUEST, "Bad input");

		assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(exception.getMessage()).isEqualTo("Bad input");
	}

}
