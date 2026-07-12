/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Disables the servlet async timeout on the measured path. Controllers return
 * {@code CompletableFuture}, so the Tomcat worker is released at async dispatch and the
 * real concurrency lives on the downstream executors/pools. With Tomcat's default 30s
 * async timeout, a request that legitimately queues past 30s under load (the benchmark
 * overlay sets connection-acquire waits to 3600s — wait, don't fail) is completed as an
 * error by the container before it can finish, and the closed-loop driver immediately
 * resends — orphaning the in-flight work and spiralling into a full wedge. Setting the
 * default timeout to 0 (never) lets saturation queue instead of error, keeping the
 * timeout ladder consistent with user-service's {@code MvcAsyncConfig} across the three
 * async services. No {@code streamExecutor} here: this service has no
 * {@code StreamingResponseBody} surface, so there is nothing to set a task executor for.
 *
 * @author Andrei Zbarcea
 */
@Configuration
public class MvcAsyncConfig implements WebMvcConfigurer {

	@Override
	public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
		configurer.setDefaultTimeout(0);
	}

}
