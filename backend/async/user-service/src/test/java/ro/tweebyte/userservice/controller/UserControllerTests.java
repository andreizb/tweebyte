/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.controller;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void testGetUserProfile() throws Exception {
		UUID userId = UUID.randomUUID();

		this.mockMvc.perform(MockMvcRequestBuilders.get("/users/" + userId)

			.header("Authorization", "Bearer AUTH_TOKEN")).andExpect(MockMvcResultMatchers.status().isOk());
	}

	@Test
	void testGetUserSummary() throws Exception {
		UUID userId = UUID.randomUUID();

		this.mockMvc.perform(MockMvcRequestBuilders.get("/users/summary/" + userId))
			.andExpect(MockMvcResultMatchers.status().isOk());
	}

	@Test
	void testGetUserSummaryByUserName() throws Exception {
		String userName = "testUser";

		this.mockMvc.perform(MockMvcRequestBuilders.get("/users/summary/name/" + userName))
			.andExpect(MockMvcResultMatchers.status().isOk());
	}

	@Test
	void testSearchUser() throws Exception {
		String searchTerm = "testSearch";

		this.mockMvc.perform(MockMvcRequestBuilders.get("/users/search/" + searchTerm)

			.header("Authorization", "Bearer validToken")).andExpect(MockMvcResultMatchers.status().isOk());
	}

	@Test
	void testUpdateUser() throws Exception {
		UUID userId = UUID.randomUUID();

		this.mockMvc.perform(MockMvcRequestBuilders.put("/users/" + userId)

			.param("userName", "newUserName")
			.param("email", "newEmail@example.com")
			.contentType(MediaType.MULTIPART_FORM_DATA_VALUE)).andExpect(MockMvcResultMatchers.status().isNoContent());
	}

}
