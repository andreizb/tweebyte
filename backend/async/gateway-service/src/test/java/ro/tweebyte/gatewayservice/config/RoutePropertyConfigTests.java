/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.gatewayservice.config;

import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.servlet.function.RouterFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Spring Cloud Gateway Server MVC gateway registers one proxy
 * {@link RouterFunction} per downstream service and that each route URI is sourced from an
 * env-var-overridable property ({@code app.gateway.<id>-uri=${USER_SERVICE_URL:...}}).
 * <ul>
 * <li>{@link DefaultProperties} - the three route beans are wired under the defaults.</li>
 * <li>{@link OverriddenProperties} - an explicit override is accepted and resolved, the
 * same binding an env var drives in a container.</li>
 * </ul>
 *
 * <p>The proxy target is opaque once a route is built, so these tests assert bean
 * registration and the property-binding contract rather than the downstream URI baked into
 * each {@link RouterFunction}.
 */
class RoutePropertyConfigTests {

	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
	class DefaultProperties {

		@Autowired
		private ApplicationContext context;

		@Test
		void registersOneRoutePerDownstreamService() {
			Map<String, RouterFunction> routes = this.context.getBeansOfType(RouterFunction.class);
			assertThat(routes).containsKeys("userServiceRoute", "tweetServiceRoute", "interactionServiceRoute");
		}

	}

	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
	@TestPropertySource(properties = { "app.gateway.user-service-uri=http://stub-user:9991/",
			"app.gateway.tweet-service-uri=http://stub-tweet:9992/",
			"app.gateway.interaction-service-uri=http://stub-interaction:9993/" })
	class OverriddenProperties {

		@Autowired
		private ApplicationContext context;

		@Autowired
		private Environment environment;

		@Test
		void acceptsOverriddenRouteUris() {
			Map<String, RouterFunction> routes = this.context.getBeansOfType(RouterFunction.class);
			assertThat(routes).containsKeys("userServiceRoute", "tweetServiceRoute", "interactionServiceRoute");
			assertThat(this.environment.getProperty("app.gateway.user-service-uri"))
				.isEqualTo("http://stub-user:9991/");
		}

	}

}
