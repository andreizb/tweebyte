/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Pools named by thread-occupancy nature, not by feature.
 *
 * <p>
 * {@code ioExecutor} — blocking-I/O executor for all user-service DB work (user
 * reads/writes + auth login/register). Bounded to the Hikari pool size
 * ({@code app.concurrency.io.pool-size}, default 256) via a fixed thread pool with an
 * unbounded task queue: the work is I/O-bound so threads mostly wait on JDBC, and the
 * Hikari pool is the real throughput governor — a thread with no connection to lease is
 * waste. Each in-flight blocking call still parks a real thread, so the async stack's
 * thread and memory cost is paid in full; it is now bounded rather than
 * growing without limit, so under saturation tasks queue and wait, never reject, never
 * OOM. commonPool is deliberately NOT used here: ~NCPU threads running blocking JDBC
 * would cap throughput far below the offered concurrency.
 *
 * <p>
 * {@code cpuExecutor} — CPU-bound: the image-upload filter pipeline (POST /media/filter —
 * blur/sobel/resize/JPEG). NCPU fixed; more threads than cores is context-switch waste.
 * The file-download stream pool lives in {@link MvcAsyncConfig} as
 * {@code streamExecutor}.
 *
 * <p>
 * {@code httpClientExecutor} — blocking-I/O, long: cross-service downstream HTTP fan-out
 * (InteractionClient follow counts, TweetClient user tweets). The blocking RestClient
 * call parks a real thread for the whole round-trip, so this is where the profile
 * fan-out's thread+memory cost honestly lands. Kept SEPARATE from ioExecutor so those
 * round-trips don't starve the short, Hikari-governed DB work. Bounded to the total
 * downstream HTTP connection count ({@code app.concurrency.http-client.pool-size}, default
 * 512) via a fixed thread pool with an unbounded task queue: the pooled Apache HttpClient
 * 5 connection manager ({@code HttpClientConfiguration}, 256 per route / 512 total)
 * governs outbound concurrency, and once a route's leases are exhausted the thread
 * parks up to the connection-request timeout (45s default, 3600s under the benchmark
 * overlay) waiting for one — so the cost surfaces as parked threads and native memory,
 * bounded to the pool size rather than growing without limit. commonPool is deliberately
 * avoided: shared JVM-wide and sized ~NCPU, blocking round-trips on it would starve all
 * other commonPool work.
 *
 * @author Andrei Zbarcea
 */
@Configuration
public class ThreadConfiguration {

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

	@Bean(name = "cpuExecutor")
	public ExecutorService cpuExecutor() {
		return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	}

	@Bean(name = "httpClientExecutor")
	public ExecutorService httpClientExecutor() {
		return Executors.newFixedThreadPool(this.httpClientPoolSize);
	}

}
