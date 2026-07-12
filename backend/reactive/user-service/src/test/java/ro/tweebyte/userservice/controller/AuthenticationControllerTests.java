/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

import ro.tweebyte.userservice.model.AuthenticationResponse;
import ro.tweebyte.userservice.model.UserLoginRequest;
import ro.tweebyte.userservice.service.AuthenticationService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = AuthenticationController.class,
		excludeAutoConfiguration = { ReactiveSecurityAutoConfiguration.class,
				ReactiveUserDetailsServiceAutoConfiguration.class })
class AuthenticationControllerTests {

	@Autowired
	private WebTestClient webTestClient;

	@MockBean
	private AuthenticationService authenticationService;

	@MockBean
	private ro.tweebyte.userservice.mapper.UserMapper userMapper;

	@BeforeEach
	void setUp() {
		AuthenticationResponse mockResponse = new AuthenticationResponse("TokenHere");
		given(this.authenticationService.register(any())).willReturn(Mono.just(mockResponse));
		given(this.authenticationService.login(any())).willReturn(Mono.just(mockResponse));
	}

	@Test
	void userLogin() {
		// UserLoginRequest now has @Email + @NotBlank validation, so the
		// previous "user"/"pass" payload is rejected with 400 before reaching the
		// controller. Use a valid email for the request body.
		UserLoginRequest request = new UserLoginRequest("user@example.com", "pass");
		this.webTestClient.post()
			.uri("/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(request)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.token")
			.isEqualTo("TokenHere");
	}

	@Test
	void userRegister() {
		MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
		formData.add("email", "test@example.com");
		formData.add("password", "password123");
		formData.add("birthDate", "1990-01-01");
		formData.add("userName", "JohnDoe");

		this.webTestClient.post()
			.uri("/auth/register")
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.bodyValue(formData)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.token")
			.isEqualTo("TokenHere");
	}

}
