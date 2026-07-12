/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.repository.UserRepository;

/**
 * Async-stack equivalent of the reactor {@code .retryWhen} on reactive
 * {@code UserService.updateUser}. Both stacks retry transient DB failures on the save
 * step of user-update; the runtime mechanism differs per paradigm (Spring Retry
 * interceptor here, reactor operator on reactive), but the observable behaviour matches.
 *
 * <p>
 * Spring Retry's {@code @Retryable} requires the proxy to intercept the call.
 * Self-invocation (e.g. {@code this.save(...)} from inside the same class) bypasses the
 * proxy, so this delegate exists as a separate {@code @Service} bean that
 * {@link UserService} injects and calls through.
 *
 * <p>
 * Enabled-flag behaviour:
 * <ul>
 * <li>{@code app.resilience.retry.enabled=true} (default):
 * {@link RetryConfiguration @EnableRetry}-imported interceptor is registered, this method
 * is proxied, retries fire on {@link TransientDataAccessException} /
 * {@link RecoverableDataAccessException}.</li>
 * <li>{@code app.resilience.retry.enabled=false} (benchmark): the
 * {@code RetryConfiguration} bean is not instantiated, no interceptor is registered, this
 * method runs as a plain bean call → save is invoked once, no retry behaviour, zero proxy
 * overhead.</li>
 * </ul>
 *
 * @author Andrei Zbarcea
 */
@Service
@RequiredArgsConstructor
public class UserUpdateRetryDelegate {

	private final UserRepository userRepository;

	@Retryable(retryFor = { TransientDataAccessException.class, RecoverableDataAccessException.class }, maxAttempts = 4,
			backoff = @Backoff(delay = 1000, multiplier = 2.0))
	public UserEntity save(UserEntity entity) {
		return this.userRepository.save(entity);
	}

}
