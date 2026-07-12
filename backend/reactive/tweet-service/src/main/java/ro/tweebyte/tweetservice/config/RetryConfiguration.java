/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.config;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.util.retry.Retry;

import ro.tweebyte.tweetservice.exception.TweetNotFoundException;

/**
 * Reactor {@code Retry} bean for tweet-service — gated by
 * {@code app.resilience.retry.enabled}.
 *
 * <p>
 * The TweetService.processTweetTokens path injects this as an
 * {@code ObjectProvider<Retry>} and conditionally applies it via {@code .retryWhen(...)}.
 * When the flag is true (default), the bean is registered and the retry operator wraps
 * the tokenization chain. When the flag is false (benchmark profile), the bean is not
 * registered and the chain runs without {@code retryWhen} added to the operator pipeline.
 *
 * <p>
 * Spec mirrors the values applied at {@code TweetService.processTweetTokens}:
 * MAX_RETRIES=10 fixed delay, filter on {@code TweetNotFoundException}. The filter is
 * baked into the spec here because the abstract {@code Retry} type doesn't expose
 * {@code .filter()} — that lives on the concrete {@code RetrySpec} subtype, which is what
 * {@code Retry.fixedDelay(...)} returns at build time.
 *
 * @author Andrei Zbarcea
 */
@Configuration
@ConditionalOnProperty(prefix = "app.resilience.retry", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RetryConfiguration {

	private static final int MAX_RETRIES = 10;

	@Bean(name = "tweetTokensRetry")
	public Retry tweetTokensRetry() {
		// Reactor Retry.filter retries ONLY when the predicate is true. Tokenization
		// must treat TweetNotFoundException as terminal (no retry) and retry every
		// other failure — mirroring async's processTweetTokens (break on
		// TweetNotFoundException, retry on any other Exception).
		return Retry.fixedDelay(MAX_RETRIES, Duration.ofSeconds(1))
			.filter(throwable -> !(throwable instanceof TweetNotFoundException));
	}

}
