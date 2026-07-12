/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	private static final String BEARER_SCHEME = "bearer-jwt";

	@Bean
	public OpenAPI interactionServiceOpenApi() {
		return new OpenAPI()
			.info(new Info().title("Tweebyte Interaction Service API")
				.version("0.0.1-SNAPSHOT")
				.description("Likes, follows, recommendations and trending hashtags."))
			.components(new Components().addSecuritySchemes(BEARER_SCHEME,
					new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
			.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
	}

}
