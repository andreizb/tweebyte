/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.client;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.tweetservice.exception.FollowRetrievingException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * Covers the Resilience4j (W6) fallback methods, which {@link InteractionClientTests} does
 * not exercise. Each fallback must error the Mono/Flux with the originating throwable.
 */
@ExtendWith(MockitoExtension.class)
class InteractionClientBranchTests {

	private InteractionClient interactionClient;

	@Mock
	private WebClient.Builder webClientBuilderMock;

	@Mock
	private WebClient webClientMock;

	@Mock
	@SuppressWarnings("rawtypes")
	private WebClient.RequestHeadersUriSpec requestHeadersUriSpecMock;

	@Mock
	@SuppressWarnings("rawtypes")
	private WebClient.RequestHeadersSpec requestHeadersSpecMock;

	@Mock
	private WebClient.ResponseSpec responseSpecMock;

	@Mock
	private ReactiveRedisTemplate<String, Object> redisTemplate;

	@BeforeEach
	void setUp() {
		this.interactionClient = new InteractionClient(this.redisTemplate, this.webClientBuilderMock);
		ReflectionTestUtils.setField(this.interactionClient, "webClient", this.webClientMock);
	}

	// ---------- W6 Resilience4j fallback methods: each forwards the throwable ----------

	@Test
	void getRepliesCountFallback_errorsWithThrowable() {
		RuntimeException boom = new RuntimeException("boom");
		StepVerifier.create(this.interactionClient.getRepliesCountFallback(UUID.randomUUID(), boom))
			.expectErrorMatches(e -> e == boom)
			.verify();
	}

	@Test
	void getLikesCountFallback_errorsWithThrowable() {
		RuntimeException boom = new RuntimeException("boom");
		StepVerifier.create(this.interactionClient.getLikesCountFallback(UUID.randomUUID(), boom))
			.expectErrorMatches(e -> e == boom)
			.verify();
	}

	@Test
	void getRetweetsCountFallback_errorsWithThrowable() {
		RuntimeException boom = new RuntimeException("boom");
		StepVerifier.create(this.interactionClient.getRetweetsCountFallback(UUID.randomUUID(), boom))
			.expectErrorMatches(e -> e == boom)
			.verify();
	}

	@Test
	void getTopReplyFallback_errorsWithThrowable() {
		RuntimeException boom = new RuntimeException("boom");
		StepVerifier.create(this.interactionClient.getTopReplyFallback(UUID.randomUUID(), boom))
			.expectErrorMatches(e -> e == boom)
			.verify();
	}

	@Test
	void getRepliesForTweetFallback_errorsWithThrowable() {
		RuntimeException boom = new RuntimeException("boom");
		StepVerifier.create(this.interactionClient.getRepliesForTweetFallback(UUID.randomUUID(), boom))
			.expectErrorMatches(e -> e == boom)
			.verify();
	}

	@Test
	void getFollowedIdsFallback_errorsWithThrowable() {
		RuntimeException boom = new RuntimeException("boom");
		StepVerifier.create(this.interactionClient.getFollowedIdsFallback(UUID.randomUUID(), boom))
			.expectErrorMatches(e -> e == boom)
			.verify();
	}

	@Test
	void getTweetInteractionsFallback_errorsWithThrowable() {
		RuntimeException boom = new RuntimeException("boom");
		StepVerifier
			.create(this.interactionClient.getTweetInteractionsFallback(List.of(UUID.randomUUID()), boom))
			.expectErrorMatches(e -> e == boom)
			.verify();
	}

	// -------------------------------------------------------------------------
	// onErrorMap branch in getFollowedIds:
	// predicate = e -> !(e instanceof FollowRetrievingException)
	// Covered branch: predicate returns TRUE (non-FollowRetrievingException → mapped)
	// -------------------------------------------------------------------------

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	void getFollowedIds_nonFollowRetrievingException_isMappedToFollowRetrievingException() {
		UUID userId = UUID.randomUUID();
		RuntimeException originalError = new RuntimeException("network error");

		given(this.webClientMock.get()).willReturn(this.requestHeadersUriSpecMock);
		given(this.requestHeadersUriSpecMock.uri(any(Function.class))).willReturn(this.requestHeadersSpecMock);
		given(this.requestHeadersSpecMock.retrieve()).willReturn(this.responseSpecMock);
		given(this.responseSpecMock.bodyToMono(any(ParameterizedTypeReference.class)))
			.willReturn(Mono.error(originalError));

		StepVerifier.create(this.interactionClient.getFollowedIds(userId))
			.expectErrorMatches(e -> e instanceof FollowRetrievingException
					&& e.getCause() == originalError)
			.verify();
	}

	// onErrorMap false-branch: error IS a FollowRetrievingException → predicate returns false → not remapped
	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	void getFollowedIds_followRetrievingExceptionPassesThroughUnmapped() {
		UUID userId = UUID.randomUUID();
		FollowRetrievingException alreadyWrapped = new FollowRetrievingException("already wrapped", new RuntimeException());

		given(this.webClientMock.get()).willReturn(this.requestHeadersUriSpecMock);
		given(this.requestHeadersUriSpecMock.uri(any(Function.class))).willReturn(this.requestHeadersSpecMock);
		given(this.requestHeadersSpecMock.retrieve()).willReturn(this.responseSpecMock);
		given(this.responseSpecMock.bodyToMono(any(ParameterizedTypeReference.class)))
			.willReturn(Mono.error(alreadyWrapped));

		// Should remain a FollowRetrievingException, not double-wrapped
		StepVerifier.create(this.interactionClient.getFollowedIds(userId))
			.expectErrorMatches(e -> e == alreadyWrapped)
			.verify();
	}

}
