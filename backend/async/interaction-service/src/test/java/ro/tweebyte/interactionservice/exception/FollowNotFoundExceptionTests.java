/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.assertj.core.api.Assertions.assertThat;

/** Mirrors reactive/.../exception/FollowNotFoundExceptionTest. */
class FollowNotFoundExceptionTests {

	@Test
	void constructorWithMessage() {
		FollowNotFoundException ex = new FollowNotFoundException("not found");
		assertThat(ex.getMessage()).isEqualTo("not found");
		assertThat(ex.getCause()).isNull();
	}

	@Test
	void isRuntimeException() {
		assertThat(new FollowNotFoundException("x")).isInstanceOf(RuntimeException.class);
	}

	@Test
	void canBeThrownAndCaught() {
		try {
			throw new FollowNotFoundException("missing follow");
		}
		catch (FollowNotFoundException caught) {
			assertThat(caught.getMessage()).isEqualTo("missing follow");
		}
	}

	@Test
	void nullMessage() {
		assertThat(new FollowNotFoundException(null).getMessage()).isNull();
	}

	@Test
	void hasResponseStatusAnnotation() {
		ResponseStatus rs = FollowNotFoundException.class.getAnnotation(ResponseStatus.class);
		assertThat(rs).isNotNull();
		assertThat(rs.value()).hasToString("404 NOT_FOUND");
	}

}
