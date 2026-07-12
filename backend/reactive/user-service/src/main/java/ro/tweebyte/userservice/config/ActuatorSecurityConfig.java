/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.security.reactive.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Locks the actuator management plane behind HTTP Basic in prod / functional-equivalence
 * (empty active profile). Only the metrics-bearing endpoints are gated; {@code health}
 * and {@code info} stay open so container and load-driver readiness probes keep working.
 * Application routes are left open at the service — they are gateway/JWT-gated at the
 * edge, and the direct benchmark path carries no auth.
 *
 * <p>
 * The benchmark profile excludes Spring Security auto-configuration entirely (see
 * {@code application-benchmark.properties}), so no security filter sits on the measured
 * request path and the credential below is never consulted. Bench impact: 0.
 *
 * @author Andrei Zbarcea
 */
@Configuration
@EnableWebFluxSecurity
@Profile("!benchmark")
public class ActuatorSecurityConfig {

	@Bean
	@Order(1)
	public SecurityWebFilterChain actuatorSecurityWebFilterChain(ServerHttpSecurity http, BCryptPasswordEncoder encoder,
			@Value("${app.actuator.username}") String username,
			@Value("${app.actuator.password}") String password) {
		UserDetails actuator = User.withUsername(username)
			.password(encoder.encode(password))
			.roles("ACTUATOR")
			.build();
		UserDetailsRepositoryReactiveAuthenticationManager authManager =
				new UserDetailsRepositoryReactiveAuthenticationManager(new MapReactiveUserDetailsService(actuator));
		authManager.setPasswordEncoder(encoder);
		return http.securityMatcher(EndpointRequest.toAnyEndpoint().excluding("health", "info"))
			.authorizeExchange(exchanges -> exchanges.anyExchange().hasRole("ACTUATOR"))
			.httpBasic(Customizer.withDefaults())
			.authenticationManager(authManager)
			.csrf(ServerHttpSecurity.CsrfSpec::disable)
			.build();
	}

	@Bean
	@Order(2)
	public SecurityWebFilterChain appSecurityWebFilterChain(ServerHttpSecurity http) {
		return http.authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
			.csrf(ServerHttpSecurity.CsrfSpec::disable)
			.build();
	}

}
