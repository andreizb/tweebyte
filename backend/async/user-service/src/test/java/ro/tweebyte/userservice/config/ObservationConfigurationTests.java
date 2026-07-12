/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.config;

import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationRegistryCustomizer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises ObservationConfiguration — drives the predicate lambda through both
 * branches (denied observation name, allowed observation name) by directly testing
 * the predicate installed by the customizer.
 */
class ObservationConfigurationTests {

	private final ObservationConfiguration config = new ObservationConfiguration();

	@Test
	void customizer_isNotNull() {
		ObservationRegistryCustomizer<ObservationRegistry> customizer = this.config.denyHttpRequestObservations();
		assertThat(customizer).isNotNull();
	}

	/**
	 * Returns the predicate by applying the customizer to a real registry and extracting
	 * it as an {@link ObservationPredicate} that wraps the registry's isObservationEnabled
	 * check.
	 *
	 * <p>We verify the predicate indirectly: the customizer installs an AND-predicate on the
	 * registry config. We test it by creating a minimal wrapper that delegates the test
	 * to the predicate we pass in.
	 */
	private ObservationPredicate buildPredicate() {
		// Directly construct the predicate with the same logic as the production code.
		// The customizer installs:
		//   (name, context) -> !"http.server.requests".equals(name) && !"http.client.requests".equals(name)
		// We verify this by applying the customizer and then observing the predicate's effect
		// through a spy on the ObservationConfig.
		//
		// Simplest correct approach: extract it via a minimal ObservationConfig override.
		final ObservationPredicate[] holder = new ObservationPredicate[1];

		ObservationRegistry registry = new ObservationRegistry() {

			private final ObservationRegistry.ObservationConfig observationConfig = new ObservationRegistry.ObservationConfig() {

				@Override
				public ObservationRegistry.ObservationConfig observationPredicate(ObservationPredicate predicate) {
					holder[0] = predicate;
					return this;
				}

			};

			@Override
			public io.micrometer.observation.Observation getCurrentObservation() {
				return null;
			}

			@Override
			public io.micrometer.observation.Observation.Scope getCurrentObservationScope() {
				return null;
			}

			@Override
			public void setCurrentObservationScope(io.micrometer.observation.Observation.Scope scope) {
			}

			@Override
			public ObservationRegistry.ObservationConfig observationConfig() {
				return this.observationConfig;
			}

		};

		this.config.denyHttpRequestObservations().customize(registry);
		return holder[0];
	}

	@Test
	void predicate_deniesHttpServerRequests() {
		ObservationPredicate predicate = buildPredicate();
		assertThat(predicate).isNotNull();
		// "http.server.requests" → predicate returns false (observation is denied).
		assertThat(predicate.test("http.server.requests", null)).isFalse();
	}

	@Test
	void predicate_deniesHttpClientRequests() {
		ObservationPredicate predicate = buildPredicate();
		assertThat(predicate).isNotNull();
		// "http.client.requests" → predicate returns false (observation is denied).
		assertThat(predicate.test("http.client.requests", null)).isFalse();
	}

	@Test
	void predicate_allowsOtherObservationNames() {
		ObservationPredicate predicate = buildPredicate();
		assertThat(predicate).isNotNull();
		// Any other name → predicate returns true (observation is allowed).
		assertThat(predicate.test("db.query", null)).isTrue();
		assertThat(predicate.test("cache.get", null)).isTrue();
	}

}
