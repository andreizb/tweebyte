/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.client;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.interactionservice.exception.InteractionException;
import ro.tweebyte.interactionservice.model.UserDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * Branch coverage for UserClient.mediaExists (present/absent body, error wrapping) and the
 * Resilience4j fallback methods, which propagate the upstream failure as a failed Mono.
 */
@ExtendWith(MockitoExtension.class)
class UserClientBranchTests {

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

	@Mock
	private WebClient.RequestBodyUriSpec requestBodyUriSpec;

	@Mock
	private WebClient.RequestBodySpec requestBodySpec;

	@Mock
	private WebClient.RequestHeadersSpec<?> requestHeadersSpecPost;

	private UserClient userClient;

	@BeforeEach
	void setUp() {
		this.userClient = new UserClient(this.webClientBuilder);
		given(this.webClientBuilder.baseUrl(any())).willReturn(this.webClientBuilder);
		given(this.webClientBuilder.build()).willReturn(this.webClient);
		this.userClient.init();
	}

	@SuppressWarnings("unchecked")
	private void stubChain() {
		given(this.webClient.get()).willReturn(this.requestHeadersUriSpec);
		given(this.requestHeadersUriSpec.uri(any(Function.class))).willReturn(this.requestHeadersSpec);
		given(this.requestHeadersSpec.retrieve()).willReturn(this.responseSpec);
	}

	@Test
	void mediaExists_BodyExistsTrue_ReturnsTrue() {
		stubChain();
		given(this.responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
			.willReturn(Mono.just(Map.of("exists", true)));

		StepVerifier.create(this.userClient.mediaExists(UUID.randomUUID())).expectNext(true).verifyComplete();
	}

	@Test
	void mediaExists_BodyExistsFalse_ReturnsFalse() {
		// user-service returns 200 with {"exists": false} for unknown ids — the answer
		// rides on the body, so the false branch of Boolean.TRUE.equals is exercised.
		stubChain();
		given(this.responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
			.willReturn(Mono.just(Map.of("exists", false)));

		StepVerifier.create(this.userClient.mediaExists(UUID.randomUUID())).expectNext(false).verifyComplete();
	}

	@Test
	void mediaExists_ResponseError_WrapsAsInteractionException() {
		stubChain();
		WebClientResponseException server = WebClientResponseException
			.create(HttpStatus.INTERNAL_SERVER_ERROR.value(), "boom", null, null, null);
		given(this.responseSpec.bodyToMono(any(ParameterizedTypeReference.class))).willReturn(Mono.error(server));

		StepVerifier.create(this.userClient.mediaExists(UUID.randomUUID()))
			.expectErrorMatches(e -> e instanceof InteractionException && e.getCause() == server)
			.verify();
	}

	@SuppressWarnings("unchecked")
	private void stubPostChain() {
		given(this.webClient.post()).willReturn(this.requestBodyUriSpec);
		given(this.requestBodyUriSpec.uri("users/summaries")).willReturn(this.requestBodySpec);
		given(this.requestBodySpec.bodyValue(any())).willReturn((WebClient.RequestHeadersSpec) this.requestHeadersSpecPost);
		given(this.requestHeadersSpecPost.retrieve()).willReturn(this.responseSpec);
	}

	@Test
	void getUserSummaries_Success_CollectsRowsIntoList() {
		stubPostChain();
		UUID idOne = UUID.randomUUID();
		UUID idTwo = UUID.randomUUID();
		UserDto first = new UserDto();
		first.setId(idOne);
		UserDto second = new UserDto();
		second.setId(idTwo);
		given(this.responseSpec.bodyToFlux(UserDto.class)).willReturn(Flux.just(first, second));

		StepVerifier.create(this.userClient.getUserSummaries(List.of(idOne, idTwo)))
			.assertNext(users -> assertThat(users).containsExactly(first, second))
			.verifyComplete();
	}

	@Test
	void getUserSummaries_ResponseError_WrapsAsInteractionException() {
		stubPostChain();
		WebClientResponseException server = WebClientResponseException
			.create(HttpStatus.INTERNAL_SERVER_ERROR.value(), "boom", null, null, null);
		given(this.responseSpec.bodyToFlux(UserDto.class)).willReturn(Flux.error(server));

		StepVerifier.create(this.userClient.getUserSummaries(List.of(UUID.randomUUID())))
			.expectErrorMatches(e -> e instanceof InteractionException && e.getCause() == server)
			.verify();
	}

	@Test
	void getUserSummariesFallback_PropagatesThrowable() {
		RuntimeException cause = new RuntimeException("circuit open");
		StepVerifier.create(this.userClient.getUserSummariesFallback(List.of(UUID.randomUUID()), cause))
			.expectErrorMatches(e -> e == cause)
			.verify();
	}

	@Test
	void getUserSummaryFallback_PropagatesThrowable() {
		RuntimeException cause = new RuntimeException("circuit open");
		StepVerifier.create(this.userClient.getUserSummaryFallback(UUID.randomUUID(), cause))
			.expectErrorMatches(e -> e == cause)
			.verify();
	}

	@Test
	void mediaExistsFallback_PropagatesThrowable() {
		RuntimeException cause = new RuntimeException("bulkhead full");
		StepVerifier.create(this.userClient.mediaExistsFallback(UUID.randomUUID(), cause))
			.expectErrorMatches(e -> e == cause)
			.verify();
	}

}
