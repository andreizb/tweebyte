/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserNotFoundExceptionTests {

	@Test
	void testConstructorWithMessage() {
		UserNotFoundException ex = new UserNotFoundException("not found");
		assertThat(ex.getMessage()).isEqualTo("not found");
		assertThat(ex.getCause()).isNull();
	}

	@Test
	void testIsRuntimeException() {
		UserNotFoundException ex = new UserNotFoundException("x");
		assertThat(ex).isInstanceOf(RuntimeException.class);
	}

	@Test
	void testCanBeThrown() {
		try {
			throw new UserNotFoundException("user 42 missing");
		}
		catch (UserNotFoundException caught) {
			assertThat(caught.getMessage()).isEqualTo("user 42 missing");
		}
	}

	@Test
	void testNullMessage() {
		UserNotFoundException ex = new UserNotFoundException(null);
		assertThat(ex.getMessage()).isNull();
	}

	@Test
	void testEmptyMessage() {
		UserNotFoundException ex = new UserNotFoundException("");
		assertThat(ex.getMessage()).isEmpty();
		assertThat(ex.getStackTrace()).isNotNull();
	}

}
