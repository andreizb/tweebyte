/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import ro.tweebyte.userservice.config.BoundedElasticMetrics;

import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
class UserServiceApplicationTests {

	@MockBean
	private BoundedElasticMetrics boundedElasticMetrics;

	@MockBean
	private ro.tweebyte.userservice.mapper.UserMapper userMapper;

	@Test
	void contextLoads() {
	}

	@Test
	void main() {
		// Invokes the application entrypoint
		// directly so SpringApplication.run is exercised. The surrounding
		// @SpringBootTest already provides config / mocked beans for the second
		// context boot.
		assertThatCode(() -> UserServiceApplication.main(new String[] {})).doesNotThrowAnyException();
	}

}
