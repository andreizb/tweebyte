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

import ro.tweebyte.interactionservice.model.RetweetCreateRequest;
import ro.tweebyte.interactionservice.model.RetweetDto;
import ro.tweebyte.interactionservice.model.RetweetUpdateRequest;
import ro.tweebyte.interactionservice.service.RetweetService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = RetweetController.class,
		excludeAutoConfiguration = { ReactiveSecurityAutoConfiguration.class,
				ReactiveUserDetailsServiceAutoConfiguration.class })
class RetweetControllerTests {

	@Autowired
	private WebTestClient webTestClient;

	@org.springframework.boot.test.mock.mockito.MockBean
	private RetweetService retweetService;

	private final UUID userId = UUID.randomUUID();

	private final UUID tweetId = UUID.randomUUID();

	private final UUID retweetId = UUID.randomUUID();

	private final RetweetDto retweetDto = new RetweetDto();

	private final RetweetCreateRequest createRequest = new RetweetCreateRequest();

	private final RetweetUpdateRequest updateRequest = new RetweetUpdateRequest();

	@Test
	void createRetweet_Success() {
		given(this.retweetService.createRetweet(any(RetweetCreateRequest.class)))
			.willReturn(Mono.just(this.retweetDto));

		this.webTestClient.post()
			.uri("/retweets/{userId}", this.userId)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(this.createRequest.setOriginalTweetId(this.tweetId))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(RetweetDto.class)
			.isEqualTo(this.retweetDto);
	}

	@Test
	void updateRetweet_Success() {
		given(this.retweetService.updateRetweet(any(RetweetUpdateRequest.class))).willReturn(Mono.empty());

		this.webTestClient.put()
			.uri("/retweets/{userId}/{retweetId}", this.userId, this.retweetId)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(this.updateRequest)
			.exchange()
			.expectStatus()
			.isOk();
	}

	@Test
	void deleteRetweet_Success() {
		given(this.retweetService.deleteRetweet(this.retweetId, this.userId)).willReturn(Mono.empty());

		this.webTestClient.delete()
			.uri("/retweets/{userId}/{retweetId}", this.userId, this.retweetId)
			.exchange()
			.expectStatus()
			.isOk();
	}

	@Test
	void getAllRetweetsByUser_Success() {
		given(this.retweetService.getRetweetsByUser(this.userId, 0, 10)).willReturn(Flux.just(this.retweetDto));

		this.webTestClient.get()
			.uri("/retweets/user/{userId}", this.userId)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBodyList(RetweetDto.class)
			.hasSize(1)
			.contains(this.retweetDto);
	}

	@Test
	void getAllRetweetsOfTweet_Success() {
		given(this.retweetService.getRetweetsOfTweet(this.tweetId, 0, 10)).willReturn(Flux.just(this.retweetDto));

		this.webTestClient.get()
			.uri("/retweets/tweet/{tweetId}", this.tweetId)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBodyList(RetweetDto.class)
			.hasSize(1)
			.contains(this.retweetDto);
	}

	@Test
	void getRetweetCountOfTweet_Success() {
		given(this.retweetService.getRetweetCountOfTweet(this.tweetId)).willReturn(Mono.just(10L));

		this.webTestClient.get()
			.uri("/retweets/tweet/{tweetId}/count", this.tweetId)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(Long.class)
			.isEqualTo(10L);
	}

	@Test
	void getRetweetCountsForTweets_Success() {
		UUID tweetTwo = UUID.randomUUID();
		given(this.retweetService.getRetweetCountsForTweets(eq(List.of(this.tweetId, tweetTwo))))
			.willReturn(Mono.just(Map.of(this.tweetId, 3L, tweetTwo, 8L)));

		this.webTestClient.post()
			.uri("/retweets/tweet/counts")
			.bodyValue(List.of(this.tweetId, tweetTwo))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$." + this.tweetId)
			.isEqualTo(3)
			.jsonPath("$." + tweetTwo)
			.isEqualTo(8);
	}

}
