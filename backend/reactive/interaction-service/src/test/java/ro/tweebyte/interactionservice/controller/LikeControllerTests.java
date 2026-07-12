/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.controller;

import java.util.List;
import java.util.Map;
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

import ro.tweebyte.interactionservice.model.LikeDto;
import ro.tweebyte.interactionservice.service.LikeService;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = LikeController.class,
		excludeAutoConfiguration = { ReactiveSecurityAutoConfiguration.class,
				ReactiveUserDetailsServiceAutoConfiguration.class })
class LikeControllerTests {

	@Autowired
	private WebTestClient webTestClient;

	@org.springframework.boot.test.mock.mockito.MockBean
	private LikeService likeService;

	private final UUID userId = UUID.randomUUID();

	private final UUID tweetId = UUID.randomUUID();

	private final UUID replyId = UUID.randomUUID();

	private final LikeDto likeDto = new LikeDto();

	@Test
	void getUserLikes_Success() {
		given(this.likeService.getUserLikes(this.userId, 0, 10)).willReturn(Flux.just(this.likeDto));

		this.webTestClient.get()
			.uri("/likes/user/{userId}", this.userId)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBodyList(LikeDto.class)
			.hasSize(1);
	}

	@Test
	void getTweetLikes_Success() {
		given(this.likeService.getTweetLikes(this.tweetId, 0, 10)).willReturn(Flux.just(this.likeDto));

		this.webTestClient.get()
			.uri("/likes/tweet/{tweetId}", this.tweetId)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBodyList(LikeDto.class)
			.hasSize(1);
	}

	@Test
	void getTweetLikesCount_Success() {
		given(this.likeService.getTweetLikesCount(this.tweetId)).willReturn(Mono.just(10L));

		this.webTestClient.get()
			.uri("/likes/{tweetId}/count", this.tweetId)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(Long.class)
			.isEqualTo(10L);
	}

	@Test
	void getTweetLikesCounts_Success() {
		UUID tweetTwo = UUID.randomUUID();
		given(this.likeService.getTweetLikesCounts(eq(List.of(this.tweetId, tweetTwo))))
			.willReturn(Mono.just(Map.of(this.tweetId, 4L, tweetTwo, 7L)));

		this.webTestClient.post()
			.uri("/likes/counts")
			.bodyValue(List.of(this.tweetId, tweetTwo))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$." + this.tweetId)
			.isEqualTo(4)
			.jsonPath("$." + tweetTwo)
			.isEqualTo(7);
	}

	@Test
	void likeTweet_Success() {
		given(this.likeService.likeTweet(this.userId, this.tweetId)).willReturn(Mono.just(this.likeDto));

		this.webTestClient.post()
			.uri("/likes/{userId}/tweets/{tweetId}", this.userId, this.tweetId)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(LikeDto.class)
			.isEqualTo(this.likeDto);
	}

	@Test
	void unlikeTweet_Success() {
		given(this.likeService.unlikeTweet(this.userId, this.tweetId)).willReturn(Mono.empty());

		this.webTestClient.delete()
			.uri("/likes/{userId}/tweets/{tweetId}", this.userId, this.tweetId)
			.exchange()
			.expectStatus()
			.isNoContent();
	}

	@Test
	void likeReply_Success() {
		given(this.likeService.likeReply(this.userId, this.replyId)).willReturn(Mono.just(this.likeDto));

		this.webTestClient.post()
			.uri("/likes/{userId}/replies/{replyId}", this.userId, this.replyId)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(LikeDto.class)
			.isEqualTo(this.likeDto);
	}

	@Test
	void unlikeReply_Success() {
		given(this.likeService.unlikeReply(this.userId, this.replyId)).willReturn(Mono.empty());

		this.webTestClient.delete()
			.uri("/likes/{userId}/replies/{replyId}", this.userId, this.replyId)
			.exchange()
			.expectStatus()
			.isNoContent();
	}

}
