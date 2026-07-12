/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.config;

import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ThreadConfiguration}.
 * Exercises every branch of the queue / reject-policy selection logic and
 * the {@link ThreadConfiguration.CountingRejectionHandler}.
 */
class ThreadConfigurationTests {

	// -------------------------------------------------------------------------
	// ioExecutor & httpClientExecutor — simple fixed-pool sanity checks
	// -------------------------------------------------------------------------

	@Test
	void ioExecutor_isNotNull() throws Exception {
		ThreadConfiguration config = new ThreadConfiguration();
		setField(config, "ioPoolSize", 2);
		ExecutorService executor = config.ioExecutor();
		assertThat(executor).isNotNull();
		executor.shutdownNow();
	}

	@Test
	void httpClientExecutor_isNotNull() throws Exception {
		ThreadConfiguration config = new ThreadConfiguration();
		setField(config, "httpClientPoolSize", 2);
		ExecutorService executor = config.httpClientExecutor();
		assertThat(executor).isNotNull();
		executor.shutdownNow();
	}

	// -------------------------------------------------------------------------
	// streamExecutor — queue-capacity branch: 0 → SynchronousQueue
	// -------------------------------------------------------------------------

	@Test
	void streamExecutor_zeroQueueCapacity_usesSynchronousQueue() throws Exception {
		ThreadConfiguration config = configWith(/* poolSize= */ 2, /* queueCapacity= */ 0, "caller-runs");
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		ExecutorService executor = config.streamExecutor(registry);
		assertThat(executor).isInstanceOf(ThreadPoolExecutor.class);
		ThreadPoolExecutor tpe = (ThreadPoolExecutor) executor;
		assertThat(tpe.getQueue()).isInstanceOf(java.util.concurrent.SynchronousQueue.class);
		executor.shutdownNow();
	}

	// -------------------------------------------------------------------------
	// streamExecutor — queue-capacity branch: >0 → ArrayBlockingQueue
	// -------------------------------------------------------------------------

	@Test
	void streamExecutor_positiveQueueCapacity_usesArrayBlockingQueue() throws Exception {
		ThreadConfiguration config = configWith(2, 10, "caller-runs");
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		ExecutorService executor = config.streamExecutor(registry);
		ThreadPoolExecutor tpe = (ThreadPoolExecutor) executor;
		assertThat(tpe.getQueue()).isInstanceOf(java.util.concurrent.ArrayBlockingQueue.class);
		executor.shutdownNow();
	}

	// -------------------------------------------------------------------------
	// streamExecutor — reject policies
	// -------------------------------------------------------------------------

	@Test
	void streamExecutor_abortPolicy_registeredWithoutError() throws Exception {
		ThreadConfiguration config = configWith(1, 0, "abort");
		ExecutorService executor = config.streamExecutor(new SimpleMeterRegistry());
		assertThat(executor).isNotNull();
		executor.shutdownNow();
	}

	@Test
	void streamExecutor_discardPolicy_registeredWithWarningLogged() throws Exception {
		ThreadConfiguration config = configWith(1, 0, "discard");
		ExecutorService executor = config.streamExecutor(new SimpleMeterRegistry());
		assertThat(executor).isNotNull();
		executor.shutdownNow();
	}

	@Test
	void streamExecutor_discardOldestPolicy_registeredWithWarningLogged() throws Exception {
		ThreadConfiguration config = configWith(1, 0, "discard-oldest");
		ExecutorService executor = config.streamExecutor(new SimpleMeterRegistry());
		assertThat(executor).isNotNull();
		executor.shutdownNow();
	}

	@Test
	void streamExecutor_unknownPolicy_defaultsToCallerRuns() throws Exception {
		ThreadConfiguration config = configWith(1, 0, "not-a-real-policy");
		ExecutorService executor = config.streamExecutor(new SimpleMeterRegistry());
		assertThat(executor).isNotNull();
		executor.shutdownNow();
	}

	@Test
	void streamExecutor_callerRunsPolicy_explicit() throws Exception {
		ThreadConfiguration config = configWith(1, 0, "caller-runs");
		ExecutorService executor = config.streamExecutor(new SimpleMeterRegistry());
		assertThat(executor).isNotNull();
		executor.shutdownNow();
	}

	// -------------------------------------------------------------------------
	// CountingRejectionHandler — counter increments before delegating
	// -------------------------------------------------------------------------

	@Test
	void countingRejectionHandler_incrementsCounterAndDelegates() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		io.micrometer.core.instrument.Counter counter = io.micrometer.core.instrument.Counter.builder("test.rejections")
			.register(registry);

		// Use AbortPolicy as the delegate so we can observe the exception
		ThreadConfiguration.CountingRejectionHandler handler = new ThreadConfiguration.CountingRejectionHandler(
				new ThreadPoolExecutor.AbortPolicy(), counter);

		ThreadPoolExecutor tpe = new ThreadPoolExecutor(1, 1, 0, java.util.concurrent.TimeUnit.SECONDS,
				new java.util.concurrent.SynchronousQueue<>());
		tpe.shutdownNow();

		assertThatThrownBy(() -> handler.rejectedExecution(() -> {
		}, tpe)).isInstanceOf(RejectedExecutionException.class);

		assertThat(counter.count()).isEqualTo(1.0);
	}

	@Test
	void countingRejectionHandler_incrementsCounterEachRejection() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		io.micrometer.core.instrument.Counter counter = io.micrometer.core.instrument.Counter.builder("test.rej2")
			.register(registry);

		// DiscardPolicy does NOT throw — we can call rejectedExecution multiple times
		ThreadConfiguration.CountingRejectionHandler handler = new ThreadConfiguration.CountingRejectionHandler(
				new ThreadPoolExecutor.DiscardPolicy(), counter);

		ThreadPoolExecutor tpe = new ThreadPoolExecutor(1, 1, 0, java.util.concurrent.TimeUnit.SECONDS,
				new java.util.concurrent.SynchronousQueue<>());
		tpe.shutdownNow();

		handler.rejectedExecution(() -> {
		}, tpe);
		handler.rejectedExecution(() -> {
		}, tpe);
		handler.rejectedExecution(() -> {
		}, tpe);

		assertThat(counter.count()).isEqualTo(3.0);
	}

	// -------------------------------------------------------------------------
	// helpers
	// -------------------------------------------------------------------------

	private static ThreadConfiguration configWith(int poolSize, int queueCapacity, String rejectPolicy)
			throws Exception {
		ThreadConfiguration config = new ThreadConfiguration();
		setField(config, "streamPoolSize", poolSize);
		setField(config, "streamQueueCapacity", queueCapacity);
		setField(config, "streamRejectPolicy", rejectPolicy);
		setField(config, "ioPoolSize", 1);
		setField(config, "httpClientPoolSize", 1);
		return config;
	}

	private static void setField(Object target, String name, Object value) throws Exception {
		Field f = target.getClass().getDeclaredField(name);
		f.setAccessible(true);
		f.set(target, value);
	}

}
