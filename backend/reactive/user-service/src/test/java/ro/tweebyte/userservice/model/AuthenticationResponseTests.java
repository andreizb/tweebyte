/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationResponseTests {

	@Test
	void gettersAndSettersRoundTrip() {
		AuthenticationResponse authenticationResponse = new AuthenticationResponse();
		String token = "testToken";
		authenticationResponse.setToken(token);

		assertThat(authenticationResponse.getToken()).isEqualTo(token);
	}

	@Test
	void getToken() {
		AuthenticationResponse authenticationResponse = new AuthenticationResponse();
		String token = "testToken";
		authenticationResponse.setToken(token);

		assertThat(authenticationResponse.getToken()).isEqualTo(token);
	}

	@Test
	void setToken() {
		AuthenticationResponse authenticationResponse = new AuthenticationResponse();
		String token = "testToken";
		assertThat(authenticationResponse.getToken()).isNull(); // Initially null
		authenticationResponse.setToken(token);

		assertThat(authenticationResponse.getToken()).isEqualTo(token);
	}

	@Test
	void testAllArgsConstructor() {
		AuthenticationResponse response = new AuthenticationResponse("sampleToken");

		assertThat(response).isNotNull();
		assertThat(response.getToken()).isEqualTo("sampleToken");
	}

	@Test
	void testBuilder() {
		AuthenticationResponse response = AuthenticationResponse.builder().token("sampleToken").build();

		assertThat(response).isNotNull();
		assertThat(response.getToken()).isEqualTo("sampleToken");
	}

}
