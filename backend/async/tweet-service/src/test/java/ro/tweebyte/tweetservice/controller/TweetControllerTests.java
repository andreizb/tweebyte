/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.controller;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import ro.tweebyte.tweetservice.model.TweetCreationRequest;
import ro.tweebyte.tweetservice.model.TweetUpdateRequest;
import ro.tweebyte.tweetservice.service.TweetService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@WebAppConfiguration
class TweetControllerTests {

	private MockMvc mockMvc;

	@Autowired
	private WebApplicationContext context;

	@MockBean
	private TweetService tweetService;

	@BeforeEach
	void setup() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).build();
	}

	@Test
	void testGetFeed() throws Exception {
		this.mockMvc.perform(get("/tweets/{userId}/feed", UUID.randomUUID()).header("Authorization", "AUTH_TOKEN"))
			.andExpect(status().isOk())
			.andDo(print());
	}

	@Test
	void testGetTweet() throws Exception {
		this.mockMvc
			.perform(get("/tweets/{tweetId}", UUID.randomUUID().toString()).header("Authorization", "AUTH_TOKEN"))
			.andExpect(status().isOk())
			.andDo(print());
	}

	@Test
	void testSearchTweets() throws Exception {
		this.mockMvc.perform(get("/tweets/search/{searchTerm}", "search-keyword"))
			.andExpect(status().isOk())
			.andDo(print());
	}

	@Test
	void testSearchTweetsByHashtag() throws Exception {
		this.mockMvc.perform(get("/tweets/search/hashtag/{searchTerm}", "hashtag-keyword"))
			.andExpect(status().isOk())
			.andDo(print());
	}

	@Test
	void testComputePopularHashtags() throws Exception {
		this.mockMvc.perform(get("/tweets/hashtag/popular")).andExpect(status().isOk()).andDo(print());
	}

	@Test
	void testGetUserTweets() throws Exception {
		this.mockMvc
			.perform(get("/tweets/user/{userId}", UUID.randomUUID().toString()).header("Authorization", "AUTH_TOKEN"))
			.andExpect(status().isOk())
			.andDo(print());
	}

	@Test
	void testGetTweetSummary() throws Exception {
		this.mockMvc.perform(get("/tweets/{tweetId}/summary", UUID.randomUUID().toString()))
			.andExpect(status().isOk())
			.andDo(print());
	}

	@Test
	void testGetUserTweetsSummary() throws Exception {
		this.mockMvc.perform(get("/tweets/user/{userId}/summary", UUID.randomUUID().toString()))
			.andExpect(status().isOk())
			.andDo(print());
	}

	@Test
	void testCreateTweet() throws Exception {
		TweetCreationRequest creationRequest = TweetCreationRequest.builder().content("This is a test tweet").build();

		this.mockMvc
			.perform(MockMvcRequestBuilders.post("/tweets/{userId}", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(new ObjectMapper().writeValueAsString(creationRequest)))
			.andExpect(status().isOk())
			.andDo(print());

		verify(this.tweetService).createTweet(any(TweetCreationRequest.class));
	}

	@Test
	void testUpdateTweet() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID tweetId = UUID.randomUUID();
		TweetUpdateRequest updateRequest = TweetUpdateRequest.builder().content("Updated test tweet content").build();

		this.mockMvc
			.perform(MockMvcRequestBuilders.put("/tweets/{userId}/{tweetId}", userId, tweetId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(new ObjectMapper().writeValueAsString(updateRequest)))
			.andExpect(status().isOk())
			.andDo(print());

		verify(this.tweetService).updateTweet(any(TweetUpdateRequest.class));
	}

	@Test
	void testDeleteTweet() throws Exception {
		this.mockMvc
			.perform(MockMvcRequestBuilders.delete("/tweets/{userId}/{tweetId}", UUID.randomUUID().toString(),
					UUID.randomUUID().toString()))
			.andExpect(status().isNoContent());
	}

}
