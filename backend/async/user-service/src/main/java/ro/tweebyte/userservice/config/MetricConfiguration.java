/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.config;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class MetricConfiguration {

	@Bean
	public MeterBinder mvcIoExecutorQueueMetrics(@Qualifier("streamExecutor") ThreadPoolTaskExecutor streamExecutor) {

		return registry -> {
			ThreadPoolExecutor tpe = streamExecutor.getThreadPoolExecutor();
			BlockingQueue<Runnable> q = tpe.getQueue();

			Gauge.builder("mvc_io_queue_size", q, BlockingQueue::size)
				.description("Tasks waiting in MVC ioExecutor queue")
				.register(registry);

			Gauge.builder("mvc_io_queue_utilization", q, queue -> {
				int size = queue.size();
				int cap = size + queue.remainingCapacity();
				return (cap != 0) ? ((double) size) / cap : 0.0;
			}).description("Queue fill ratio for MVC ioExecutor (0..1)").register(registry);

			Gauge
				.builder("mvc_io_thread_utilization", tpe,
						ex -> (ex.getMaximumPoolSize() != 0)
								? ((double) ex.getActiveCount()) / ex.getMaximumPoolSize() : 0.0)
				.description("Active threads / max threads for MVC ioExecutor")
				.register(registry);
		};
	}

}
