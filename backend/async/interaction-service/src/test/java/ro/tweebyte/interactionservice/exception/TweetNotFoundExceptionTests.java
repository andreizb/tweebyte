/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.assertj.core.api.Assertions.assertThat;

/** Mirrors reactive/.../exception/TweetNotFoundExceptionTest. */
class TweetNotFoundExceptionTests {

	@Test
	void constructorWithMessage() {
		TweetNotFoundException ex = new TweetNotFoundException("missing tweet");
		assertThat(ex.getMessage()).isEqualTo("missing tweet");
	}

	@Test
	void isRuntimeException() {
		assertThat(new TweetNotFoundException("x")).isInstanceOf(RuntimeException.class);
	}

	@Test
	void canBeThrownAndCaught() {
		try {
			throw new TweetNotFoundException("nope");
		}
		catch (TweetNotFoundException caught) {
			assertThat(caught.getMessage()).isEqualTo("nope");
		}
	}

	@Test
	void hasNotFoundResponseStatus() {
		ResponseStatus rs = TweetNotFoundException.class.getAnnotation(ResponseStatus.class);
		assertThat(rs).isNotNull();
		assertThat(rs.value()).hasToString("404 NOT_FOUND");
	}

}
