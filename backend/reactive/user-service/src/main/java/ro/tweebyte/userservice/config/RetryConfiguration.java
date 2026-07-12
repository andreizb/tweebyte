/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.config;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.util.retry.Retry;

import ro.tweebyte.userservice.exception.UserNotFoundException;

/**
 * Reactor {@code Retry} bean — gated by {@code app.resilience.retry.enabled}.
 *
 * <p>
 * The reactive UserService.updateUser injects this as an {@code ObjectProvider<Retry>}
 * and conditionally applies it via {@code .retryWhen(...)}. When the flag is true
 * (default), the bean is registered and the retry operator wraps the save chain. When the
 * flag is false (benchmark profile), the bean is not registered and the save chain runs
 * without {@code retryWhen} added to the operator pipeline.
 *
 * <p>
 * Spec mirrors the values applied at {@code UserService.updateUser}: 3 attempts, 1s
 * exponential backoff, skip retry on {@code UserNotFoundException}. The filter is baked
 * into the spec here because the abstract {@code Retry} type doesn't expose
 * {@code .filter()} — that lives on the concrete {@code RetryBackoffSpec} subtype, which
 * is what {@code Retry.backoff(...)} returns at build time.
 *
 * <p>
 * Async-stack symmetric mechanism lives in
 * {@code async/user-service/.../config/RetryConfiguration.java} — same flag, different
 * machinery (Spring Retry {@code @EnableRetry} +
 * {@code UserUpdateRetryDelegate.save(@Retryable)}).
 *
 * @author Andrei Zbarcea
 */
@Configuration
@ConditionalOnProperty(prefix = "app.resilience.retry", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RetryConfiguration {

	@Bean(name = "updateUserRetry")
	public Retry updateUserRetry() {
		return Retry.backoff(3, Duration.ofSeconds(1))
			.filter(throwable -> !(throwable instanceof UserNotFoundException));
	}

}
