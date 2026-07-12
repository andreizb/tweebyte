/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.controller;

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

import ro.tweebyte.interactionservice.model.ReplyCreateRequest;
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.ReplyUpdateRequest;
import ro.tweebyte.interactionservice.service.ReplyService;

import static org.mockito.ArgumentMatchers.any;
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
class ReplyControllerTests {

	@Autowired
	private WebApplicationContext context;

	@MockBean
	private ReplyService replyService;

	private MockMvc mockMvc;

	private final UUID userId = UUID.randomUUID();

	private final UUID replyId = UUID.randomUUID();

	private final UUID tweetId = UUID.randomUUID();

	@BeforeEach
	void setup() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).build();
	}

	@Test
	void createReply() throws Exception {
		ReplyCreateRequest request = new ReplyCreateRequest().setTweetId(this.tweetId).setContent("Test content");
		ReplyDto expectedReply = new ReplyDto().setId(this.replyId).setContent(request.getContent());

		given(this.replyService.createReply(any(ReplyCreateRequest.class)))
			.willReturn(CompletableFuture.completedFuture(expectedReply));

		MvcResult result = this.mockMvc
			.perform(post("/replies/{userId}", this.userId).contentType(MediaType.APPLICATION_JSON)
				.content(new ObjectMapper().writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(request().asyncStarted())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(this.replyId.toString()))
			.andExpect(jsonPath("$.content").value("Test content"));

		verify(this.replyService).createReply(any(ReplyCreateRequest.class));
	}

	@Test
	void updateReply() throws Exception {
		ReplyUpdateRequest request = new ReplyUpdateRequest().setContent("Updated content");

		given(this.replyService.updateReply(any(ReplyUpdateRequest.class)))
			.willReturn(CompletableFuture.completedFuture(null));

		MvcResult result = this.mockMvc
			.perform(put("/replies/{userId}/{replyId}", this.userId, this.replyId).contentType(MediaType.APPLICATION_JSON)
				.content(new ObjectMapper().writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(request().asyncStarted())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());

		verify(this.replyService).updateReply(any(ReplyUpdateRequest.class));
	}

	@Test
	void deleteReply() throws Exception {
		given(this.replyService.deleteReply(this.userId, this.replyId)).willReturn(CompletableFuture.completedFuture(null));

		MvcResult result = this.mockMvc.perform(delete("/replies/{userId}/{replyId}", this.userId, this.replyId))
			.andExpect(status().isOk())
			.andExpect(request().asyncStarted())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());

		verify(this.replyService).deleteReply(this.userId, this.replyId);
	}

	@Test
	void getAllRepliesForTweet() throws Exception {
		List<ReplyDto> replies = List.of(new ReplyDto().setId(this.replyId).setContent("Test reply"));
		given(this.replyService.getRepliesForTweet(this.tweetId, 0, 10))
			.willReturn(CompletableFuture.completedFuture(replies));

		MvcResult result = this.mockMvc.perform(get("/replies/tweet/{tweetId}", this.tweetId))
			.andExpect(request().asyncStarted())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(this.replyId.toString()))
			.andExpect(jsonPath("$[0].content").value("Test reply"));

		verify(this.replyService).getRepliesForTweet(this.tweetId, 0, 10);
	}

	@Test
	void getReplyCountForTweet() throws Exception {
		given(this.replyService.getReplyCountForTweet(this.tweetId)).willReturn(CompletableFuture.completedFuture(5L));

		MvcResult result = this.mockMvc.perform(get("/replies/tweet/{tweetId}/count", this.tweetId))
			.andExpect(request().asyncStarted())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andExpect(jsonPath("$").value(5));

		verify(this.replyService).getReplyCountForTweet(this.tweetId);
	}

	@Test
	void getTopReplyForTweet() throws Exception {
		ReplyDto topReply = new ReplyDto().setId(this.replyId).setContent("Top reply");
		given(this.replyService.getTopReplyForTweet(this.tweetId)).willReturn(CompletableFuture.completedFuture(topReply));

		MvcResult result = this.mockMvc.perform(get("/replies/tweet/{tweetId}/top", this.tweetId))
			.andExpect(request().asyncStarted())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(this.replyId.toString()))
			.andExpect(jsonPath("$.content").value("Top reply"));

		verify(this.replyService).getTopReplyForTweet(this.tweetId);
	}

	@Test
	void getReplyCountsForTweets() throws Exception {
		given(this.replyService.getReplyCountsForTweets(List.of(this.tweetId)))
			.willReturn(CompletableFuture.completedFuture(java.util.Map.of(this.tweetId, 9L)));

		MvcResult result = this.mockMvc
			.perform(post("/replies/tweet/counts").contentType(MediaType.APPLICATION_JSON)
				.content("[\"" + this.tweetId + "\"]"))
			.andExpect(request().asyncStarted())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$['" + this.tweetId + "']").value(9));

		verify(this.replyService).getReplyCountsForTweets(List.of(this.tweetId));
	}

	@Test
	void getTopRepliesForTweets() throws Exception {
		ReplyDto topReply = new ReplyDto().setId(this.replyId).setContent("Batched top");
		given(this.replyService.getTopRepliesForTweets(List.of(this.tweetId)))
			.willReturn(CompletableFuture.completedFuture(java.util.Map.of(this.tweetId, topReply)));

		MvcResult result = this.mockMvc
			.perform(post("/replies/tweet/top").contentType(MediaType.APPLICATION_JSON)
				.content("[\"" + this.tweetId + "\"]"))
			.andExpect(request().asyncStarted())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$['" + this.tweetId + "'].content").value("Batched top"));

		verify(this.replyService).getTopRepliesForTweets(List.of(this.tweetId));
	}

}
