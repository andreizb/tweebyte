/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserLoginRequestTests {

	@Test
	void getEmail() {
		String email = "test@example.com";
		UserLoginRequest userLoginRequest = new UserLoginRequest();
		userLoginRequest.setEmail(email);
		assertThat(userLoginRequest.getEmail()).isEqualTo(email);
	}

	@Test
	void getPassword() {
		String password = "testPassword";
		UserLoginRequest userLoginRequest = new UserLoginRequest();
		userLoginRequest.setPassword(password);
		assertThat(userLoginRequest.getPassword()).isEqualTo(password);
	}

	@Test
	void setEmail() {
		String email = "test@example.com";
		UserLoginRequest userLoginRequest = new UserLoginRequest();
		assertThat(userLoginRequest.getEmail()).isNull(); // Initially null
		userLoginRequest.setEmail(email);
		assertThat(userLoginRequest.getEmail()).isEqualTo(email);
	}

	@Test
	void setPassword() {
		String password = "testPassword";
		UserLoginRequest userLoginRequest = new UserLoginRequest();
		assertThat(userLoginRequest.getPassword()).isNull(); // Initially null
		userLoginRequest.setPassword(password);
		assertThat(userLoginRequest.getPassword()).isEqualTo(password);
	}

	@Test
	void testUserLoginRequestBuilder() {
		UserLoginRequest request = UserLoginRequest.builder()
			.email("test@example.com")
			.password("securePassword123")
			.build();

		assertThat(request.getEmail()).isEqualTo("test@example.com");
		assertThat(request.getPassword()).isEqualTo("securePassword123");
	}

}
