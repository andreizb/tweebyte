/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserAlreadyExistsExceptionTests {

	@Test
	void testUserAlreadyExistsExceptionMessage() {
		String errorMessage = "User already exists";
		UserAlreadyExistsException exception = new UserAlreadyExistsException(errorMessage);

		assertThat(exception.getMessage()).isEqualTo(errorMessage);
	}

}
