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

	private ObservationPredicate buildPredicate() {
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
		assertThat(predicate.test("http.server.requests", null)).isFalse();
	}

	@Test
	void predicate_deniesHttpClientRequests() {
		ObservationPredicate predicate = buildPredicate();
		assertThat(predicate).isNotNull();
		assertThat(predicate.test("http.client.requests", null)).isFalse();
	}

	@Test
	void predicate_allowsOtherObservationNames() {
		ObservationPredicate predicate = buildPredicate();
		assertThat(predicate).isNotNull();
		assertThat(predicate.test("db.query", null)).isTrue();
	}

}
