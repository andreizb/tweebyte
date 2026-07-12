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

import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.model.FollowCountsDto;
import ro.tweebyte.interactionservice.model.FollowDto;
import ro.tweebyte.interactionservice.model.ProfileInteractionsDto;
import ro.tweebyte.interactionservice.model.TweetInteractionsEntryDto;
import ro.tweebyte.interactionservice.service.FollowService;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@WebAppConfiguration

class FollowControllerTests {

	private static final String BASE_URL = "/follows";

	private final UUID userId = UUID.randomUUID();

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc;

	@MockBean
	private FollowService followService;

	@BeforeEach
	void setup() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).build();
	}

	@Test
	void getFollowers() throws Exception {
		UUID pathUserId = UUID.randomUUID();
		List<FollowDto> mockFollowers = Collections.emptyList();
		given(this.followService.getFollowers(pathUserId)).willReturn(CompletableFuture.completedFuture(mockFollowers));

		MvcResult result = this.mockMvc.perform(get(BASE_URL + "/{userId}/followers", pathUserId))
			.andExpect(status().isOk())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());

		verify(this.followService).getFollowers(pathUserId);
	}

	@Test
	void getFollowing() throws Exception {
		UUID pathUserId = UUID.randomUUID();
		byte[] mockFollowing = new byte[0];
		given(this.followService.getFollowing(pathUserId)).willReturn(CompletableFuture.completedFuture(mockFollowing));

		MvcResult result = this.mockMvc.perform(get(BASE_URL + "/{userId}/following", pathUserId))
			.andExpect(status().isOk())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andExpect(content().bytes(mockFollowing));

		verify(this.followService).getFollowing(pathUserId);
	}

	@Test
	void getFollowersCount() throws Exception {
		UUID pathUserId = UUID.randomUUID();
		given(this.followService.getFollowersCount(pathUserId)).willReturn(CompletableFuture.completedFuture(10L));

		MvcResult result = this.mockMvc.perform(get(BASE_URL + "/{userId}/followers/count", pathUserId))
			.andExpect(status().isOk())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andExpect(content().string("10"));

		verify(this.followService).getFollowersCount(pathUserId);
	}

	@Test
	void getFollowersIdentifiers() throws Exception {
		UUID pathUserId = UUID.randomUUID();
		List<UUID> mockIdentifiers = List.of(UUID.randomUUID(), UUID.randomUUID());
		given(this.followService.getFollowedIdentifiers(pathUserId))
			.willReturn(CompletableFuture.completedFuture(mockIdentifiers));

		MvcResult result = this.mockMvc.perform(get(BASE_URL + "/{userId}/followers/identifiers", pathUserId))
			.andExpect(status().isOk())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());

		verify(this.followService).getFollowedIdentifiers(pathUserId);
	}

	@Test
	void getFollowingCount() throws Exception {
		UUID pathUserId = UUID.randomUUID();
		given(this.followService.getFollowingCount(pathUserId)).willReturn(CompletableFuture.completedFuture(15L));

		MvcResult result = this.mockMvc.perform(get(BASE_URL + "/{userId}/following/count", pathUserId))
			.andExpect(status().isOk())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andExpect(content().string("15"));

		verify(this.followService).getFollowingCount(pathUserId);
	}

	@Test
	void getFollowCounts() throws Exception {
		UUID pathUserId = UUID.randomUUID();
		given(this.followService.getFollowCounts(pathUserId))
			.willReturn(CompletableFuture.completedFuture(new FollowCountsDto(12L, 5L)));

		MvcResult result = this.mockMvc.perform(get(BASE_URL + "/{userId}/counts", pathUserId))
			.andExpect(status().isOk())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.followers").value(12))
			.andExpect(jsonPath("$.following").value(5));

		verify(this.followService).getFollowCounts(pathUserId);
	}

	@Test
	void getProfileInteractions() throws Exception {
		UUID pathUserId = UUID.randomUUID();
		UUID tweetId = UUID.randomUUID();
		ProfileInteractionsDto payload = new ProfileInteractionsDto(new FollowCountsDto(3L, 4L),
				List.of(new TweetInteractionsEntryDto(tweetId, 1L, 2L, 3L, null)));
		given(this.followService.getProfileInteractions(eq(pathUserId), org.mockito.ArgumentMatchers.anyList()))
			.willReturn(CompletableFuture.completedFuture(payload));

		MvcResult result = this.mockMvc
			.perform(post(BASE_URL + "/{userId}/profile-interactions", pathUserId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("[\"" + tweetId + "\"]"))
			.andExpect(status().isOk())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.follow_counts.followers").value(3))
			.andExpect(jsonPath("$.tweet_interactions[0].tweet_id").value(tweetId.toString()));

		verify(this.followService).getProfileInteractions(eq(pathUserId), org.mockito.ArgumentMatchers.anyList());
	}

	@Test
	void getFollowRequests() throws Exception {
		List<FollowDto> mockRequests = Collections.emptyList();
		given(this.followService.getFollowRequests(this.userId)).willReturn(CompletableFuture.completedFuture(mockRequests));

		MvcResult result = this.mockMvc.perform(get(BASE_URL + "/" + this.userId + "/requests"))
			.andExpect(status().isOk())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());

		verify(this.followService).getFollowRequests(this.userId);
	}

	@Test
	void follow() throws Exception {
		UUID followedId = UUID.randomUUID();
		given(this.followService.follow(this.userId, followedId)).willReturn(CompletableFuture.completedFuture(null));

		MvcResult result = this.mockMvc
			.perform(post(BASE_URL + "/{userId}/{followedId}", this.userId, followedId)
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isNoContent())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent());

		verify(this.followService).follow(this.userId, followedId);
	}

	@Test
	void updateFollowRequest() throws Exception {
		UUID followRequestId = UUID.randomUUID();
		FollowEntity.Status status = FollowEntity.Status.ACCEPTED;
		given(this.followService.updateFollowRequest(this.userId, followRequestId, status))
			.willReturn(CompletableFuture.completedFuture(null));

		MvcResult result = this.mockMvc
			.perform(put(BASE_URL + "/{userId}/{followRequestId}/{status}", this.userId, followRequestId, status)
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isNoContent())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent());

		verify(this.followService).updateFollowRequest(this.userId, followRequestId, status);
	}

	@Test
	void unfollow() throws Exception {
		UUID followedId = UUID.randomUUID();
		given(this.followService.unfollow(this.userId, followedId)).willReturn(CompletableFuture.completedFuture(null));

		MvcResult result = this.mockMvc
			.perform(delete(BASE_URL + "/{userId}/{followedId}", this.userId, followedId)
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isNoContent())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(result)).andExpect(status().isNoContent());

		verify(this.followService).unfollow(this.userId, followedId);
	}

}
