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
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.TweetSummaryDto;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

/**
 * Branch coverage — exercises the FALSE branch of the NOT_FOUND check (lines 38 and 51 of
 * TweetClient) where a non-404 WebClientResponseException is wrapped in an
 * InteractionException instead of TweetNotFoundException.
 */
@ExtendWith(MockitoExtension.class)
class TweetClientBranchTests {

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
		// The fallback tests invoke the fallback methods directly and never touch the
		// WebClient call chain, so these shared stubs are lenient.
		lenient().when(this.webClient.get()).thenReturn(this.requestHeadersUriSpec);
		lenient().when(this.requestHeadersUriSpec.uri(any(Function.class))).thenReturn(this.requestHeadersSpec);
		lenient().when(this.requestHeadersSpec.retrieve()).thenReturn(this.responseSpec);
	}

	@Test
	void getTweetSummary_NonNotFound_WrapsAsInteractionException() {
		UUID tweetId = UUID.randomUUID();
		WebClientResponseException server = WebClientResponseException.create(HttpStatus.INTERNAL_SERVER_ERROR.value(),
				"boom", null, null, null);
		given(this.responseSpec.bodyToMono(TweetDto.class)).willReturn(Mono.error(server));

		StepVerifier.create(this.tweetClient.getTweetSummary(tweetId))
			.expectErrorMatches(e -> e instanceof InteractionException && e.getCause() == server)
			.verify();
	}

	@Test
	void getUserTweetsSummary_NonNotFound_WrapsAsInteractionException() {
		UUID userId = UUID.randomUUID();
		WebClientResponseException server = WebClientResponseException.create(HttpStatus.SERVICE_UNAVAILABLE.value(),
				"down", null, null, null);
		given(this.responseSpec.bodyToFlux(TweetSummaryDto.class)).willReturn(Flux.error(server));

		StepVerifier.create(this.tweetClient.getUserTweetsSummary(userId))
			.expectErrorMatches(e -> e instanceof InteractionException && e.getCause() == server)
			.verify();
	}

	@Test
	void getTweetSummary_NotFound_WrapsAsTweetNotFound() {
		UUID tweetId = UUID.randomUUID();
		WebClientResponseException notFound = WebClientResponseException.create(HttpStatus.NOT_FOUND.value(),
				"missing", null, null, null);
		given(this.responseSpec.bodyToMono(TweetDto.class)).willReturn(Mono.error(notFound));

		StepVerifier.create(this.tweetClient.getTweetSummary(tweetId))
			.expectError(ro.tweebyte.interactionservice.exception.TweetNotFoundException.class)
			.verify();
	}

	@Test
	void getUserTweetsSummary_NotFound_WrapsAsTweetNotFound() {
		UUID userId = UUID.randomUUID();
		WebClientResponseException notFound = WebClientResponseException.create(HttpStatus.NOT_FOUND.value(),
				"missing", null, null, null);
		given(this.responseSpec.bodyToFlux(TweetSummaryDto.class)).willReturn(Flux.error(notFound));

		StepVerifier.create(this.tweetClient.getUserTweetsSummary(userId))
			.expectError(ro.tweebyte.interactionservice.exception.TweetNotFoundException.class)
			.verify();
	}

	@Test
	void getPopularHashtags_NotFound_WrapsAsTweetNotFound() {
		WebClientResponseException notFound = WebClientResponseException.create(HttpStatus.NOT_FOUND.value(), "missing",
				null, null, null);
		given(this.responseSpec.bodyToFlux(TweetDto.HashtagDto.class)).willReturn(Flux.error(notFound));

		StepVerifier.create(this.tweetClient.getPopularHashtags())
			.expectError(ro.tweebyte.interactionservice.exception.TweetNotFoundException.class)
			.verify();
	}

	@Test
	void getPopularHashtags_ExistingInteractionException_PassedThrough() {
		// The onErrorMap passes an already-wrapped InteractionException through
		// unchanged instead of double-wrapping it.
		InteractionException original = new InteractionException(new IllegalStateException("already wrapped"));
		given(this.responseSpec.bodyToFlux(TweetDto.HashtagDto.class)).willReturn(Flux.error(original));

		StepVerifier.create(this.tweetClient.getPopularHashtags())
			.expectErrorMatches(e -> e == original)
			.verify();
	}

	@Test
	void getPopularHashtags_GenericError_WrapsAsInteractionException() {
		RuntimeException boom = new RuntimeException("network down");
		given(this.responseSpec.bodyToFlux(TweetDto.HashtagDto.class)).willReturn(Flux.error(boom));

		StepVerifier.create(this.tweetClient.getPopularHashtags())
			.expectErrorMatches(e -> e instanceof InteractionException && e.getCause() == boom)
			.verify();
	}

	@Test
	void getTweetSummaryFallback_PropagatesThrowable() {
		RuntimeException cause = new RuntimeException("circuit open");
		StepVerifier.create(this.tweetClient.getTweetSummaryFallback(UUID.randomUUID(), cause))
			.expectErrorMatches(e -> e == cause)
			.verify();
	}

	@Test
	void getUserTweetsSummaryFallback_PropagatesThrowable() {
		RuntimeException cause = new RuntimeException("bulkhead full");
		StepVerifier.create(this.tweetClient.getUserTweetsSummaryFallback(UUID.randomUUID(), cause))
			.expectErrorMatches(e -> e == cause)
			.verify();
	}

	@Test
	void getPopularHashtagsFallback_PropagatesThrowable() {
		RuntimeException cause = new RuntimeException("timeout");
		StepVerifier.create(this.tweetClient.getPopularHashtagsFallback(cause))
			.expectErrorMatches(e -> e == cause)
			.verify();
	}

	@Test
	void getPopularHashtags_Non404WebClientResponseException_WrapsAsInteractionException() {
		// Branch: e instanceof WebClientResponseException w → true, BUT
		// w.getStatusCode() != NOT_FOUND → the first if is false; falls through to the
		// InteractionException wrap (third branch).
		WebClientResponseException serverError = WebClientResponseException.create(
				HttpStatus.INTERNAL_SERVER_ERROR.value(), "server error", null, null, null);
		given(this.responseSpec.bodyToFlux(TweetDto.HashtagDto.class)).willReturn(Flux.error(serverError));

		StepVerifier.create(this.tweetClient.getPopularHashtags())
			.expectErrorMatches(e -> e instanceof InteractionException && e.getCause() == serverError)
			.verify();
	}

}
