/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class TweetExceptionTests {

	@Test
	void testTweetExceptionWithCause() {
		Exception cause = new Exception("Test cause");
		TweetException exception = new TweetException(cause);
		assertThat(exception).isNotNull();
		assertThat(exception.getCause().getMessage()).isEqualTo("Test cause");
		assertThat(exception.getCause()).isEqualTo(cause);
		assertThat(exception.getStatus()).isNull();
	}

	@Test
	void testTweetExceptionWithMessage() {
		TweetException exception = new TweetException("Test message");
		assertThat(exception).isNotNull();
		assertThat(exception.getMessage()).isEqualTo("Test message");
		assertThat(exception.getCause()).isNull();
		assertThat(exception.getStatus()).isNull();
	}

	@Test
	void testTweetExceptionWithStatusAndMessage() {
		TweetException exception = new TweetException(HttpStatus.BAD_REQUEST, "Bad input");
		assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(exception.getMessage()).isEqualTo("Bad input");
	}

}
