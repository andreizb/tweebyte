/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkProfilePropertiesTests {

	@Test
	void benchmarkProfileNeutralizesResilience4jAndDeclaresTheMeasuredKnobs() throws IOException {
		try (InputStream inputStream = getClass().getClassLoader()
			.getResourceAsStream("application-benchmark.properties")) {
			assertThat(inputStream).isNotNull();

			Properties properties = new Properties();
			properties.load(inputStream);

			// Resilience4j is neutralized through its configs.default (never-trip breaker + inert
			// retry/bulkhead/timelimiter/ratelimiter), NOT through resilience4j.<aspect>.enabled —
			// those are not real properties in resilience4j-spring-boot3 2.2.0 and must stay absent.
			assertThat(properties.getProperty("resilience4j.circuitbreaker.enabled")).isNull();
			assertThat(properties.getProperty("resilience4j.retry.enabled")).isNull();
			assertThat(properties.getProperty("resilience4j.circuitbreaker.configs.default.slidingWindowType"))
				.isEqualTo("TIME_BASED");
			assertThat(properties.getProperty("resilience4j.circuitbreaker.configs.default.minimumNumberOfCalls"))
				.isEqualTo("2147483647");
			assertThat(properties.getProperty("resilience4j.retry.configs.default.maxAttempts")).isEqualTo("1");
			assertThat(properties.getProperty("resilience4j.bulkhead.configs.default.maxConcurrentCalls"))
				.isEqualTo("2147483647");

			// Measured benchmark knobs declared on the measured path: the DB pool auto-grows to
			// max-size, the colocation fix is on, and acquire waits are effectively infinite so
			// saturation queues instead of erroring (0% errors).
			assertThat(properties.getProperty("spring.r2dbc.pool.max-size")).isEqualTo("256");
			assertThat(properties.getProperty("spring.r2dbc.pool.min-idle")).isEqualTo("10");
			assertThat(properties.getProperty("spring.r2dbc.pool.max-acquire-time")).isEqualTo("3600s");
			assertThat(properties.getProperty("app.r2dbc.disable-colocation")).isEqualTo("true");
			assertThat(properties.getProperty("app.http.downstream.acquire-timeout-seconds")).isEqualTo("3600");
		}
	}

}
