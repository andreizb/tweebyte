/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FollowRetrievingExceptionTests {

	@Test
	void testMessageAndCauseConstructor() {
		Throwable cause = new RuntimeException("downstream");
		FollowRetrievingException ex = new FollowRetrievingException("failed to retrieve followers", cause);
		assertThat(ex).isNotNull();
		assertThat(ex.getMessage()).isEqualTo("failed to retrieve followers");
		assertThat(ex.getCause()).isEqualTo(cause);
	}

	@Test
	void testIsRuntimeException() {
		FollowRetrievingException ex = new FollowRetrievingException("msg", new RuntimeException());
		assertThat(ex).isInstanceOf(RuntimeException.class);
	}

	@Test
	void testCanBeThrown() {
		Throwable cause = new RuntimeException("downstream");
		try {
			throw new FollowRetrievingException("msg", cause);
		}
		catch (FollowRetrievingException caught) {
			assertThat(caught).isNotNull();
			assertThat(caught.getCause()).isEqualTo(cause);
		}
	}

}
