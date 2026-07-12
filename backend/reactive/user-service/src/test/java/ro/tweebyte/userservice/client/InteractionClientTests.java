/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.client;

import java.net.URI;
import java.util.List;
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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.userservice.exception.FollowRetrievingException;
import ro.tweebyte.userservice.model.FollowCountsDto;
import ro.tweebyte.userservice.model.ProfileInteractionsDto;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InteractionClientTests {

	@InjectMocks
	private InteractionClient interactionClient;

	@Mock
	private WebClient.Builder webClientBuilderMock;

	@Mock
	private WebClient webClientMock;

	@Mock
	private WebClient.RequestHeadersUriSpec requestHeadersUriSpecMock;

	@Mock
	private WebClient.RequestHeadersSpec requestHeadersSpecMock;

	@Mock
	private WebClient.RequestBodyUriSpec requestBodyUriSpecMock;

	@Mock
	private WebClient.RequestBodySpec requestBodySpecMock;

	@Mock
	private WebClient.ResponseSpec responseSpecMock;

	@BeforeEach
	void setUp() {
		given(this.webClientBuilderMock.baseUrl(any())).willReturn(this.webClientBuilderMock);
		given(this.webClientBuilderMock.build()).willReturn(this.webClientMock);
		this.interactionClient.init();
		// Request-chain stubs are lenient: the fallback tests call the bean methods
		// directly without touching the WebClient.
		lenient().when(this.webClientMock.get()).thenReturn(this.requestHeadersUriSpecMock);
		lenient().when(this.requestHeadersUriSpecMock.uri((Function<UriBuilder, URI>) any()))
			.thenReturn(this.requestHeadersSpecMock);
		lenient().when(this.requestHeadersSpecMock.retrieve()).thenReturn(this.responseSpecMock);
	}

	@Test
	void testGetReferencedMediaIds() {
		UUID mediaId = UUID.randomUUID();
		given(this.requestHeadersUriSpecMock.uri("/media/referenced")).willReturn(this.requestHeadersSpecMock);
		given(this.responseSpecMock.bodyToFlux(UUID.class)).willReturn(Flux.just(mediaId));

		StepVerifier.create(this.interactionClient.getReferencedMediaIds()).expectNext(mediaId).verifyComplete();
	}

	@Test
	@SuppressWarnings("unchecked")
	void testGetProfileInteractionsPostsToCombinedEndpoint() {
		UUID userId = UUID.randomUUID();
		UUID tweetId = UUID.randomUUID();
		ProfileInteractionsDto expected = new ProfileInteractionsDto(new FollowCountsDto(20L, 10L), List.of());

		given(this.webClientMock.post()).willReturn(this.requestBodyUriSpecMock);
		given(this.requestBodyUriSpecMock.uri((Function<UriBuilder, URI>) any())).willReturn(this.requestBodySpecMock);
		given(this.requestBodySpecMock.bodyValue(any())).willReturn(this.requestHeadersSpecMock);
		given(this.requestHeadersSpecMock.retrieve()).willReturn(this.responseSpecMock);
		given(this.responseSpecMock.bodyToMono(ProfileInteractionsDto.class)).willReturn(Mono.just(expected));

		StepVerifier.create(this.interactionClient.getProfileInteractions(userId, List.of(tweetId)))
			.expectNext(expected)
			.verifyComplete();

		// POST /follows/{userId}/profile-interactions with the tweet-id list as the body.
		ArgumentCaptor<Function<UriBuilder, URI>> uriCaptor = ArgumentCaptor.forClass(Function.class);
		verify(this.requestBodyUriSpecMock).uri(uriCaptor.capture());
		URI uri = uriCaptor.getValue().apply(new org.springframework.web.util.DefaultUriBuilderFactory().builder());
		org.assertj.core.api.Assertions.assertThat(uri.getPath()).isEqualTo("/follows/" + userId + "/profile-interactions");
		verify(this.requestBodySpecMock).bodyValue(List.of(tweetId));
	}

	@Test
	void testGetProfileInteractionsWrapsErrorAsFollowRetrieving() {
		UUID userId = UUID.randomUUID();

		given(this.webClientMock.post()).willReturn(this.requestBodyUriSpecMock);
		given(this.requestBodyUriSpecMock.uri(org.mockito.ArgumentMatchers.<Function<UriBuilder, URI>>any()))
			.willReturn(this.requestBodySpecMock);
		given(this.requestBodySpecMock.bodyValue(any())).willReturn(this.requestHeadersSpecMock);
		given(this.requestHeadersSpecMock.retrieve()).willReturn(this.responseSpecMock);
		given(this.responseSpecMock.bodyToMono(ProfileInteractionsDto.class))
			.willReturn(Mono.error(new RuntimeException()));

		StepVerifier.create(this.interactionClient.getProfileInteractions(userId, List.of()))
			.expectError(FollowRetrievingException.class)
			.verify();
	}

	// --- resilience fallbacks (master-off on the benchmark path, but covered) ---

	@Test
	void getProfileInteractionsFallbackPropagatesError() {
		RuntimeException boom = new RuntimeException("circuit open");

		StepVerifier.create(this.interactionClient.getProfileInteractionsFallback(UUID.randomUUID(), List.of(), boom))
			.expectErrorMatches(t -> t == boom)
			.verify();
	}

	@Test
	void getReferencedMediaIdsFallbackPropagatesError() {
		RuntimeException boom = new RuntimeException("bulkhead full");

		StepVerifier.create(this.interactionClient.getReferencedMediaIdsFallback(boom))
			.expectErrorMatches(t -> t == boom)
			.verify();
	}

}
