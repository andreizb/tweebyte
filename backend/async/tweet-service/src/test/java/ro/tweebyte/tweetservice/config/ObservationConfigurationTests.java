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
 * Unit tests for {@link ObservationConfiguration}.
 * Verifies that the customizer is produced and that its predicate correctly
 * allows / denies the two observation names it is responsible for filtering.
 *
 * <p>Micrometer returns {@link Observation#NOOP} when (a) the registry has no handlers
 * OR (b) a predicate denies the name. A no-op stub handler is registered so that
 * permitted observations appear non-noop while denied ones stay noop.
 */
class ObservationConfigurationTests {

	private final ObservationConfiguration config = new ObservationConfiguration();

	// Minimal no-op handler that makes observations non-noop when permitted.
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
		ObservationRegistryCustomizer<ObservationRegistry> customizer = this.config.denyHttpRequestObservations();
		ObservationRegistry registry = ObservationRegistry.create();
		customizer.customize(registry);
	}

	@Test
	void observationPredicate_deniesHttpServerRequests() {
		ObservationRegistry registry = buildCustomizedRegistry();
		Observation obs = Observation.createNotStarted("http.server.requests", registry);
		assertThat(obs.isNoop()).isTrue();
	}

	@Test
	void observationPredicate_deniesHttpClientRequests() {
		ObservationRegistry registry = buildCustomizedRegistry();
		Observation obs = Observation.createNotStarted("http.client.requests", registry);
		assertThat(obs.isNoop()).isTrue();
	}

	@Test
	void observationPredicate_allowsOtherObservations() {
		ObservationRegistry registry = buildCustomizedRegistry();
		Observation obs = Observation.createNotStarted("custom.metric", registry);
		// A permitted observation with a registered handler is NOT noop
		assertThat(obs.isNoop()).isFalse();
	}

	@Test
	void observationPredicate_allowsSpringDataObservations() {
		ObservationRegistry registry = buildCustomizedRegistry();
		Observation obs = Observation.createNotStarted("spring.data.repository.invocations", registry);
		assertThat(obs.isNoop()).isFalse();
	}

	// -------------------------------------------------------------------------
	// helper
	// -------------------------------------------------------------------------

	private ObservationRegistry buildCustomizedRegistry() {
		ObservationRegistry registry = ObservationRegistry.create();
		// Register a stub handler so permitted observations become non-noop
		registry.observationConfig().observationHandler(STUB_HANDLER);
		this.config.denyHttpRequestObservations().customize(registry);
		return registry;
	}

}
