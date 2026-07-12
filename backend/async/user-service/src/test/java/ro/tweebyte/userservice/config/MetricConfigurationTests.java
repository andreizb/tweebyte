/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises MetricConfiguration — registers gauges using a real ThreadPoolTaskExecutor
 * and drives the utilization lambda branches (empty queue, non-zero cap, active threads).
 */
class MetricConfigurationTests {

	private final MetricConfiguration config = new MetricConfiguration();

	private ThreadPoolTaskExecutor buildStreamExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(10);
		executor.setBeanName("streamExecutor");
		executor.initialize();
		return executor;
	}

	@Test
	void mvcIoExecutorQueueMetrics_registersGauges() {
		ThreadPoolTaskExecutor executor = buildStreamExecutor();
		try {
			MeterBinder binder = this.config.mvcIoExecutorQueueMetrics(executor);
			MeterRegistry registry = new SimpleMeterRegistry();
			binder.bindTo(registry);

			assertThat(registry.find("mvc_io_queue_size").gauge()).isNotNull();
			assertThat(registry.find("mvc_io_queue_utilization").gauge()).isNotNull();
			assertThat(registry.find("mvc_io_thread_utilization").gauge()).isNotNull();
		}
		finally {
			executor.shutdown();
		}
	}

	@Test
	void mvcIoQueueUtilization_emptyQueue_returnsZero() {
		ThreadPoolTaskExecutor executor = buildStreamExecutor();
		try {
			MeterBinder binder = this.config.mvcIoExecutorQueueMetrics(executor);
			MeterRegistry registry = new SimpleMeterRegistry();
			binder.bindTo(registry);

			// With an idle executor and empty queue the utilization gauge returns 0.0.
			double util = registry.find("mvc_io_queue_utilization").gauge().value();
			assertThat(util).isEqualTo(0.0);
		}
		finally {
			executor.shutdown();
		}
	}

	@Test
	void mvcIoThreadUtilization_idleExecutor_returnsZero() {
		ThreadPoolTaskExecutor executor = buildStreamExecutor();
		try {
			MeterBinder binder = this.config.mvcIoExecutorQueueMetrics(executor);
			MeterRegistry registry = new SimpleMeterRegistry();
			binder.bindTo(registry);

			// With no active threads the ratio is 0 / maxPoolSize = 0.0.
			double util = registry.find("mvc_io_thread_utilization").gauge().value();
			assertThat(util).isEqualTo(0.0);
		}
		finally {
			executor.shutdown();
		}
	}

}
