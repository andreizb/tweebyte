/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.metrics;

import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.SignalType;

/**
 * In-flight subscription gauge + per-terminal-signal counters for AI streaming.
 *
 * <p>
 * Decrements live in {@link #onTerminate(SignalType)} so an erroring upstream (which
 * fires neither {@code doOnComplete} nor {@code doOnCancel}) can't leak the gauge under
 * sustained load — every subscription pairs with exactly one terminal signal via
 * {@code doFinally}.
 *
 * @author Andrei Zbarcea
 */
@Component
@RequiredArgsConstructor
public class PoolOccupancyMetrics {

	private final MeterRegistry registry;

	private final AtomicLong inFlightStreams = new AtomicLong();

	private Counter cancelledStreams;

	private Counter completedStreams;

	private Counter erroredStreams;

	@PostConstruct
	public void register() {
		Gauge.builder("tweebyte.reactive.inflight.streams", this.inFlightStreams, AtomicLong::get)
			.description("Number of in-flight AI streaming subscriptions on the reactive stack")
			.register(this.registry);
		this.cancelledStreams = Counter.builder("tweebyte.reactive.streams.cancelled")
			.description("AI streams terminated by downstream cancellation (client disconnect)")
			.register(this.registry);
		this.completedStreams = Counter.builder("tweebyte.reactive.streams.completed")
			.description("AI streams that completed successfully")
			.register(this.registry);
		this.erroredStreams = Counter.builder("tweebyte.reactive.streams.errored")
			.description("AI streams terminated by an upstream error signal")
			.register(this.registry);
	}

	public void onSubscribe() {
		this.inFlightStreams.incrementAndGet();
	}

	public void onTerminate(SignalType signalType) {
		this.inFlightStreams.decrementAndGet();
		switch (signalType) {
			case ON_COMPLETE -> incrementIfPresent(this.completedStreams);
			case CANCEL -> incrementIfPresent(this.cancelledStreams);
			case ON_ERROR -> incrementIfPresent(this.erroredStreams);
			default -> {
				/* no-op for non-terminal signals */ }
		}
	}

	// Counters are wired in @PostConstruct register(); a terminal signal that arrives on a
	// not-yet-registered instance (covered by onTerminateBeforeRegisterDoesNotNpe) is a no-op.
	private static void incrementIfPresent(Counter counter) {
		if (counter != null) {
			counter.increment();
		}
	}

}
