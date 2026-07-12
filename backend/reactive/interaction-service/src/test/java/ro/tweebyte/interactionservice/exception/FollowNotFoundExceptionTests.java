/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.assertj.core.api.Assertions.assertThat;

class FollowNotFoundExceptionTests {

	@Test
	void testConstructorWithMessage() {
		FollowNotFoundException ex = new FollowNotFoundException("not found");
		assertThat(ex.getMessage()).isEqualTo("not found");
		assertThat(ex.getCause()).isNull();
	}

	@Test
	void testIsRuntimeException() {
		FollowNotFoundException ex = new FollowNotFoundException("x");
		assertThat(ex).isInstanceOf(RuntimeException.class);
	}

	@Test
	void testCanBeThrown() {
		try {
			throw new FollowNotFoundException("missing follow");
		}
		catch (FollowNotFoundException caught) {
			assertThat(caught.getMessage()).isEqualTo("missing follow");
		}
	}

	@Test
	void testNullMessage() {
		FollowNotFoundException ex = new FollowNotFoundException(null);
		assertThat(ex.getMessage()).isNull();
	}

	@Test
	void testHasResponseStatusAnnotation() {
		ResponseStatus rs = FollowNotFoundException.class.getAnnotation(ResponseStatus.class);
		assertThat(rs).isNotNull();
		assertThat(rs.value()).hasToString("404 NOT_FOUND");
	}

}
