/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TweetServiceApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void main() {
		SpringApplication app = new SpringApplication(TweetServiceApplication.class);
		try (ConfigurableApplicationContext ctx = app.run("--server.port=0")) {
			assertThat(ctx.isRunning()).isTrue();
		}
	}

}
