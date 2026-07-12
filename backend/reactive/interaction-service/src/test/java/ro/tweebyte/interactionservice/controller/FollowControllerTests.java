/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.controller;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import ro.tweebyte.interactionservice.model.FollowCountsDto;
import ro.tweebyte.interactionservice.model.FollowDto;
import ro.tweebyte.interactionservice.model.ProfileInteractionsDto;
import ro.tweebyte.interactionservice.model.Status;
import ro.tweebyte.interactionservice.model.TweetInteractionsEntryDto;
import ro.tweebyte.interactionservice.service.FollowService;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = FollowController.class,
		excludeAutoConfiguration = { ReactiveSecurityAutoConfiguration.class,
				ReactiveUserDetailsServiceAutoConfiguration.class })
class FollowControllerTests {

	@Autowired
	private WebTestClient webTestClient;

	@org.springframework.boot.test.mock.mockito.MockBean
	private FollowService followService;

	private final UUID userId = UUID.randomUUID();

	private final UUID followedId = UUID.randomUUID();

	private final UUID followRequestId = UUID.randomUUID();

	private final FollowDto followDto = new FollowDto();

	@Test
	void getFollowers_Success() {
		given(this.followService.getFollowers(this.userId)).willReturn(Flux.just(this.followDto));

		this.webTestClient.get()
			.uri("/follows/{userId}/followers", this.userId)
			.header("Authorization", "Bearer test-token")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBodyList(FollowDto.class)
			.hasSize(1);
	}

	@Test
	void getFollowing_Success() throws Exception {
		byte[] response = new com.fasterxml.jackson.databind.ObjectMapper()
			.writeValueAsBytes(List.of(this.followDto));
		given(this.followService.getFollowing(this.userId)).willReturn(Mono.just(response));

		this.webTestClient.get()
			.uri("/follows/{userId}/following", this.userId)
			.header("Authorization", "Bearer test-token")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBodyList(FollowDto.class)
			.hasSize(1);
	}

	@Test
	void getFollowersCount_Success() {
		given(this.followService.getFollowersCount(this.userId)).willReturn(Mono.just(10L));

		this.webTestClient.get()
			.uri("/follows/{userId}/followers/count", this.userId)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(Long.class)
			.isEqualTo(10L);
	}

	@Test
	void getFollowersIdentifiers_Success() {
		UUID followerId = UUID.randomUUID();
		given(this.followService.getFollowedIdentifiers(this.userId)).willReturn(Flux.just(followerId));

		this.webTestClient.get()
			.uri("/follows/{userId}/followers/identifiers", this.userId)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBodyList(UUID.class)
			.hasSize(1)
			.contains(followerId);
	}

	@Test
	void getFollowingCount_Success() {
		given(this.followService.getFollowingCount(this.userId)).willReturn(Mono.just(5L));

		this.webTestClient.get()
			.uri("/follows/{userId}/following/count", this.userId)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(Long.class)
			.isEqualTo(5L);
	}

	@Test
	void getFollowCounts_Success() {
		given(this.followService.getFollowCounts(this.userId)).willReturn(Mono.just(new FollowCountsDto(20L, 10L)));

		this.webTestClient.get()
			.uri("/follows/{userId}/counts", this.userId)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.followers")
			.isEqualTo(20)
			.jsonPath("$.following")
			.isEqualTo(10);
	}

	@Test
	void getProfileInteractions_Success() {
		UUID tweetId = UUID.randomUUID();
		ProfileInteractionsDto dto = new ProfileInteractionsDto(new FollowCountsDto(20L, 10L),
				List.of(new TweetInteractionsEntryDto(tweetId, 3L, 2L, 1L, null)));
		given(this.followService.getProfileInteractions(eq(this.userId), eq(List.of(tweetId)))).willReturn(Mono.just(dto));

		this.webTestClient.post()
			.uri("/follows/{userId}/profile-interactions", this.userId)
			.bodyValue(List.of(tweetId))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.follow_counts.followers")
			.isEqualTo(20)
			.jsonPath("$.follow_counts.following")
			.isEqualTo(10)
			.jsonPath("$.tweet_interactions[0].tweet_id")
			.isEqualTo(tweetId.toString())
			.jsonPath("$.tweet_interactions[0].likes")
			.isEqualTo(3);
	}

	@Test
	void getFollowRequests_Success() {
		given(this.followService.getFollowRequests(this.userId)).willReturn(Flux.just(this.followDto));

		this.webTestClient.get()
			.uri("/follows/{userId}/requests", this.userId)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBodyList(FollowDto.class)
			.hasSize(1);
	}

	@Test
	void follow_Success() {
		given(this.followService.follow(this.userId, this.followedId)).willReturn(Mono.empty());

		this.webTestClient.post()
			.uri("/follows/{userId}/{followedId}", this.userId, this.followedId)
			.exchange()
			.expectStatus()
			.isNoContent();
	}

	@Test
	void updateFollowRequest_Success() {
		given(this.followService.updateFollowRequest(this.userId, this.followRequestId, Status.ACCEPTED))
			.willReturn(Mono.empty());

		this.webTestClient.put()
			.uri("/follows/{userId}/{followRequestId}/{status}", this.userId, this.followRequestId, Status.ACCEPTED)
			.exchange()
			.expectStatus()
			.isNoContent();
	}

	@Test
	void unfollow_Success() {
		given(this.followService.unfollow(this.userId, this.followedId)).willReturn(Mono.empty());

		this.webTestClient.delete()
			.uri("/follows/{userId}/{followedId}", this.userId, this.followedId)
			.exchange()
			.expectStatus()
			.isNoContent();
	}

}
