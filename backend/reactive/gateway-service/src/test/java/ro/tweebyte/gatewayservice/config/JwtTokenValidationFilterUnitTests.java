/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.gatewayservice.config;

import com.auth0.jwk.JwkProvider;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cloud.gateway.filter.NettyRoutingFilter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit assertion that the JWT filter's order beats Spring Cloud Gateway's routing
 * filters. On reactive, "lower number runs first"; the routing filter's order is exposed
 * by the {@link NettyRoutingFilter#getOrder()} contract (Integer.MAX_VALUE in SCG 4.x).
 */
class JwtTokenValidationFilterUnitTests {

	@Test
	void getOrderRunsBeforeRoutingFilter() {
		JwkProvider jwkProvider = Mockito.mock(JwkProvider.class);
		JwtTokenValidationFilter filter = new JwtTokenValidationFilter(jwkProvider, new OwnershipRuleSet(),
				"http://localhost:8090/realms/tweebyte");
		int routingOrder = Integer.MAX_VALUE; // NettyRoutingFilter terminal order on SCG
												// 4.x.
		assertThat(filter.getOrder()).as("JWT filter order must precede NettyRoutingFilter and route-mapping filters")
			.isLessThan(routingOrder)
			.isNegative();
	}

}
