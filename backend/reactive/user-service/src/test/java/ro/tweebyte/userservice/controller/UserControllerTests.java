/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.controller;

import java.util.UUID;

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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import ro.tweebyte.userservice.model.UserDto;
import ro.tweebyte.userservice.model.UserUpdateRequest;
import ro.tweebyte.userservice.service.UserService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = UserController.class,
		excludeAutoConfiguration = { ReactiveSecurityAutoConfiguration.class,
				ReactiveUserDetailsServiceAutoConfiguration.class })
class UserControllerTests {

	@Autowired
	private WebTestClient webTestClient;

	@MockBean
	private UserService userService;

	private final UUID testUserId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		UserDto testUserDto = new UserDto();
		testUserDto.setId(this.testUserId);
		testUserDto.setUserName("Test User");
		testUserDto.setEmail("test@example.com");
		testUserDto.setBiography("Bio");

		given(this.userService.getUserProfile(any(UUID.class))).willReturn(Mono.just(testUserDto));
		given(this.userService.getUserSummary(any(UUID.class))).willReturn(Mono.just(testUserDto));
		given(this.userService.getUserSummaryByUserName(any(String.class))).willReturn(Mono.just(testUserDto));
		given(this.userService.searchUser(any(String.class), anyInt(), anyInt())).willReturn(Flux.just(testUserDto));
		given(this.userService.updateUser(eq(this.testUserId), any(UserUpdateRequest.class))).willReturn(Mono.empty());
	}

	@Test
	void getUserProfile() {
		this.webTestClient.get()
			.uri("/users/{userId}", this.testUserId)
			.header("Authorization", "Bearer someToken")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.id")
			.isEqualTo(this.testUserId.toString());
	}

	@Test
	void getUserSummary() {
		this.webTestClient.get()
			.uri("/users/summary/{userId}", this.testUserId)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.id")
			.isEqualTo(this.testUserId.toString());
	}

	@Test
	void getUserSummaryByUserName() {
		String testUserName = "Test User";
		this.webTestClient.get()
			.uri("/users/summary/name/{userName}", testUserName)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.user_name")
			.isEqualTo(testUserName);
	}

	@Test
	void searchUser() {
		String searchTerm = "search";
		this.webTestClient.get()
			.uri("/users/search/{searchTerm}", searchTerm)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBodyList(UserDto.class)
			.hasSize(1);
	}

	@Test
	void testUpdateUser() {
		MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
		formData.add("userName", "newUserName");
		formData.add("email", "newEmail@example.com");

		this.webTestClient.put()
			.uri("/users/{userId}", this.testUserId)
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.bodyValue(formData)
			.exchange()
			.expectStatus()
			.isNoContent();
	}

}
