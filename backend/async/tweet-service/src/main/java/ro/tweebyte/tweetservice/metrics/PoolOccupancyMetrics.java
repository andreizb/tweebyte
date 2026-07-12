/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.metrics;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Micrometer gauges for the custom executor. Rejection counter lives in
 * {@link ro.tweebyte.tweetservice.config.ThreadConfiguration.CountingRejectionHandler} so
 * it actually fires when the stock reject policy runs.
 *
 * @author Andrei Zbarcea
 */
@Component
@RequiredArgsConstructor
public class PoolOccupancyMetrics {

	@Qualifier("streamExecutor")
	private final ExecutorService executorService;

	private final MeterRegistry registry;

	@Value("${app.concurrency.stream.reject-policy:caller-runs}")
	private String streamRejectPolicy;

	@PostConstruct
	public void register() {
		if (this.executorService instanceof ThreadPoolExecutor pool) {
			Gauge.builder("tweebyte.pool.queue.depth", pool, p -> p.getQueue().size())
				.description("Current depth of the custom executor task queue")
				.register(this.registry);
			Gauge.builder("tweebyte.pool.active", pool, ThreadPoolExecutor::getActiveCount)
				.description("Active thread count of the custom executor")
				.register(this.registry);
			Gauge.builder("tweebyte.pool.size", pool, p -> p.getPoolSize())
				.description("Current pool size of the custom executor")
				.register(this.registry);
			Gauge.builder("tweebyte.pool.core.size", pool, ThreadPoolExecutor::getCorePoolSize)
				.description("Configured core size of the custom executor")
				.register(this.registry);
			Gauge.builder("tweebyte.pool.max.size", pool, ThreadPoolExecutor::getMaximumPoolSize)
				.description("Configured maximum size of the custom executor")
				.register(this.registry);
			Gauge.builder("tweebyte.pool.queue.capacity", pool,
					p -> p.getQueue().size() + p.getQueue().remainingCapacity())
				.description("Configured capacity of the custom executor task queue")
				.register(this.registry);
			Gauge.builder("tweebyte.pool.reject.policy.info", this, ignored -> 1.0)
				.tag("policy", this.streamRejectPolicy)
				.description("Static info metric exposing the configured custom executor reject policy")
				.register(this.registry);
			Gauge.builder("tweebyte.pool.tasks.completed", pool, ThreadPoolExecutor::getCompletedTaskCount)
				.description("Completed task count of the custom executor")
				.register(this.registry);
		}
	}

}
