/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
class UserServiceApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void main() {
		assertThatCode(() -> UserServiceApplication.main(new String[] {})).doesNotThrowAnyException();
	}

}
