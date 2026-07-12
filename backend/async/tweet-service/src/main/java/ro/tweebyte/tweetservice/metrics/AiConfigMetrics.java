/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Always-on info gauges exposing the effective AI configuration so an external benchmark
 * runner can assert it from {@code /actuator/prometheus} before load — exactly as it
 * asserts the stream pool via {@code tweebyte_pool_max_size}. Registered unconditionally
 * (mock or live backend) so the series are scrapeable in either mode.
 *
 * @author Andrei Zbarcea
 */
@Component
public class AiConfigMetrics {

	public AiConfigMetrics(MeterRegistry registry,
			@Value("${app.ai.mock.tokens-per-response:150}") int tokensPerResponse,
			@Value("${app.ai.backend:mock}") String aiBackend) {
		Gauge.builder("tweebyte.ai.mock.tokens.per.response", () -> tokensPerResponse)
			.description("Effective configured mock output token count (app.ai.mock.tokens-per-response)")
			.register(registry);
		Gauge.builder("tweebyte.ai.backend.info", this, ignored -> 1.0)
			.tag("backend", aiBackend)
			.description("Effective AI backend (app.ai.backend): mock or live")
			.register(registry);
	}

}
