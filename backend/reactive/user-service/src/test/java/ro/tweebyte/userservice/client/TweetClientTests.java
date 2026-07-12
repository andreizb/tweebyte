/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.client;

import java.net.URI;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import ro.tweebyte.userservice.model.TweetDto;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TweetClientTests {

	@InjectMocks
	private TweetClient tweetClient;

	@Mock
	private WebClient.Builder webClientBuilderMock;

	@Mock
	private WebClient webClientMock;

	@Mock
	private WebClient.RequestHeadersUriSpec requestHeadersUriSpecMock;

	@Mock
	private WebClient.RequestHeadersSpec requestHeadersSpecMock;

	@Mock
	private WebClient.ResponseSpec responseSpecMock;

	@BeforeEach
	void setUp() {
		given(this.webClientBuilderMock.baseUrl(any())).willReturn(this.webClientBuilderMock);
		given(this.webClientBuilderMock.build()).willReturn(this.webClientMock);
		this.tweetClient.init();
		// Request-chain stubs are lenient: the fallback tests call the bean methods
		// directly without touching the WebClient.
		lenient().when(this.webClientMock.get()).thenReturn(this.requestHeadersUriSpecMock);
		lenient().when(this.requestHeadersUriSpecMock.uri((Function<UriBuilder, URI>) any()))
			.thenReturn(this.requestHeadersSpecMock);
		lenient().when(this.requestHeadersSpecMock.retrieve()).thenReturn(this.responseSpecMock);
	}

	@Test
	void testGetUserProfileTweetsRequestsUnenriched() {
		UUID userId = UUID.randomUUID();
		TweetDto tweetDto = new TweetDto();
		given(this.responseSpecMock.bodyToFlux(TweetDto.class)).willReturn(Flux.just(tweetDto));

		Flux<TweetDto> result = this.tweetClient.getUserProfileTweets(userId);

		StepVerifier.create(result).expectNext(tweetDto).verifyComplete();

		// The profile read must request the page UN-enriched: /tweets/user/{id}?enrich=false.
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Function<UriBuilder, URI>> uriCaptor = ArgumentCaptor.forClass(Function.class);
		verify(this.requestHeadersUriSpecMock).uri(uriCaptor.capture());
		URI uri = uriCaptor.getValue()
			.apply(new org.springframework.web.util.DefaultUriBuilderFactory().builder());
		org.assertj.core.api.Assertions.assertThat(uri.getPath()).isEqualTo("/tweets/user/" + userId);
		org.assertj.core.api.Assertions.assertThat(uri.getQuery()).isEqualTo("enrich=false");
	}

	@Test
	void testGetReferencedMediaIds() {
		UUID mediaId = UUID.randomUUID();
		given(this.requestHeadersUriSpecMock.uri("/tweets/media/referenced")).willReturn(this.requestHeadersSpecMock);
		given(this.responseSpecMock.bodyToFlux(UUID.class)).willReturn(Flux.just(mediaId));

		StepVerifier.create(this.tweetClient.getReferencedMediaIds()).expectNext(mediaId).verifyComplete();
	}

	// --- resilience fallbacks (master-off on the benchmark path, but covered) ---

	@Test
	void getUserProfileTweetsFallbackPropagatesError() {
		RuntimeException boom = new RuntimeException("circuit open");

		StepVerifier.create(this.tweetClient.getUserProfileTweetsFallback(UUID.randomUUID(), boom))
			.expectErrorMatches(t -> t == boom)
			.verify();
	}

	@Test
	void getReferencedMediaIdsFallbackPropagatesError() {
		RuntimeException boom = new RuntimeException("bulkhead full");

		StepVerifier.create(this.tweetClient.getReferencedMediaIdsFallback(boom)).expectErrorMatches(t -> t == boom).verify();
	}

}
