/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.controller;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import ro.tweebyte.tweetservice.model.HashtagDto;
import ro.tweebyte.tweetservice.model.TweetCreationRequest;
import ro.tweebyte.tweetservice.model.TweetDto;
import ro.tweebyte.tweetservice.model.TweetSummaryDto;
import ro.tweebyte.tweetservice.model.TweetUpdateRequest;
import ro.tweebyte.tweetservice.service.HashtagService;
import ro.tweebyte.tweetservice.service.TweetService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = TweetController.class,
		excludeAutoConfiguration = { ReactiveSecurityAutoConfiguration.class,
				ReactiveUserDetailsServiceAutoConfiguration.class })
class TweetControllerTests {

	@Autowired
	private WebTestClient webTestClient;

	@MockBean
	private TweetService tweetService;

	@MockBean
	private HashtagService hashtagService;

	private final TweetDto tweetDto = new TweetDto();

	private final TweetSummaryDto summaryDto = new TweetSummaryDto();

	private final HashtagDto hashtagDto = new HashtagDto();

	private final UUID tweetId = UUID.randomUUID();

	private final UUID userId = UUID.randomUUID();

	private final String searchTerm = "searchTerm";

	@BeforeEach
	void setUp() {

		given(this.tweetService.getUserFeed(eq(this.userId), anyInt(), anyInt())).willReturn(Flux.just(this.tweetDto));
		given(this.tweetService.getTweet(this.tweetId)).willReturn(Mono.just(this.tweetDto));
		given(this.tweetService.searchTweets(eq(this.searchTerm), anyInt(), anyInt()))
			.willReturn(Flux.just(this.tweetDto));
		given(this.tweetService.searchTweetsByHashtag(eq(this.searchTerm), anyInt(), anyInt()))
			.willReturn(Flux.just(this.tweetDto));
		given(this.hashtagService.computePopularHashtags()).willReturn(Flux.just(this.hashtagDto));
		given(this.tweetService.getUserTweets(eq(this.userId), anyInt(), anyInt(), anyBoolean()))
			.willReturn(Flux.just(this.tweetDto));
		given(this.tweetService.getTweetSummary(this.tweetId)).willReturn(Mono.just(this.tweetDto));
		given(this.tweetService.getUserTweetsSummary(this.userId)).willReturn(Flux.just(this.summaryDto));
		given(this.tweetService.createTweet(any(TweetCreationRequest.class))).willReturn(Mono.just(this.tweetDto));
		given(this.tweetService.updateTweet(any(TweetUpdateRequest.class))).willReturn(Mono.empty());
		given(this.tweetService.deleteTweet(this.userId, this.tweetId)).willReturn(Mono.empty());
	}

	@Test
	void searchTweets() {
		this.webTestClient.get()
			.uri("/tweets/search/{searchTerm}", this.searchTerm)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBodyList(TweetDto.class)
			.hasSize(1);
	}

	@Test
	void searchTweetsByHashtag() {
		this.webTestClient.get()
			.uri("/tweets/search/hashtag/{searchTerm}", this.searchTerm)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBodyList(TweetDto.class)
			.hasSize(1);
	}

	@Test
	void computePopularHashtags() {
		this.webTestClient.get()
			.uri("/tweets/hashtag/popular")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBodyList(HashtagDto.class)
			.hasSize(1);
	}

	@Test
	void getTweetSummary() {
		this.webTestClient.get()
			.uri("/tweets/{tweetId}/summary", this.tweetId)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(TweetDto.class)
			.isEqualTo(this.tweetDto);
	}

	@Test
	void getUserTweetsSummary() {
		this.webTestClient.get()
			.uri("/tweets/user/{userId}/summary", this.userId)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBodyList(TweetSummaryDto.class)
			.hasSize(1);
	}

	@Test
	void createTweet() {
		TweetCreationRequest request = new TweetCreationRequest();
		request.setContent("asdfffffffffffffffffffffffffffffffffffffff");

		this.webTestClient.post()
			.uri("/tweets/{userId}", this.userId)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(request)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(TweetDto.class)
			.isEqualTo(this.tweetDto);
	}

	@Test
	void updateTweet() {
		TweetUpdateRequest request = new TweetUpdateRequest();
		request.setContent("Updated test tweet content");
		this.webTestClient.put()
			.uri("/tweets/{userId}/{tweetId}", this.userId, this.tweetId)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(request)
			.exchange()
			.expectStatus()
			.isOk();
	}

	@Test
	void deleteTweet() {
		this.webTestClient.delete()
			.uri("/tweets/{userId}/{tweetId}", this.userId, this.tweetId)
			.exchange()
			.expectStatus()
			.isNoContent();
	}

	@Test
	void getUserTweets() {
		// No ?enrich param → the endpoint must default to enrich=true, the tweets-get path.
		given(this.tweetService.getUserTweets(eq(this.userId), anyInt(), anyInt(), eq(true)))
			.willReturn(Flux.just(this.tweetDto));

		this.webTestClient.get()
			.uri("/tweets/user/{userId}", this.userId)
			.header("Authorization", "Bearer test-token")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBodyList(TweetDto.class)
			.hasSize(1)
			.contains(this.tweetDto);

		org.mockito.Mockito.verify(this.tweetService).getUserTweets(eq(this.userId), anyInt(), anyInt(), eq(true));
	}

	@Test
	void getUserTweetsUnenriched() {
		// ?enrich=false (the user-profile read's opt-out) must thread through to the service.
		given(this.tweetService.getUserTweets(eq(this.userId), anyInt(), anyInt(), eq(false)))
			.willReturn(Flux.just(this.tweetDto));

		this.webTestClient.get()
			.uri(uriBuilder -> uriBuilder.path("/tweets/user/{userId}").queryParam("enrich", "false").build(this.userId))
			.header("Authorization", "Bearer test-token")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBodyList(TweetDto.class)
			.hasSize(1)
			.contains(this.tweetDto);

		org.mockito.Mockito.verify(this.tweetService).getUserTweets(eq(this.userId), anyInt(), anyInt(), eq(false));
	}

	@Test
	void getFeed() {
		given(this.tweetService.getUserFeed(eq(this.userId), anyInt(), anyInt())).willReturn(Flux.just(this.tweetDto));

		this.webTestClient.get()
			.uri("/tweets/{userId}/feed", this.userId)
			.header("Authorization", "Bearer test-token")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBodyList(TweetDto.class)
			.hasSize(1)
			.contains(this.tweetDto);
	}

	@Test
	void getTweet() {
		given(this.tweetService.getTweet(this.tweetId)).willReturn(Mono.just(this.tweetDto));

		this.webTestClient.get()
			.uri("/tweets/{tweetId}", this.tweetId)
			.header("Authorization", "Bearer test-token")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(TweetDto.class)
			.isEqualTo(this.tweetDto);
	}

}
