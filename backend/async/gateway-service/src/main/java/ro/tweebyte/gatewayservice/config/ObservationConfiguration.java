/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.gatewayservice.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Suppresses the per-request {@code http.server.requests} observation on the measured
 * benchmark path. That one observation drives Micrometer Timer construction plus
 * Reactor context-propagation across every operator boundary, which JFR measured at
 * ~16-22% inclusive CPU on the hot read paths — framework tax that throttles throughput
 * without serving the workload. Denying just this observation (symmetric with the async
 * stack, which pays the same tax as thread-local context copying across its executor
 * hops) leaves every other observation, the {@code http.client.requests} east-west
 * instrumentation, and the health/readiness probes intact. Benchmark profile only —
 * prod keeps full server-request observability.
 *
 * @author Andrei Zbarcea
 */
@Configuration
@Profile("benchmark")
public class ObservationConfiguration {

	@Bean
	public ObservationRegistryCustomizer<ObservationRegistry> denyHttpServerRequestsObservation() {
		return registry -> registry.observationConfig()
			.observationPredicate((name, context) -> !"http.server.requests".equals(name));
	}

}
