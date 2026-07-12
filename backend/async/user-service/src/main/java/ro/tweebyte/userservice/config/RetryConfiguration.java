/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Spring Retry registration — gated by {@code app.resilience.retry.enabled}.
 *
 * <p>
 * When the flag is true (default), {@code @EnableRetry} imports
 * {@code RetryOperationsInterceptor}, which proxies any bean carrying {@code @Retryable}.
 * The single consumer today is
 * {@link ro.tweebyte.userservice.service.UserUpdateRetryDelegate#save(ro.tweebyte.userservice.entity.UserEntity)}.
 *
 * <p>
 * When the flag is false (benchmark profile sets this in
 * {@code application-benchmark.properties}), this config class is not instantiated →
 * {@code @EnableRetry} never runs → no interceptor is registered → {@code @Retryable}
 * becomes inert metadata. Zero proxy, zero advice chain, zero overhead. The save call
 * inlines directly to the repository.
 *
 * <p>
 * Mirrors {@link ro.tweebyte.userservice.config.RetryConfiguration the reactive side}
 * which uses the same flag to conditionally register the reactor {@code Retry} bean
 * injected into reactive UserService.updateUser.
 *
 * @author Andrei Zbarcea
 */
@Configuration
@EnableRetry
@ConditionalOnProperty(prefix = "app.resilience.retry", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RetryConfiguration {

}
