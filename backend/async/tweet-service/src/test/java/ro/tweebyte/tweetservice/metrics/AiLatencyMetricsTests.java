/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.metrics;

import java.time.Duration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiLatencyMetricsTests {

	private MeterRegistry registry;

	private AiLatencyMetrics metrics;

	@BeforeEach
	void setUp() {
		this.registry = new SimpleMeterRegistry();
		this.metrics = new AiLatencyMetrics(this.registry);
	}

	@Test
	void registersExpectedTimers() {
		assertThat(this.registry.find("tweebyte.ai.ttft").timer()).isNotNull();
		assertThat(this.registry.find("tweebyte.ai.itl").timer()).isNotNull();
		assertThat(this.registry.find("tweebyte.ai.tool").timer()).isNotNull();
		assertThat(this.registry.find("tweebyte.ai.serialize").timer()).isNotNull();
		// The three pre-registered outcome variants must all be present so the
		// first prometheus scrape exposes every series.
		assertThat(this.registry.find("tweebyte.ai.e2e").tag("outcome", "success").timer()).isNotNull();
		assertThat(this.registry.find("tweebyte.ai.e2e").tag("outcome", "error").timer()).isNotNull();
		assertThat(this.registry.find("tweebyte.ai.e2e").tag("outcome", "cancel").timer()).isNotNull();
	}

	@Test
	void recordTtftIncrementsTimer() {
		this.metrics.recordTtft(Duration.ofMillis(10));
		assertThat(this.registry.find("tweebyte.ai.ttft").timer().count()).isEqualTo(1);
	}

	@Test
	void recordItlIncrementsTimer() {
		this.metrics.recordItl(Duration.ofMillis(5));
		assertThat(this.registry.find("tweebyte.ai.itl").timer().count()).isEqualTo(1);
	}

	@Test
	void recordToolCallIncrementsTimer() {
		this.metrics.recordToolCall(Duration.ofMillis(20));
		assertThat(this.registry.find("tweebyte.ai.tool").timer().count()).isEqualTo(1);
	}

	@Test
	void recordSerializeIncrementsTimer() {
		this.metrics.recordSerialize(Duration.ofMillis(1));
		assertThat(this.registry.find("tweebyte.ai.serialize").timer().count()).isEqualTo(1);
	}

	@Test
	void recordEndToEndDispatchesByOutcome() {
		this.metrics.recordEndToEnd(Duration.ofMillis(100), AiLatencyMetrics.OUTCOME_SUCCESS);
		this.metrics.recordEndToEnd(Duration.ofMillis(200), AiLatencyMetrics.OUTCOME_ERROR);
		this.metrics.recordEndToEnd(Duration.ofMillis(300), AiLatencyMetrics.OUTCOME_CANCEL);

		assertThat(this.registry.find("tweebyte.ai.e2e").tag("outcome", "success").timer().count()).isEqualTo(1);
		assertThat(this.registry.find("tweebyte.ai.e2e").tag("outcome", "error").timer().count()).isEqualTo(1);
		assertThat(this.registry.find("tweebyte.ai.e2e").tag("outcome", "cancel").timer().count()).isEqualTo(1);
	}

	@Test
	void recordEndToEndUnknownOutcomeFallsBackToSuccess() {
		// The switch's `default` branch routes any non-error/non-cancel value to the
		// success timer — exercise the default arm explicitly so JaCoCo records it.
		this.metrics.recordEndToEnd(Duration.ofMillis(50), "weird-outcome");
		assertThat(this.registry.find("tweebyte.ai.e2e").tag("outcome", "success").timer().count()).isEqualTo(1);
	}

}
