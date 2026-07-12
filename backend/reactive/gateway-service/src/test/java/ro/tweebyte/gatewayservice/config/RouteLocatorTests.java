/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.gatewayservice.config;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts the reactive Spring Cloud Gateway exposes a {@code RouteLocator} bean. Goes a
 * little further by asserting all three downstream routes (user, tweet, interaction) are
 * wired from the route properties.
 */
@SpringBootTest
class RouteLocatorTests {

	@Autowired
	private RouteLocator routeLocator;

	@Test
	void routeLocatorBeanIsPresent() {
		assertThat(this.routeLocator).as("RouteLocator bean should be wired").isNotNull();
	}

	@Test
	void allDownstreamRoutesAreReachable() {
		List<Route> routes = this.routeLocator.getRoutes().collectList().block();
		assertThat(routes).isNotNull();
		List<String> ids = routes.stream().map(Route::getId).toList();
		assertThat(ids).as("all downstream routes must be wired: " + ids)
			.contains("user-service", "tweet-service", "interaction-service");

		StepVerifier.create(this.routeLocator.getRoutes().count())
			.assertNext(count -> assertThat(count).as("expected >=3 routes, got " + count).isGreaterThanOrEqualTo(3L))
			.verifyComplete();
	}

	@Test
	void downstreamRoutesUseLocalServiceDefaults() {
		List<Route> routes = this.routeLocator.getRoutes().collectList().block();
		assertThat(routes).isNotNull();
		Map<String, String> uris = routes.stream()
			.collect(Collectors.toMap(Route::getId, route -> route.getUri().toString(), (left, right) -> left));
		assertThat(uris.get("user-service")).startsWith("http://localhost:9091");
		assertThat(uris.get("tweet-service")).startsWith("http://localhost:9092");
		assertThat(uris.get("interaction-service")).startsWith("http://localhost:9093");
	}

}
