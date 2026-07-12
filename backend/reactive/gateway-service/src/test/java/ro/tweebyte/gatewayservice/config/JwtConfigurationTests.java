/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.gatewayservice.config;

import com.auth0.jwk.JwkProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class JwtConfigurationTests {

	@Autowired
	private JwkProvider jwkProvider;

	@Test
	void jwkProviderBeanIsBuilt() {
		assertThat(this.jwkProvider).as("A cached JwkProvider must be wired to verify Keycloak tokens").isNotNull();
	}

}
