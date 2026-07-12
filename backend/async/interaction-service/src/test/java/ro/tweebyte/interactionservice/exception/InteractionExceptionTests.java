/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/** Mirrors reactive/.../exception/InteractionExceptionTest. */
class InteractionExceptionTests {

	@Test
	void withCause() {
		Throwable cause = new RuntimeException("Root");
		InteractionException ex = new InteractionException((Exception) cause);
		assertThat(ex.getCause()).isEqualTo(cause);
		assertThat(ex.getStatus()).isNull();
	}

	@Test
	void withMessage() {
		InteractionException ex = new InteractionException("explicit message");
		assertThat(ex.getMessage()).isEqualTo("explicit message");
		assertThat(ex.getStatus()).isNull();
	}

	@Test
	void carriesStatusAndMessage() {
		InteractionException ex = new InteractionException(HttpStatus.BAD_REQUEST, "Bad input");
		assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(ex.getMessage()).isEqualTo("Bad input");
	}

}
