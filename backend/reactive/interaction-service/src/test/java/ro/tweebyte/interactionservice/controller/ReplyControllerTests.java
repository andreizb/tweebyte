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
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import ro.tweebyte.interactionservice.model.ReplyCreateRequest;
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.ReplyUpdateRequest;
import ro.tweebyte.interactionservice.service.ReplyService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = ReplyController.class,
		excludeAutoConfiguration = { ReactiveSecurityAutoConfiguration.class,
				ReactiveUserDetailsServiceAutoConfiguration.class })
class ReplyControllerTests {

	@Autowired
	private WebTestClient webTestClient;

	@org.springframework.boot.test.mock.mockito.MockBean
	private ReplyService replyService;

	private final UUID userId = UUID.randomUUID();

	private final UUID tweetId = UUID.randomUUID();

	private final UUID replyId = UUID.randomUUID();

	private final ReplyDto replyDto = new ReplyDto();

	private final ReplyCreateRequest createRequest = new ReplyCreateRequest();

	private final ReplyUpdateRequest updateRequest = new ReplyUpdateRequest();

	@Test
	void createReply_Success() {
		given(this.replyService.createReply(any(ReplyCreateRequest.class))).willReturn(Mono.just(this.replyDto));

		this.webTestClient.post()
			.uri("/replies/{userId}", this.userId)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(this.createRequest.setTweetId(this.tweetId))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(ReplyDto.class)
			.isEqualTo(this.replyDto);
	}

	@Test
	void updateReply_Success() {
		given(this.replyService.updateReply(any(ReplyUpdateRequest.class))).willReturn(Mono.empty());

		this.webTestClient.put()
			.uri("/replies/{userId}/{replyId}", this.userId, this.replyId)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(this.updateRequest)
			.exchange()
			.expectStatus()
			.isOk();
	}

	@Test
	void deleteReply_Success() {
		given(this.replyService.deleteReply(this.userId, this.replyId)).willReturn(Mono.empty());

		this.webTestClient.delete()
			.uri("/replies/{userId}/{replyId}", this.userId, this.replyId)
			.exchange()
			.expectStatus()
			.isOk();
	}

	@Test
	void getAllRepliesForTweet_Success() {
		given(this.replyService.getRepliesForTweet(this.tweetId, 0, 10)).willReturn(Flux.just(this.replyDto));

		this.webTestClient.get()
			.uri("/replies/tweet/{tweetId}", this.tweetId)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBodyList(ReplyDto.class)
			.hasSize(1)
			.contains(this.replyDto);
	}

	@Test
	void getReplyCountForTweet_Success() {
		given(this.replyService.getReplyCountForTweet(this.tweetId)).willReturn(Mono.just(5L));

		this.webTestClient.get()
			.uri("/replies/tweet/{tweetId}/count", this.tweetId)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(Long.class)
			.isEqualTo(5L);
	}

	@Test
	void getTopReplyForTweet_Success() {
		given(this.replyService.getTopReplyForTweet(this.tweetId)).willReturn(Mono.just(this.replyDto));

		this.webTestClient.get()
			.uri("/replies/tweet/{tweetId}/top", this.tweetId)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(ReplyDto.class)
			.isEqualTo(this.replyDto);
	}

	@Test
	void getReplyCountsForTweets_Success() {
		UUID tweetTwo = UUID.randomUUID();
		given(this.replyService.getReplyCountsForTweets(eq(List.of(this.tweetId, tweetTwo))))
			.willReturn(Mono.just(Map.of(this.tweetId, 2L, tweetTwo, 9L)));

		this.webTestClient.post()
			.uri("/replies/tweet/counts")
			.bodyValue(List.of(this.tweetId, tweetTwo))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$." + this.tweetId)
			.isEqualTo(2)
			.jsonPath("$." + tweetTwo)
			.isEqualTo(9);
	}

	@Test
	void getTopRepliesForTweets_Success() {
		UUID topReplyId = UUID.randomUUID();
		ReplyDto top = new ReplyDto();
		top.setId(topReplyId);
		given(this.replyService.getTopRepliesForTweets(eq(List.of(this.tweetId))))
			.willReturn(Mono.just(Map.of(this.tweetId, top)));

		this.webTestClient.post()
			.uri("/replies/tweet/top")
			.bodyValue(List.of(this.tweetId))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$." + this.tweetId + ".id")
			.isEqualTo(topReplyId.toString());
	}

}
