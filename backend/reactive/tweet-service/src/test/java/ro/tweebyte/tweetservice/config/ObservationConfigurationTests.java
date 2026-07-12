/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationRegistryCustomizer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for reactive {@link ObservationConfiguration}.
 */
class ObservationConfigurationTests {

	private final ObservationConfiguration config = new ObservationConfiguration();

	private static final ObservationHandler<Observation.Context> STUB_HANDLER =
		new ObservationHandler<>() {
			@Override
			public boolean supportsContext(Observation.Context context) {
				return true;
			}
		};

	@Test
	void denyHttpRequestObservations_returnsNonNullCustomizer() {
		ObservationRegistryCustomizer<ObservationRegistry> customizer = this.config.denyHttpRequestObservations();
		assertThat(customizer).isNotNull();
	}

	@Test
	void customizer_appliesWithoutThrowing() {
		ObservationRegistry registry = ObservationRegistry.create();
		this.config.denyHttpRequestObservations().customize(registry);
	}

	@Test
	void observationPredicate_deniesHttpServerRequests() {
		ObservationRegistry registry = buildCustomizedRegistry();
		assertThat(Observation.createNotStarted("http.server.requests", registry).isNoop()).isTrue();
	}

	@Test
	void observationPredicate_deniesHttpClientRequests() {
		ObservationRegistry registry = buildCustomizedRegistry();
		assertThat(Observation.createNotStarted("http.client.requests", registry).isNoop()).isTrue();
	}

	@Test
	void observationPredicate_allowsOtherObservations() {
		ObservationRegistry registry = buildCustomizedRegistry();
		assertThat(Observation.createNotStarted("custom.metric", registry).isNoop()).isFalse();
	}

	@Test
	void observationPredicate_allowsReactorNettyObservations() {
		ObservationRegistry registry = buildCustomizedRegistry();
		assertThat(Observation.createNotStarted("reactor.netty.http.client", registry).isNoop()).isFalse();
	}

	private ObservationRegistry buildCustomizedRegistry() {
		ObservationRegistry registry = ObservationRegistry.create();
		registry.observationConfig().observationHandler(STUB_HANDLER);
		this.config.denyHttpRequestObservations().customize(registry);
		return registry;
	}

}
