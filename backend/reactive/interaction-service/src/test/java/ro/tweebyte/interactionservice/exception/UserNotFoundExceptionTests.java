/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserNotFoundExceptionTests {

	@Test
	void testUserNotFoundExceptionWithMessage() {
		String message = "User not found";
		UserNotFoundException exception = new UserNotFoundException(message);

		assertThat(exception.getMessage()).isNotNull();
		assertThat(exception.getMessage()).isEqualTo(message);
	}

}
