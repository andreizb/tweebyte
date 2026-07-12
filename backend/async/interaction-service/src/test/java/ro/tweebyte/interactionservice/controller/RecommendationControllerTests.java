/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.controller;

import java.util.Collections;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.service.RecommendationService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@WebAppConfiguration
class RecommendationControllerTests {

	private MockMvc mockMvc;

	@Autowired
	private WebApplicationContext context;

	@MockBean
	private RecommendationService recommendationService;

	private final UUID userId = UUID.randomUUID();

	@BeforeEach
	void setup() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).build();
	}

	@Test
	void findFollowRecommendations() throws Exception {
		List<UserDto> expectedRecommendations = Collections.singletonList(new UserDto());
		given(this.recommendationService.recommendUsersToFollow(any(UUID.class)))
			.willReturn(CompletableFuture.completedFuture(expectedRecommendations));

		this.mockMvc.perform(get("/recommendations/{userId}/follow", this.userId)).andExpect(status().isOk());

		verify(this.recommendationService).recommendUsersToFollow(any(UUID.class));
	}

	@Test
	void findHashtagRecommendations() throws Exception {
		List<TweetDto.HashtagDto> expectedHashtags = Collections.singletonList(new TweetDto.HashtagDto());
		given(this.recommendationService.fetchPopularHashtags())
			.willReturn(CompletableFuture.completedFuture(expectedHashtags));

		this.mockMvc.perform(get("/recommendations/hashtags").contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk());

		verify(this.recommendationService).fetchPopularHashtags();
	}

}
