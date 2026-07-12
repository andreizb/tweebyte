/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.gatewayservice.config;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the reactive Spring Cloud Gateway honours env-var-overridable route URIs:
 * {@code ${USER_SERVICE_URL:http://localhost:9091/}}.
 * <ul>
 * <li>{@link EnvVarSet} — when the env var is provided, the route URI honours it.</li>
 * <li>{@link EnvVarAbsent} — when the env var is not provided, the localhost default
 * applies.</li>
 * </ul>
 */
class RoutePropertyConfigTests {

	private static String findRouteUri(RouteLocator locator, String id) {
		List<Route> routes = locator.getRoutes().collectList().block();
		assertThat(routes).as("no routes loaded").isNotEmpty();
		Optional<Route> route = routes.stream().filter(r -> id.equals(r.getId())).findFirst();
		assertThat(route).as(id + " route not found").isPresent();
		return route.get().getUri().toString();
	}

	@Nested
	@SpringBootTest
	@TestPropertySource(properties = { "USER_SERVICE_URL=http://stub-user:9991/",
			"TWEET_SERVICE_URL=http://stub-tweet:9992/", "INTERACTION_SERVICE_URL=http://stub-interaction:9993/" })
	class EnvVarSet {

		@Autowired
		private RouteLocator routeLocator;

		@Test
		void userServiceRouteHonoursEnvVar() {
			String uri = findRouteUri(this.routeLocator, "user-service");
			// SCG normalises trailing slash off URIs.
			assertThat(uri).as("expected USER_SERVICE_URL override, got " + uri).startsWith("http://stub-user:9991");
		}

		@Test
		void tweetServiceRouteHonoursEnvVar() {
			String uri = findRouteUri(this.routeLocator, "tweet-service");
			assertThat(uri).as("expected TWEET_SERVICE_URL override, got " + uri).startsWith("http://stub-tweet:9992");
		}

		@Test
		void interactionServiceRouteHonoursEnvVar() {
			String uri = findRouteUri(this.routeLocator, "interaction-service");
			assertThat(uri).as("expected INTERACTION_SERVICE_URL override, got " + uri)
				.startsWith("http://stub-interaction:9993");
		}

	}

	@Nested
	@SpringBootTest
	class EnvVarAbsent {

		@Autowired
		private RouteLocator routeLocator;

		@Test
		void userServiceRouteFallsBackToLocalhost() {
			String uri = findRouteUri(this.routeLocator, "user-service");
			assertThat(uri).as("expected localhost fallback, got " + uri).startsWith("http://localhost:9091");
		}

		@Test
		void downstreamRoutesFallbackToLocalhost() {
			assertThat(findRouteUri(this.routeLocator, "tweet-service")).startsWith("http://localhost:9092");
			assertThat(findRouteUri(this.routeLocator, "interaction-service")).startsWith("http://localhost:9093");
		}

	}

}
