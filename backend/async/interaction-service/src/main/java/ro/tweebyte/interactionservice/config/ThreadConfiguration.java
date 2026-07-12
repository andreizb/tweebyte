/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.config;

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
 * {@code ioExecutor} — blocking-I/O executor for all interaction-service DB work
 * (follows, likes, replies, retweets, recommendations — reads and writes). Bounded to the
 * Hikari pool size ({@code app.concurrency.io.pool-size}, default 256) via a fixed thread
 * pool with an unbounded task queue: I/O-bound, threads wait on JDBC, Hikari pool is the
 * real throughput governor, so a thread with no connection to lease is waste. Each
 * in-flight blocking call still parks a real thread — the async memory cost under load —
 * now bounded rather than growing without limit, so under saturation tasks
 * queue and wait, never reject, never OOM. {@code @Primary} so the unqualified
 * {@code ExecutorService} injection points (e.g. FollowService) keep resolving to it.
 * The blocking Redis cache reads also run on this pool, now backed by the Lettuce
 * connection pool ({@code spring.data.redis.lettuce.pool.*}) sized to match it.
 *
 * <p>
 * {@code httpClientExecutor} — blocking-I/O, long: cross-service downstream HTTP fan-out
 * (UserClient user summaries, TweetClient tweet summaries / popular hashtags). The
 * blocking RestClient call parks a real thread for the whole round-trip, so this is where
 * the fan-out's thread+memory cost honestly lands. Kept SEPARATE from ioExecutor so those
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

	@Bean(name = "httpClientExecutor")
	public ExecutorService httpClientExecutor() {
		return Executors.newFixedThreadPool(this.httpClientPoolSize);
	}

}
