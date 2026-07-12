/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.assertj.core.api.Assertions.assertThat;

/** Mirrors reactive/.../exception/UserNotFoundExceptionTest. */
class UserNotFoundExceptionTests {

	@Test
	void constructorWithMessage() {
		UserNotFoundException ex = new UserNotFoundException("missing user");
		assertThat(ex.getMessage()).isEqualTo("missing user");
	}

	@Test
	void isRuntimeException() {
		assertThat(new UserNotFoundException("x")).isInstanceOf(RuntimeException.class);
	}

	@Test
	void canBeThrownAndCaught() {
		try {
			throw new UserNotFoundException("absent");
		}
		catch (UserNotFoundException caught) {
			assertThat(caught.getMessage()).isEqualTo("absent");
		}
	}

	@Test
	void hasNotFoundResponseStatus() {
		ResponseStatus rs = UserNotFoundException.class.getAnnotation(ResponseStatus.class);
		assertThat(rs).isNotNull();
		assertThat(rs.value()).hasToString("404 NOT_FOUND");
	}

}
