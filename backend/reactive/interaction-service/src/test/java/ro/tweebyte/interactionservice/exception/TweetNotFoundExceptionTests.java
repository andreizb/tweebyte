/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TweetNotFoundExceptionTests {

	@Test
	void testTweetNotFoundExceptionWithMessage() {
		String message = "Tweet not found";
		TweetNotFoundException exception = new TweetNotFoundException(message);

		assertThat(exception.getMessage()).isNotNull();
		assertThat(exception.getMessage()).isEqualTo(message);
	}

}
