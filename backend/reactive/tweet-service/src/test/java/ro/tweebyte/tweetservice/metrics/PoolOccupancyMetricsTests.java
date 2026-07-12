/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.SignalType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PoolOccupancyMetricsTests {

	private MeterRegistry registry;

	private PoolOccupancyMetrics metrics;

	@BeforeEach
	void setUp() {
		this.registry = new SimpleMeterRegistry();
		this.metrics = new PoolOccupancyMetrics(this.registry);
		this.metrics.register();
	}

	private double inFlight() {
		Gauge g = this.registry.find("tweebyte.reactive.inflight.streams").gauge();
		assertThat(g).isNotNull();
		return g.value();
	}

	private double counter(String name) {
		Counter c = this.registry.find(name).counter();
		assertThat(c).isNotNull();
		return c.count();
	}

	@Test
	void registerCreatesInFlightGaugeAndCounters() {
		assertThat(inFlight()).isEqualTo(0.0);
		assertThat(counter("tweebyte.reactive.streams.completed")).isEqualTo(0.0);
		assertThat(counter("tweebyte.reactive.streams.cancelled")).isEqualTo(0.0);
		assertThat(counter("tweebyte.reactive.streams.errored")).isEqualTo(0.0);
	}

	@Test
	void onSubscribeIncrementsInFlight() {
		this.metrics.onSubscribe();
		this.metrics.onSubscribe();
		assertThat(inFlight()).isEqualTo(2.0);
	}

	@Test
	void onTerminateCompleteIncrementsCompletedCounter() {
		this.metrics.onSubscribe();
		this.metrics.onTerminate(SignalType.ON_COMPLETE);
		assertThat(inFlight()).isEqualTo(0.0);
		assertThat(counter("tweebyte.reactive.streams.completed")).isEqualTo(1.0);
		assertThat(counter("tweebyte.reactive.streams.cancelled")).isEqualTo(0.0);
		assertThat(counter("tweebyte.reactive.streams.errored")).isEqualTo(0.0);
	}

	@Test
	void onTerminateCancelIncrementsCancelledCounter() {
		this.metrics.onSubscribe();
		this.metrics.onTerminate(SignalType.CANCEL);
		assertThat(inFlight()).isEqualTo(0.0);
		assertThat(counter("tweebyte.reactive.streams.cancelled")).isEqualTo(1.0);
		assertThat(counter("tweebyte.reactive.streams.completed")).isEqualTo(0.0);
	}

	@Test
	void onTerminateErrorIncrementsErroredCounter() {
		this.metrics.onSubscribe();
		this.metrics.onTerminate(SignalType.ON_ERROR);
		assertThat(inFlight()).isEqualTo(0.0);
		assertThat(counter("tweebyte.reactive.streams.errored")).isEqualTo(1.0);
	}

	@Test
	void onTerminateNonTerminalSignalDecrementsButTouchesNoCounter() {
		this.metrics.onSubscribe();
		this.metrics.onTerminate(SignalType.ON_NEXT);
		assertThat(inFlight()).isEqualTo(0.0);
		assertThat(counter("tweebyte.reactive.streams.completed")).isEqualTo(0.0);
		assertThat(counter("tweebyte.reactive.streams.cancelled")).isEqualTo(0.0);
		assertThat(counter("tweebyte.reactive.streams.errored")).isEqualTo(0.0);
	}

	@Test
	void onTerminateBeforeRegisterDoesNotNpe() {
		// Construct a fresh metrics instance and call onTerminate without
		// calling register() first — counters are null but the null guards
		// in the switch arms must keep this safe.
		PoolOccupancyMetrics fresh = new PoolOccupancyMetrics(new SimpleMeterRegistry());
		assertThatCode(() -> {
			fresh.onSubscribe();
			fresh.onTerminate(SignalType.ON_COMPLETE);
			fresh.onTerminate(SignalType.CANCEL);
			fresh.onTerminate(SignalType.ON_ERROR);
		}).doesNotThrowAnyException();
	}

	@Test
	void multipleSubscribesAndMixedTerminations() {
		this.metrics.onSubscribe();
		this.metrics.onSubscribe();
		this.metrics.onSubscribe();
		this.metrics.onTerminate(SignalType.ON_COMPLETE);
		this.metrics.onTerminate(SignalType.CANCEL);
		this.metrics.onTerminate(SignalType.ON_ERROR);
		assertThat(inFlight()).isEqualTo(0.0);
		assertThat(counter("tweebyte.reactive.streams.completed")).isEqualTo(1.0);
		assertThat(counter("tweebyte.reactive.streams.cancelled")).isEqualTo(1.0);
		assertThat(counter("tweebyte.reactive.streams.errored")).isEqualTo(1.0);
	}

}
