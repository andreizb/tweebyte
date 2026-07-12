/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.gatewayservice.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Edge CORS policy for the Spring Cloud Gateway Server MVC front door. Allowed origins are
 * supplied via {@code app.gateway.cors.allowed-origins} (env-overridable, default none so
 * only same-origin callers are accepted), mirroring the reactive gateway's
 * {@code globalcors} configuration.
 *
 * @author Andrei Zbarcea
 */
@Configuration
public class CorsConfiguration {

	@Bean
	public CorsFilter gatewayCorsFilter(@Value("${app.gateway.cors.allowed-origins:}") List<String> allowedOrigins) {
		org.springframework.web.cors.CorsConfiguration config = new org.springframework.web.cors.CorsConfiguration();
		config.setAllowedOrigins(List.copyOf(allowedOrigins));
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		config.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return new CorsFilter(source);
	}

}
