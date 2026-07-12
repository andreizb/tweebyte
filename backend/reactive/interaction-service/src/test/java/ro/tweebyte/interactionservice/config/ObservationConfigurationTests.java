/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.config;

import java.util.concurrent.atomic.AtomicBoolean;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationRegistryCustomizer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ObservationConfiguration. Verifies the predicate logic by installing a
 * handler and checking whether it fires: a denied observation produces a no-op that never
 * calls the handler; a permitted observation is real and invokes the handler.
 *
 * The registry must have at least one handler for observations to be non-noop; without one
 * ALL observations are no-ops regardless of predicates.
 */
class ObservationConfigurationTests {

	private final ObservationConfiguration config = new ObservationConfiguration();

	/**
	 * Returns a registry with the customizer applied and a tracking handler installed so
	 * non-denied observations are real (non-noop).
	 */
	private ObservationRegistry registryWithHandler() {
		ObservationRegistry registry = ObservationRegistry.create();
		// Install a handler so the registry is non-empty and observations can be real.
		registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
			@Override
			public boolean supportsContext(Observation.Context context) {
				return true;
			}

			@Override
			public void onStart(Observation.Context context) {
			}

			@Override
			public void onStop(Observation.Context context) {
			}
		});
		ObservationRegistryCustomizer<ObservationRegistry> customizer = this.config.denyHttpRequestObservations();
		customizer.customize(registry);
		return registry;
	}

	@Test
	void beanReturnedIsNonNull() {
		assertThat(this.config.denyHttpRequestObservations()).isNotNull();
	}

	@Test
	void denyHttpServerRequests_observationIsNoop() {
		ObservationRegistry registry = registryWithHandler();
		Observation obs = Observation.start("http.server.requests", registry);
		assertThat(obs.isNoop()).as("http.server.requests should be DENIED (noop)").isTrue();
		obs.stop();
	}

	@Test
	void denyHttpClientRequests_observationIsNoop() {
		ObservationRegistry registry = registryWithHandler();
		Observation obs = Observation.start("http.client.requests", registry);
		assertThat(obs.isNoop()).as("http.client.requests should be DENIED (noop)").isTrue();
		obs.stop();
	}

	@Test
	void otherObservations_areNotNoop() {
		ObservationRegistry registry = registryWithHandler();
		Observation obs = Observation.start("db.query.time", registry);
		assertThat(obs.isNoop()).as("db.query.time should be PERMITTED (not noop)").isFalse();
		obs.stop();
	}

	@Test
	void unrelatedName_isPermitted() {
		ObservationRegistry registry = registryWithHandler();
		Observation obs = Observation.start("custom.metric", registry);
		assertThat(obs.isNoop()).as("custom.metric should be PERMITTED (not noop)").isFalse();
		obs.stop();
	}

	@Test
	void deniedName_handlerNotCalled() {
		AtomicBoolean handlerCalled = new AtomicBoolean(false);
		ObservationRegistry registry = ObservationRegistry.create();
		registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
			@Override
			public boolean supportsContext(Observation.Context context) {
				return true;
			}

			@Override
			public void onStart(Observation.Context context) {
				handlerCalled.set(true);
			}

			@Override
			public void onStop(Observation.Context context) {
				handlerCalled.set(true);
			}
		});
		this.config.denyHttpRequestObservations().customize(registry);

		Observation.start("http.server.requests", registry).stop();

		assertThat(handlerCalled.get()).as("handler must NOT be called for denied observations").isFalse();
	}

	@Test
	void permittedName_handlerIsCalled() {
		AtomicBoolean handlerCalled = new AtomicBoolean(false);
		ObservationRegistry registry = ObservationRegistry.create();
		registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
			@Override
			public boolean supportsContext(Observation.Context context) {
				return true;
			}

			@Override
			public void onStart(Observation.Context context) {
				handlerCalled.set(true);
			}

			@Override
			public void onStop(Observation.Context context) {
			}
		});
		this.config.denyHttpRequestObservations().customize(registry);

		Observation.start("db.query.time", registry).stop();

		assertThat(handlerCalled.get()).as("handler MUST be called for permitted observations").isTrue();
	}

}
