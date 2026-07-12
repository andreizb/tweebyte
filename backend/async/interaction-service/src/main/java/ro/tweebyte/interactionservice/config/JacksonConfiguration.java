/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers Jackson's Blackbird module so the auto-configured ObjectMapper serializes and
 * deserializes DTOs through LambdaMetafactory-compiled accessors instead of reflection.
 * Spring Boot auto-registers every {@link Module} bean into the ObjectMapper that backs the
 * HTTP message codecs, so the cross-service request/response (de)serialization on the
 * user-profile fan-out — the JFR-measured Jackson/JSON slice — stops paying reflective cost.
 *
 * @author Andrei Zbarcea
 */
@Configuration
public class JacksonConfiguration {

	@Bean
	public Module blackbirdModule() {
		return new BlackbirdModule();
	}

}
