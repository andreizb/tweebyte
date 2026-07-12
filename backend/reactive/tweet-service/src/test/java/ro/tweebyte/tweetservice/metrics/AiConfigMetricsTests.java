/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiConfigMetricsTests {

	@Test
	void registersTokensGaugeAndMockBackendInfoGauge() {
		MeterRegistry registry = new SimpleMeterRegistry();
		// Hold a strong reference: the info gauge's state object is the component
		// instance, which the registry references weakly.
		AiConfigMetrics metrics = new AiConfigMetrics(registry, 200, "mock");

		assertThat(registry.get("tweebyte.ai.mock.tokens.per.response").gauge().value()).isEqualTo(200.0);
		assertThat(registry.get("tweebyte.ai.backend.info").tag("backend", "mock").gauge().value()).isEqualTo(1.0);
		assertThat(metrics).isNotNull();
	}

	@Test
	void backendInfoGaugeTagsLiveBackend() {
		MeterRegistry registry = new SimpleMeterRegistry();
		AiConfigMetrics metrics = new AiConfigMetrics(registry, 150, "live");

		assertThat(registry.get("tweebyte.ai.mock.tokens.per.response").gauge().value()).isEqualTo(150.0);
		assertThat(registry.get("tweebyte.ai.backend.info").tag("backend", "live").gauge().value()).isEqualTo(1.0);
		assertThat(metrics).isNotNull();
	}

}
