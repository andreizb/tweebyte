/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.metrics;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PoolOccupancyMetricsTests {

	private ExecutorService executor;

	@AfterEach
	void tearDown() {
		if (this.executor != null) {
			this.executor.shutdownNow();
		}
	}

	@Test
	void registersAllGaugesWhenExecutorIsThreadPoolExecutor() {
		this.executor = Executors.newFixedThreadPool(2);
		MeterRegistry registry = new SimpleMeterRegistry();
		PoolOccupancyMetrics metrics = new PoolOccupancyMetrics(this.executor, registry);
		ReflectionTestUtils.setField(metrics, "streamRejectPolicy", "caller-runs");

		metrics.register();

		assertThat(registry.find("tweebyte.pool.queue.depth").gauge()).isNotNull();
		assertThat(registry.find("tweebyte.pool.active").gauge()).isNotNull();
		assertThat(registry.find("tweebyte.pool.size").gauge()).isNotNull();
		assertThat(registry.find("tweebyte.pool.tasks.completed").gauge()).isNotNull();

		// Active threads should be 0 right after construction (no submitted tasks).
		assertThat(registry.find("tweebyte.pool.active").gauge().value()).isCloseTo(0, within(0.0));
	}

	@Test
	void registersNothingWhenExecutorIsNotThreadPoolExecutor() {
		// The instanceof guard means a non-ThreadPoolExecutor (e.g. a forwarding
		// wrapper or work-stealing pool) leaves the registry empty for these gauges.
		ExecutorService notTpe = new ExecutorService() {
			@Override
			public void shutdown() {
			}

			@Override
			public java.util.List<Runnable> shutdownNow() {
				return java.util.List.of();
			}

			@Override
			public boolean isShutdown() {
				return true;
			}

			@Override
			public boolean isTerminated() {
				return true;
			}

			@Override
			public boolean awaitTermination(long t, java.util.concurrent.TimeUnit u) {
				return true;
			}

			@Override
			public <T> java.util.concurrent.Future<T> submit(java.util.concurrent.Callable<T> task) {
				return null;
			}

			@Override
			public <T> java.util.concurrent.Future<T> submit(Runnable task, T result) {
				return null;
			}

			@Override
			public java.util.concurrent.Future<?> submit(Runnable task) {
				return null;
			}

			@Override
			public <T> java.util.List<java.util.concurrent.Future<T>> invokeAll(
					java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks) {
				return java.util.List.of();
			}

			@Override
			public <T> java.util.List<java.util.concurrent.Future<T>> invokeAll(
					java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks, long t,
					java.util.concurrent.TimeUnit u) {
				return java.util.List.of();
			}

			@Override
			public <T> T invokeAny(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks) {
				return null;
			}

			@Override
			public <T> T invokeAny(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks, long t,
					java.util.concurrent.TimeUnit u) {
				return null;
			}

			@Override
			public void execute(Runnable command) {
			}
		};

		MeterRegistry registry = new SimpleMeterRegistry();
		new PoolOccupancyMetrics(notTpe, registry).register();

		// No pool.* gauges should be registered for the non-TPE branch.
		assertThat(registry.find("tweebyte.pool.queue.depth").gauge()).isNull();
	}

	@Test
	void queueDepthGaugeReflectsThreadPoolExecutorState() {
		ThreadPoolExecutor tpe = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
		this.executor = tpe;
		MeterRegistry registry = new SimpleMeterRegistry();
		PoolOccupancyMetrics tpeMetrics = new PoolOccupancyMetrics(tpe, registry);
		ReflectionTestUtils.setField(tpeMetrics, "streamRejectPolicy", "caller-runs");
		tpeMetrics.register();
		// No tasks queued initially.
		assertThat(registry.find("tweebyte.pool.queue.depth").gauge().value()).isCloseTo(0.0, within(0.0));
		// PoolSize gauge present even before tasks run.
		assertThat(registry.find("tweebyte.pool.size").gauge()).isNotNull();
	}

}
