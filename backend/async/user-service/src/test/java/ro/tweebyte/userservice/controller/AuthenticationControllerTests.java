/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.controller;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import ro.tweebyte.userservice.model.AuthenticationResponse;
import ro.tweebyte.userservice.service.AuthenticationService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class AuthenticationControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private AuthenticationService authenticationService;

	@BeforeEach
	void setUp() {
		AuthenticationResponse mockResponse = new AuthenticationResponse("TokenHere");
		given(this.authenticationService.register(any()))
			.willReturn(CompletableFuture.completedFuture(mockResponse));
		given(this.authenticationService.login(any())).willReturn(CompletableFuture.completedFuture(mockResponse));
	}

	@Test
	void testUserRegister() throws Exception {
		MvcResult mvcResult = this.mockMvc
			.perform(MockMvcRequestBuilders.multipart("/auth/register")
				.param("userName", "testUser")
				.param("password", "testPassword")
				.param("birthDate", "1990-01-01")
				.param("email", "test@test.com"))
			.andExpect(MockMvcResultMatchers.request().asyncStarted())
			.andReturn();

		this.mockMvc.perform(MockMvcRequestBuilders.asyncDispatch(mvcResult))
			.andExpect(MockMvcResultMatchers.status().isOk())
			.andExpect(MockMvcResultMatchers.jsonPath("$.token").value("TokenHere"));
	}

	@Test
	void testUserLogin() throws Exception {
		String body = """
				{"email":"test@test.com","password":"testPassword"}""";

		MvcResult mvcResult = this.mockMvc
			.perform(MockMvcRequestBuilders.post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(MockMvcResultMatchers.request().asyncStarted())
			.andReturn();

		this.mockMvc.perform(MockMvcRequestBuilders.asyncDispatch(mvcResult))
			.andExpect(MockMvcResultMatchers.status().isOk())
			.andExpect(MockMvcResultMatchers.jsonPath("$.token").value("TokenHere"));
	}

}
