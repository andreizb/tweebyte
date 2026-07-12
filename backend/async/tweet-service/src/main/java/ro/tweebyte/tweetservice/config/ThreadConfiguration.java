/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.config;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Pools named by thread-occupancy nature, not by feature.
 *
 * <ul>
 * <li>{@code ioExecutor} — blocking-I/O, short: tweet DB CRUD (create/update/
 * delete/get). Bounded to the Hikari pool size ({@code app.concurrency.io.pool-size},
 * default 256) via a fixed thread pool with an unbounded task queue, Hikari-governed: a
 * thread with no connection to lease is waste, and under saturation tasks queue and wait
 * rather than reject or OOM. Kept separate from the AI-SSE pool so seconds-long streams
 * cannot starve ms-long DB writes.</li>
 * <li>{@code streamExecutor} — long-held: AI SSE (/tweets/ai/*). Bounded + reject policy
 * (abort) because a stream parks its thread for seconds and saturation must surface as a
 * clean 5xx, not a silently dropped emitter. Benchmark profiles can sweep pool sizes
 * {200/400/800/1600}.</li>
 * <li>{@code httpClientExecutor} — blocking-I/O, long: cross-service downstream HTTP
 * fan-out (InteractionClient/UserClient). Async MVC frees the Tomcat worker via
 * CompletableFuture, but a blocking RestClient call still parks a real thread for the
 * whole cross-service round-trip — so this is where the timeline fan-out's thread+memory
 * cost honestly lands. Kept SEPARATE from ioExecutor because those round-trips are long
 * and would starve the short, Hikari-governed DB CRUD if they shared a pool (the same
 * starvation this split was created to fix). Bounded to the total downstream HTTP
 * connection count ({@code app.concurrency.http-client.pool-size}, default 512) via a
 * fixed thread pool with an unbounded task queue, like ioExecutor: the pooled Apache
 * HttpClient 5 connection manager ({@code HttpClientConfiguration}, 256 per route / 512
 * total) governs outbound concurrency, and once a route's leases are exhausted the thread
 * parks up to the connection-request timeout (45s default, 3600s under the benchmark
 * overlay) waiting for one, so the cost surfaces as parked threads and native memory,
 * bounded to the pool size rather than growing without limit. commonPool is deliberately
 * avoided: shared JVM-wide and sized ~NCPU, blocking I/O on it starves all other
 * commonPool work.</li>
 * </ul>
 *
 * @author Andrei Zbarcea
 */
@Configuration
@Slf4j
public class ThreadConfiguration {

	@Value("${app.concurrency.stream.pool-size:200}")
	private int streamPoolSize;

	@Value("${app.concurrency.stream.queue-capacity:0}")
	private int streamQueueCapacity;

	@Value("${app.concurrency.stream.reject-policy:caller-runs}")
	private String streamRejectPolicy;

	// ioExecutor / httpClientExecutor are bounded to the connection pool each feeds — a thread
	// with no connection is waste. Bounded pool + unbounded queue (newFixedThreadPool): under
	// saturation tasks queue and wait, never reject, never OOM. ioExecutor = DB pool;
	// httpClientExecutor = total downstream HTTP connections.
	@Value("${app.concurrency.io.pool-size:200}")
	private int ioPoolSize;

	@Value("${app.concurrency.http-client.pool-size:2000}")
	private int httpClientPoolSize;

	@Bean(name = "ioExecutor")
	@Primary
	public ExecutorService ioExecutor() {
		return Executors.newFixedThreadPool(this.ioPoolSize);
	}

	@Bean(name = "httpClientExecutor")
	public ExecutorService httpClientExecutor() {
		return Executors.newFixedThreadPool(this.httpClientPoolSize);
	}

	@Bean(name = "streamExecutor")
	public ExecutorService streamExecutor(MeterRegistry registry) {
		// queueCapacity == 0 → direct hand-off via SynchronousQueue.
		BlockingQueue<Runnable> queue = (this.streamQueueCapacity > 0)
				? new ArrayBlockingQueue<>(this.streamQueueCapacity) : new SynchronousQueue<>();
		RejectedExecutionHandler baseHandler = switch (this.streamRejectPolicy) {
			case "abort" -> new ThreadPoolExecutor.AbortPolicy();
			case "discard" -> new ThreadPoolExecutor.DiscardPolicy();
			case "discard-oldest" -> new ThreadPoolExecutor.DiscardOldestPolicy();
			default -> new ThreadPoolExecutor.CallerRunsPolicy();
		};
		// SSE caveat: discard / discard-oldest silently drop the submitted
		// Runnable, but AiController has already returned an open SseEmitter →
		// the client hangs until read timeout. `abort` (clean 5xx) is the
		// benchmark default; `caller-runs` is dev-safe. Warn loudly otherwise.
		if ("discard".equals(this.streamRejectPolicy) || "discard-oldest".equals(this.streamRejectPolicy)) {
			log.warn(
					"WARNING: app.concurrency.stream.reject-policy={} silently drops rejected tasks. "
							+ "AI streaming endpoints (/tweets/ai/*) return an open SseEmitter BEFORE the worker "
							+ "starts, so a dropped task leaves the SSE response open until the client times out "
							+ "— not a clean rejection signal. Use 'abort' or 'caller-runs' for AI sweeps.",
					this.streamRejectPolicy);
		}
		Counter rejectionCounter = Counter.builder("tweebyte.pool.rejections")
			.description("Count of tasks rejected by the stream executor (delegates to the configured reject policy)")
			.register(registry);
		RejectedExecutionHandler countingHandler = new CountingRejectionHandler(baseHandler, rejectionCounter);
		return new ThreadPoolExecutor(this.streamPoolSize, this.streamPoolSize, 60L, TimeUnit.SECONDS, queue,
				countingHandler);
	}

	/**
	 * Delegating {@link RejectedExecutionHandler} that increments a Micrometer counter
	 * before forwarding to the configured base policy, so the "queue overflowed"
	 * observable isn't stuck at zero during AI sweeps.
	 */
	static final class CountingRejectionHandler implements RejectedExecutionHandler {

		private final RejectedExecutionHandler delegate;

		private final Counter counter;

		CountingRejectionHandler(RejectedExecutionHandler delegate, Counter counter) {
			this.delegate = delegate;
			this.counter = counter;
		}

		@Override
		public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
			this.counter.increment();
			this.delegate.rejectedExecution(runnable, executor);
		}

	}

}
