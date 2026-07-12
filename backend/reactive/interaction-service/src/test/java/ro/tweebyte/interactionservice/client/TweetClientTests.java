/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.client;

import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.interactionservice.exception.InteractionException;
import ro.tweebyte.interactionservice.exception.TweetNotFoundException;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.TweetSummaryDto;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TweetClientTests {

	@InjectMocks
	private TweetClient tweetClient;

	@Mock
	private WebClient.Builder webClientBuilder;

	@Mock
	private WebClient webClient;

	@Mock
	private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

	@Mock
	private WebClient.RequestHeadersSpec requestHeadersSpec;

	@Mock
	private WebClient.ResponseSpec responseSpec;

	@BeforeEach
	void setUp() {
		given(this.webClientBuilder.baseUrl(any())).willReturn(this.webClientBuilder);
		given(this.webClientBuilder.build()).willReturn(this.webClient);
		this.tweetClient.init();
		given(this.webClient.get()).willReturn(this.requestHeadersUriSpec);
		given(this.requestHeadersSpec.retrieve()).willReturn(this.responseSpec);
	}

	@Test
	void getTweetSummary_Success() {
		UUID tweetId = UUID.randomUUID();
		TweetDto expectedTweet = new TweetDto();
		expectedTweet.setId(tweetId);

		given(this.webClient.get()).willReturn(this.requestHeadersUriSpec);
		given(this.requestHeadersUriSpec.uri(any(Function.class))).willReturn(this.requestHeadersSpec);
		given(this.requestHeadersSpec.retrieve()).willReturn(this.responseSpec);
		given(this.responseSpec.bodyToMono(TweetDto.class)).willReturn(Mono.just(expectedTweet));

		StepVerifier.create(this.tweetClient.getTweetSummary(tweetId)).expectNext(expectedTweet).verifyComplete();

		verify(this.webClient).get();
		verify(this.requestHeadersUriSpec).uri(any(Function.class));
		verify(this.responseSpec).bodyToMono(TweetDto.class);
	}

	@Test
	void getTweetSummary_NotFound() {
		UUID tweetId = UUID.randomUUID();
		given(this.webClient.get()).willReturn(this.requestHeadersUriSpec);
		given(this.requestHeadersUriSpec.uri(any(Function.class))).willReturn(this.requestHeadersSpec);
		given(this.requestHeadersSpec.retrieve()).willReturn(this.responseSpec);
		given(this.responseSpec.bodyToMono(TweetDto.class)).willReturn(Mono
			.error(new WebClientResponseException(HttpStatus.NOT_FOUND.value(), "Not Found", null, null, null)));

		StepVerifier.create(this.tweetClient.getTweetSummary(tweetId))
			.expectError(TweetNotFoundException.class)
			.verify();

		verify(this.webClient).get();
		verify(this.requestHeadersUriSpec).uri(any(Function.class));
	}

	@Test
	void getUserTweetsSummary_Success() {
		UUID userId = UUID.randomUUID();
		TweetSummaryDto tweet1 = new TweetSummaryDto();
		tweet1.setId(UUID.randomUUID());
		TweetSummaryDto tweet2 = new TweetSummaryDto();
		tweet2.setId(UUID.randomUUID());

		given(this.webClient.get()).willReturn(this.requestHeadersUriSpec);
		given(this.requestHeadersUriSpec.uri(any(Function.class))).willReturn(this.requestHeadersSpec);
		given(this.requestHeadersSpec.retrieve()).willReturn(this.responseSpec);
		given(this.responseSpec.bodyToFlux(TweetSummaryDto.class)).willReturn(Flux.just(tweet1, tweet2));

		StepVerifier.create(this.tweetClient.getUserTweetsSummary(userId)).expectNext(tweet1, tweet2).verifyComplete();

		verify(this.webClient).get();
		verify(this.requestHeadersUriSpec).uri(any(Function.class));
		verify(this.responseSpec).bodyToFlux(TweetSummaryDto.class);
	}

	@Test
	void getUserTweetsSummary_NotFound() {
		UUID userId = UUID.randomUUID();

		given(this.webClient.get()).willReturn(this.requestHeadersUriSpec);
		given(this.requestHeadersUriSpec.uri(any(Function.class))).willReturn(this.requestHeadersSpec);
		given(this.requestHeadersSpec.retrieve()).willReturn(this.responseSpec);
		given(this.responseSpec.bodyToFlux(TweetSummaryDto.class)).willReturn(Flux
			.error(new WebClientResponseException(HttpStatus.NOT_FOUND.value(), "Not Found", null, null, null)));

		StepVerifier.create(this.tweetClient.getUserTweetsSummary(userId))
			.expectError(TweetNotFoundException.class)
			.verify();

		verify(this.webClient).get();
		verify(this.requestHeadersUriSpec).uri(any(Function.class));
	}

	@Test
	void getPopularHashtags_Success() {
		TweetDto.HashtagDto hashtag1 = new TweetDto.HashtagDto();
		hashtag1.setText("#test1");
		TweetDto.HashtagDto hashtag2 = new TweetDto.HashtagDto();
		hashtag2.setText("#test2");

		given(this.webClient.get()).willReturn(this.requestHeadersUriSpec);
		given(this.requestHeadersUriSpec.uri(any(Function.class))).willReturn(this.requestHeadersSpec);
		given(this.requestHeadersSpec.retrieve()).willReturn(this.responseSpec);
		given(this.responseSpec.bodyToFlux(TweetDto.HashtagDto.class)).willReturn(Flux.just(hashtag1, hashtag2));

		StepVerifier.create(this.tweetClient.getPopularHashtags()).expectNext(hashtag1, hashtag2).verifyComplete();

		verify(this.webClient).get();
		verify(this.requestHeadersUriSpec).uri(any(Function.class));
		verify(this.responseSpec).bodyToFlux(TweetDto.HashtagDto.class);
	}

	@Test
	void getPopularHashtags_Failure() {
		given(this.webClient.get()).willReturn(this.requestHeadersUriSpec);
		given(this.requestHeadersUriSpec.uri(any(Function.class))).willReturn(this.requestHeadersSpec);
		given(this.requestHeadersSpec.retrieve()).willReturn(this.responseSpec);
		given(this.responseSpec.bodyToFlux(TweetDto.HashtagDto.class))
			.willReturn(Flux.error(new RuntimeException("Service unavailable")));

		StepVerifier.create(this.tweetClient.getPopularHashtags()).expectError(InteractionException.class).verify();

		verify(this.webClient).get();
		verify(this.requestHeadersUriSpec).uri(any(Function.class));
	}

}
