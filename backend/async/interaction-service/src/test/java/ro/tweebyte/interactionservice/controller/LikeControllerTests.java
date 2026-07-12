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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import ro.tweebyte.interactionservice.model.LikeDto;
import ro.tweebyte.interactionservice.service.LikeService;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@WebAppConfiguration
class LikeControllerTests {

	private static final String BASE_URL = "/likes";

	private final UUID userId = UUID.randomUUID();

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc;

	@MockBean
	private LikeService likeService;

	@BeforeEach
	void setup() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).build();
	}

	@Test
	void getUserLikes() throws Exception {
		List<LikeDto> mockLikes = Collections.emptyList();
		given(this.likeService.getUserLikes(this.userId, 0, 10))
			.willReturn(CompletableFuture.completedFuture(mockLikes));

		MvcResult result = this.mockMvc.perform(get(BASE_URL + "/user/{userId}", this.userId))
			.andExpect(status().isOk())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());

		verify(this.likeService).getUserLikes(this.userId, 0, 10);
	}

	@Test
	void getTweetLikes() throws Exception {
		UUID tweetId = UUID.randomUUID();
		List<LikeDto> mockLikes = Collections.emptyList();
		given(this.likeService.getTweetLikes(tweetId, 0, 10))
			.willReturn(CompletableFuture.completedFuture(mockLikes));

		MvcResult result = this.mockMvc.perform(get(BASE_URL + "/tweet/{tweetId}", tweetId))
			.andExpect(status().isOk())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());

		verify(this.likeService).getTweetLikes(tweetId, 0, 10);
	}

	@Test
	void getTweetLikesCount() throws Exception {
		UUID tweetId = UUID.randomUUID();
		given(this.likeService.getTweetLikesCount(tweetId)).willReturn(CompletableFuture.completedFuture(20L));

		MvcResult result = this.mockMvc.perform(get(BASE_URL + "/{tweetId}/count", tweetId))
			.andExpect(status().isOk())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andExpect(content().string("20"));

		verify(this.likeService).getTweetLikesCount(tweetId);
	}

	@Test
	void getTweetLikesCounts() throws Exception {
		UUID tweetId = UUID.randomUUID();
		given(this.likeService.getTweetLikesCounts(List.of(tweetId)))
			.willReturn(CompletableFuture.completedFuture(java.util.Map.of(tweetId, 42L)));

		MvcResult result = this.mockMvc
			.perform(post(BASE_URL + "/counts").contentType(MediaType.APPLICATION_JSON)
				.content("[\"" + tweetId + "\"]"))
			.andExpect(status().isOk())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$['" + tweetId + "']").value(42));

		verify(this.likeService).getTweetLikesCounts(List.of(tweetId));
	}

	@Test
	void likeTweet() throws Exception {
		UUID tweetId = UUID.randomUUID();
		LikeDto mockLikeDto = new LikeDto();
		given(this.likeService.likeTweet(this.userId, tweetId)).willReturn(CompletableFuture.completedFuture(mockLikeDto));

		MvcResult result = this.mockMvc
			.perform(post(BASE_URL + "/{userId}/tweets/{tweetId}", this.userId, tweetId)
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());

		verify(this.likeService).likeTweet(this.userId, tweetId);
	}

	@Test
	void unlikeTweet() throws Exception {
		UUID tweetId = UUID.randomUUID();
		given(this.likeService.unlikeTweet(this.userId, tweetId)).willReturn(CompletableFuture.completedFuture(null));

		MvcResult result = this.mockMvc
			.perform(delete(BASE_URL + "/{userId}/tweets/{tweetId}", this.userId, tweetId)
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isNoContent())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent());

		verify(this.likeService).unlikeTweet(this.userId, tweetId);
	}

	@Test
	void likeReply() throws Exception {
		UUID replyId = UUID.randomUUID();
		LikeDto mockLikeDto = new LikeDto();
		given(this.likeService.likeReply(this.userId, replyId)).willReturn(CompletableFuture.completedFuture(mockLikeDto));

		MvcResult result = this.mockMvc
			.perform(post(BASE_URL + "/{userId}/replies/{replyId}", this.userId, replyId)
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());

		verify(this.likeService).likeReply(this.userId, replyId);
	}

	@Test
	void unlikeReply() throws Exception {
		UUID replyId = UUID.randomUUID();
		given(this.likeService.unlikeReply(this.userId, replyId)).willReturn(CompletableFuture.completedFuture(null));

		MvcResult result = this.mockMvc
			.perform(delete(BASE_URL + "/{userId}/replies/{replyId}", this.userId, replyId)
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isNoContent())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent());

		verify(this.likeService).unlikeReply(this.userId, replyId);
	}

}
