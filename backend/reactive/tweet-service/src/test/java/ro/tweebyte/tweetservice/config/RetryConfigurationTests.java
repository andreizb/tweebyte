/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.config;

import org.junit.jupiter.api.Test;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import ro.tweebyte.tweetservice.exception.TweetNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RetryConfiguration}.
 * Verifies the retry bean is created and that its filter correctly
 * allows / denies retry on the exception types it governs.
 */
class RetryConfigurationTests {

	private final RetryConfiguration config = new RetryConfiguration();

	@Test
	void tweetTokensRetry_returnsNonNull() {
		Retry retry = this.config.tweetTokensRetry();
		assertThat(retry).isNotNull();
	}

	@Test
	void tweetTokensRetry_returnsRetryBackoffSpec() {
		Retry retry = this.config.tweetTokensRetry();
		// Retry.fixedDelay returns a RetryBackoffSpec
		assertThat(retry).isInstanceOf(RetryBackoffSpec.class);
	}

	@Test
	void tweetTokensRetry_filterRetries_onGenericException() {
		RetryBackoffSpec spec = (RetryBackoffSpec) this.config.tweetTokensRetry();
		// Generic exception → predicate returns true (= retry allowed)
		assertThat(spec.errorFilter.test(new RuntimeException("transient"))).isTrue();
	}

	@Test
	void tweetTokensRetry_filterDoesNotRetry_onTweetNotFoundException() {
		RetryBackoffSpec spec = (RetryBackoffSpec) this.config.tweetTokensRetry();
		// TweetNotFoundException → predicate returns false (= terminal, no retry)
		assertThat(spec.errorFilter.test(new TweetNotFoundException("not found"))).isFalse();
	}

	@Test
	void tweetTokensRetry_filterRetries_onIllegalStateException() {
		RetryBackoffSpec spec = (RetryBackoffSpec) this.config.tweetTokensRetry();
		assertThat(spec.errorFilter.test(new IllegalStateException("db error"))).isTrue();
	}

	@Test
	void tweetTokensRetry_maxAttempts_is10() {
		RetryBackoffSpec spec = (RetryBackoffSpec) this.config.tweetTokensRetry();
		assertThat(spec.maxAttempts).isEqualTo(10L);
	}

}
