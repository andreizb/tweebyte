/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FollowRetrievingExceptionTests {

	@Test
	void testMessageAndCauseConstructor() {
		Throwable cause = new RuntimeException("downstream");
		FollowRetrievingException exception = new FollowRetrievingException("failed to retrieve followed ids", cause);
		assertThat(exception).isNotNull();
		assertThat(exception.getMessage()).isEqualTo("failed to retrieve followed ids");
		assertThat(exception.getCause()).isEqualTo(cause);
	}

}
