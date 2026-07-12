/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserNotFoundExceptionTests {

	@Test
	void testUserNotFoundException() {
		UserNotFoundException exception = new UserNotFoundException("Test message");
		assertThat(exception).isNotNull();
		assertThat(exception.getMessage()).isEqualTo("Test message");
	}

}
