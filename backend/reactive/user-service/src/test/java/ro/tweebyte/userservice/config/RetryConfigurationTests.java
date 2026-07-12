/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.config;

import org.junit.jupiter.api.Test;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import ro.tweebyte.userservice.exception.UserNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises RetryConfiguration — verifies that {@code updateUserRetry} returns a
 * {@link RetryBackoffSpec} and that its filter predicate correctly allows retries
 * for generic exceptions and suppresses them for {@link UserNotFoundException}.
 */
class RetryConfigurationTests {

	private final RetryConfiguration config = new RetryConfiguration();

	@Test
	void updateUserRetry_returnsRetryBackoffSpec() {
		Retry retry = this.config.updateUserRetry();
		assertThat(retry).isNotNull().isInstanceOf(RetryBackoffSpec.class);
	}

	@Test
	void filter_allowsRetryForGenericException() {
		// Any non-UserNotFoundException should be retried (filter returns true).
		RetryBackoffSpec spec = (RetryBackoffSpec) this.config.updateUserRetry();
		boolean shouldRetry = spec.errorFilter.test(new RuntimeException("transient error"));
		assertThat(shouldRetry).isTrue();
	}

	@Test
	void filter_suppressesRetryForUserNotFoundException() {
		// UserNotFoundException must not trigger retry (filter returns false).
		RetryBackoffSpec spec = (RetryBackoffSpec) this.config.updateUserRetry();
		boolean shouldRetry = spec.errorFilter.test(new UserNotFoundException("not found"));
		assertThat(shouldRetry).isFalse();
	}

}
