/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

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
@EnableWebSecurity
@Profile("!benchmark")
public class ActuatorSecurityConfig {

	@Bean
	@Order(1)
	public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http,
			@Value("${app.actuator.username}") String username,
			@Value("${app.actuator.password}") String password) throws Exception {
		BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
		UserDetails actuator = User.withUsername(username)
			.password(encoder.encode(password))
			.roles("ACTUATOR")
			.build();
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
		provider.setUserDetailsService(new InMemoryUserDetailsManager(actuator));
		provider.setPasswordEncoder(encoder);
		return http.securityMatcher(EndpointRequest.toAnyEndpoint().excluding("health", "info"))
			.authorizeHttpRequests(requests -> requests.anyRequest().hasRole("ACTUATOR"))
			.httpBasic(Customizer.withDefaults())
			.authenticationManager(new ProviderManager(provider))
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf(AbstractHttpConfigurer::disable)
			.build();
	}

	@Bean
	@Order(2)
	public SecurityFilterChain appSecurityFilterChain(HttpSecurity http) throws Exception {
		return http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
			.csrf(AbstractHttpConfigurer::disable)
			.build();
	}

}
