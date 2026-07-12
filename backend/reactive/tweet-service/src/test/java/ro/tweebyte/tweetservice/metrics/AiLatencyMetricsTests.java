/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.metrics;

import java.time.Duration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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

	private long count(String name, String... tags) {
		Timer t = this.registry.find(name).tags(tags).timer();
		return (t != null) ? t.count() : 0;
	}

	@Test
	void recordTtftIncrementsTtftTimer() {
		this.metrics.recordTtft(Duration.ofMillis(10));
		this.metrics.recordTtft(Duration.ofMillis(20));
		assertThat(count("tweebyte.ai.ttft")).isEqualTo(2);
	}

	@Test
	void recordItlIncrementsItlTimer() {
		this.metrics.recordItl(Duration.ofMillis(5));
		assertThat(count("tweebyte.ai.itl")).isEqualTo(1);
	}

	@Test
	void recordToolCallIncrementsToolTimer() {
		this.metrics.recordToolCall(Duration.ofMillis(50));
		assertThat(count("tweebyte.ai.tool")).isEqualTo(1);
	}

	@Test
	void recordSerializeIncrementsSerializeTimer() {
		this.metrics.recordSerialize(Duration.ofNanos(123));
		assertThat(count("tweebyte.ai.serialize")).isEqualTo(1);
	}

	@Test
	void recordEndToEndSuccessRoutesToSuccessTimer() {
		this.metrics.recordEndToEnd(Duration.ofMillis(100), AiLatencyMetrics.OUTCOME_SUCCESS);
		assertThat(count("tweebyte.ai.e2e", "outcome", AiLatencyMetrics.OUTCOME_SUCCESS)).isEqualTo(1);
		assertThat(count("tweebyte.ai.e2e", "outcome", AiLatencyMetrics.OUTCOME_ERROR)).isZero();
		assertThat(count("tweebyte.ai.e2e", "outcome", AiLatencyMetrics.OUTCOME_CANCEL)).isZero();
	}

	@Test
	void recordEndToEndErrorRoutesToErrorTimer() {
		this.metrics.recordEndToEnd(Duration.ofMillis(100), AiLatencyMetrics.OUTCOME_ERROR);
		assertThat(count("tweebyte.ai.e2e", "outcome", AiLatencyMetrics.OUTCOME_ERROR)).isEqualTo(1);
		assertThat(count("tweebyte.ai.e2e", "outcome", AiLatencyMetrics.OUTCOME_SUCCESS)).isZero();
	}

	@Test
	void recordEndToEndCancelRoutesToCancelTimer() {
		this.metrics.recordEndToEnd(Duration.ofMillis(100), AiLatencyMetrics.OUTCOME_CANCEL);
		assertThat(count("tweebyte.ai.e2e", "outcome", AiLatencyMetrics.OUTCOME_CANCEL)).isEqualTo(1);
		assertThat(count("tweebyte.ai.e2e", "outcome", AiLatencyMetrics.OUTCOME_SUCCESS)).isZero();
	}

	@Test
	void recordEndToEndUnknownOutcomeRoutesToSuccessDefault() {
		this.metrics.recordEndToEnd(Duration.ofMillis(50), "weird-outcome");
		assertThat(count("tweebyte.ai.e2e", "outcome", AiLatencyMetrics.OUTCOME_SUCCESS)).isEqualTo(1);
	}

}
