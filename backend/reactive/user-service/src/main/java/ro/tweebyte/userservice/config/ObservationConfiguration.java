/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Benchmark-profile-only observability trimming on the measured path. The per-request
 * {@code http.server.requests} and {@code http.client.requests} observations drive Micrometer
 * Timer construction plus Reactor/thread-local context propagation across every operator and
 * executor boundary — JFR-measured framework tax (~16-22% inclusive on the server half alone)
 * that throttles throughput without serving the workload; the orchestrator paid the client half
 * twice per request. This bean denies both, leaving every other observation and the
 * health/readiness probes intact.
 *
 * <p>Complementing it, {@code application-benchmark.properties} excludes the Micrometer-tracing /
 * OpenTelemetry / OTLP auto-configurations, so no observation — present or future — can build and
 * discard an OTel span ({@code management.tracing.enabled=false} alone did not prevent that: it
 * gates only the exporter, not span construction). Benchmark profile only — prod keeps full
 * observability and tracing. Symmetric across both stacks, so reactive keeps its lead.
 *
 * @author Andrei Zbarcea
 */
@Configuration
@Profile("benchmark")
public class ObservationConfiguration {

	@Bean
	public ObservationRegistryCustomizer<ObservationRegistry> denyHttpRequestObservations() {
		return registry -> registry.observationConfig()
			.observationPredicate((name, context) -> !"http.server.requests".equals(name)
					&& !"http.client.requests".equals(name));
	}

}
