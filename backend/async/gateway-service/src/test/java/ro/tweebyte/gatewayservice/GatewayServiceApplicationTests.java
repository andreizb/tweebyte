/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.gatewayservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayServiceApplicationTests {

	@Test
	void main() {
		SpringApplication app = new SpringApplication(GatewayServiceApplication.class);
		try (ConfigurableApplicationContext ctx = app.run("--server.port=0")) {
			assertThat(ctx.isRunning()).isTrue();
		}
	}

}
