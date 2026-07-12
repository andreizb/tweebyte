/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.cache;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Hands the continuation after a Redis read off the single shared Lettuce IO thread.
 *
 * <p>{@code ReactiveRedisTemplate} multiplexes every command over one Lettuce connection,
 * so every Redis response emits on the single {@code lettuce-epollEventLoop} thread; in
 * Reactor the downstream operators then run inline on that emitting thread unless a
 * scheduler hand-off intervenes. On the read-heavy interaction paths that meant cache-miss
 * DB issuance, {@code EVAL} write-backs and response assembly all serialised on one thread
 * while the HTTP workers idled. Wrapping each cache read in {@link #offload} re-publishes
 * its continuation onto the parallel scheduler, restoring the work-spreading the blocking
 * (async) stack gets for free from its worker-per-request model — so this is a
 * capability-symmetry fix, not a new advantage.
 *
 * @author Andrei Zbarcea
 */
public final class CacheReads {

	private CacheReads() {
	}

	public static <T> Mono<T> offload(Mono<T> afterRedisRead) {
		return afterRedisRead.publishOn(Schedulers.parallel());
	}

}
