/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.controller;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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

import ro.tweebyte.interactionservice.model.TweetInteractionsEntryDto;
import ro.tweebyte.interactionservice.service.TweetInteractionsService;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@WebAppConfiguration
class TweetInteractionsControllerTests {

	private static final String BASE_URL = "/tweets/interactions";

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc;

	@MockBean
	private TweetInteractionsService tweetInteractionsService;

	@BeforeEach
	void setup() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).build();
	}

	@Test
	void getTweetInteractions() throws Exception {
		UUID tweetId = UUID.randomUUID();
		TweetInteractionsEntryDto entry = new TweetInteractionsEntryDto(tweetId, 5L, 6L, 7L, null);
		given(this.tweetInteractionsService.getTweetInteractionsEntries(anyList()))
			.willReturn(CompletableFuture.completedFuture(List.of(entry)));

		MvcResult result = this.mockMvc
			.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content("[\"" + tweetId + "\"]"))
			.andExpect(status().isOk())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].tweet_id").value(tweetId.toString()))
			.andExpect(jsonPath("$[0].likes").value(5))
			.andExpect(jsonPath("$[0].replies").value(6))
			.andExpect(jsonPath("$[0].retweets").value(7));

		verify(this.tweetInteractionsService).getTweetInteractionsEntries(anyList());
	}

}
