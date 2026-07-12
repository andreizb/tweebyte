/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.controller;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import ro.tweebyte.interactionservice.model.RetweetCreateRequest;
import ro.tweebyte.interactionservice.model.RetweetDto;
import ro.tweebyte.interactionservice.model.RetweetUpdateRequest;
import ro.tweebyte.interactionservice.service.RetweetService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@WebAppConfiguration
class RetweetControllerTests {

	private MockMvc mockMvc;

	@Autowired
	private WebApplicationContext context;

	@MockBean
	private RetweetService retweetService;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private final UUID userId = UUID.randomUUID();

	private final UUID tweetId = UUID.randomUUID();

	private final UUID retweetId = UUID.randomUUID();

	@BeforeEach
	void setup() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).build();
	}

	@Test
	void testCreateRetweet() throws Exception {
		RetweetCreateRequest request = new RetweetCreateRequest().setOriginalTweetId(this.tweetId)
			.setContent("Retweet content");
		RetweetDto expectedRetweet = new RetweetDto().setId(this.retweetId).setContent(request.getContent());

		given(this.retweetService.createRetweet(any())).willReturn(CompletableFuture.completedFuture(expectedRetweet));

		MvcResult result = this.mockMvc
			.perform(post("/retweets/{userId}", this.userId).contentType(MediaType.APPLICATION_JSON)
				.content(this.objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(this.retweetId.toString()))
			.andExpect(jsonPath("$.content").value("Retweet content"));

		verify(this.retweetService).createRetweet(any());
	}

	@Test
	void testUpdateRetweet() throws Exception {
		RetweetUpdateRequest request = new RetweetUpdateRequest().setContent("Updated content");

		given(this.retweetService.updateRetweet(any())).willReturn(CompletableFuture.completedFuture(null));

		MvcResult result = this.mockMvc
			.perform(put("/retweets/{userId}/{retweetId}", this.userId, this.retweetId).contentType(MediaType.APPLICATION_JSON)
				.content(this.objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());

		verify(this.retweetService).updateRetweet(any());
	}

	@Test
	void testDeleteRetweet() throws Exception {
		given(this.retweetService.deleteRetweet(any(), any())).willReturn(CompletableFuture.completedFuture(null));

		MvcResult result = this.mockMvc.perform(delete("/retweets/{userId}/{retweetId}", this.userId, this.retweetId))
			.andExpect(status().isOk())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());

		verify(this.retweetService).deleteRetweet(eq(this.retweetId), any());
	}

	@Test
	void testGetRetweetsByUser() throws Exception {
		List<RetweetDto> retweets = Collections
			.singletonList(new RetweetDto().setId(this.retweetId).setContent("User retweet"));

		given(this.retweetService.getRetweetsByUser(this.userId, 0, 10))
			.willReturn(CompletableFuture.completedFuture(retweets));

		MvcResult result = this.mockMvc.perform(get("/retweets/user/{userId}", this.userId))
			.andExpect(request().asyncStarted())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(this.retweetId.toString()))
			.andExpect(jsonPath("$[0].content").value("User retweet"));

		verify(this.retweetService).getRetweetsByUser(this.userId, 0, 10);
	}

	@Test
	void testGetAllRetweetsOfTweet() throws Exception {
		List<RetweetDto> retweets = Collections
			.singletonList(new RetweetDto().setId(this.retweetId).setContent("Tweet retweet"));

		given(this.retweetService.getRetweetsOfTweet(this.tweetId, 0, 10))
			.willReturn(CompletableFuture.completedFuture(retweets));

		MvcResult result = this.mockMvc.perform(get("/retweets/tweet/{tweetId}", this.tweetId))
			.andExpect(request().asyncStarted())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(this.retweetId.toString()))
			.andExpect(jsonPath("$[0].content").value("Tweet retweet"));

		verify(this.retweetService).getRetweetsOfTweet(this.tweetId, 0, 10);
	}

	@Test
	void testGetRetweetCountOfTweet() throws Exception {
		given(this.retweetService.getRetweetCountOfTweet(this.tweetId)).willReturn(CompletableFuture.completedFuture(3L));

		MvcResult result = this.mockMvc.perform(get("/retweets/tweet/{tweetId}/count", this.tweetId))
			.andExpect(request().asyncStarted())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andExpect(jsonPath("$").value(3));

		verify(this.retweetService).getRetweetCountOfTweet(this.tweetId);
	}

	@Test
	void getRetweetCountsForTweets() throws Exception {
		given(this.retweetService.getRetweetCountsForTweets(List.of(this.tweetId)))
			.willReturn(CompletableFuture.completedFuture(java.util.Map.of(this.tweetId, 4L)));

		MvcResult result = this.mockMvc
			.perform(post("/retweets/tweet/counts").contentType(MediaType.APPLICATION_JSON)
				.content("[\"" + this.tweetId + "\"]"))
			.andExpect(request().asyncStarted())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$['" + this.tweetId + "']").value(4));

		verify(this.retweetService).getRetweetCountsForTweets(List.of(this.tweetId));
	}

}
