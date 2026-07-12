/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.gatewayservice.config;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies the cached JWKS provider the edge uses to verify Keycloak-issued tokens. The
 * provider resolves the realm's RS256 signing keys by {@code kid} from the realm's JWKS
 * endpoint; keys are cached and the endpoint is rate-limited so a steady request stream
 * never re-fetches and a key-rotation burst cannot hammer Keycloak.
 *
 * @author Andrei Zbarcea
 */
@Configuration
public class JwtConfiguration {

	@Value("${app.keycloak.jwks-uri}")
	private String jwksUri;

	@Bean
	public JwkProvider jwkProvider() throws MalformedURLException {
		return new JwkProviderBuilder(URI.create(this.jwksUri).toURL()).cached(10, 24, TimeUnit.HOURS)
			.rateLimited(10, 1, TimeUnit.MINUTES)
			.build();
	}

}
