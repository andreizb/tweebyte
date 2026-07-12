/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.client;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.tweetservice.exception.UserNotFoundException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * Covers {@code mediaExists} (both body branches) and the Resilience4j (W6) fallback
 * methods, which {@link UserClientTests} does not exercise.
 */
@ExtendWith(MockitoExtension.class)
class UserClientBranchTests {

	private UserClient userClient;

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
		this.userClient = new UserClient(this.webClientBuilderMock);
		ReflectionTestUtils.setField(this.userClient, "webClient", this.webClientMock);
	}

	@SuppressWarnings("unchecked")
	private void stubMediaGet() {
		given(this.webClientMock.get()).willReturn(this.requestHeadersUriSpecMock);
		given(this.requestHeadersUriSpecMock.uri(any(String.class), any(Object[].class)))
			.willReturn(this.requestHeadersSpecMock);
		given(this.requestHeadersSpecMock.retrieve()).willReturn(this.responseSpecMock);
	}

	@Test
	void mediaExists_trueWhenBodyExistsTrue() {
		stubMediaGet();
		given(this.responseSpecMock.bodyToMono(new ParameterizedTypeReference<Map<String, Boolean>>() {
		})).willReturn(Mono.just(Map.of("exists", true)));

		StepVerifier.create(this.userClient.mediaExists(UUID.randomUUID())).expectNext(true).verifyComplete();
	}

	@Test
	void mediaExists_falseWhenBodyExistsFalse() {
		stubMediaGet();
		given(this.responseSpecMock.bodyToMono(new ParameterizedTypeReference<Map<String, Boolean>>() {
		})).willReturn(Mono.just(Map.of("exists", false)));

		StepVerifier.create(this.userClient.mediaExists(UUID.randomUUID())).expectNext(false).verifyComplete();
	}

	@Test
	void mediaExists_exceptionThrown() {
		stubMediaGet();
		given(this.responseSpecMock.bodyToMono(new ParameterizedTypeReference<Map<String, Boolean>>() {
		})).willReturn(Mono.error(new RuntimeException("boom")));

		StepVerifier.create(this.userClient.mediaExists(UUID.randomUUID()))
			.expectError(RuntimeException.class)
			.verify();
	}

	// ---------- W6 Resilience4j fallback methods ----------

	@Test
	void getUserSummaryFallbackByName_errorsWithThrowable() {
		RuntimeException boom = new RuntimeException("boom");
		StepVerifier.create(this.userClient.getUserSummaryFallback("name", boom))
			.expectErrorMatches(e -> e == boom)
			.verify();
	}

	@Test
	void getUserSummaryFallbackById_errorsWithThrowable() {
		RuntimeException boom = new RuntimeException("boom");
		StepVerifier.create(this.userClient.getUserSummaryFallback(UUID.randomUUID(), boom))
			.expectErrorMatches(e -> e == boom)
			.verify();
	}

	@Test
	void mediaExistsFallback_errorsWithThrowable() {
		RuntimeException boom = new RuntimeException("boom");
		StepVerifier.create(this.userClient.mediaExistsFallback(UUID.randomUUID(), boom))
			.expectErrorMatches(e -> e == boom)
			.verify();
	}

	@Test
	void getUserSummariesFallback_errorsWithThrowable() {
		RuntimeException boom = new RuntimeException("boom");
		StepVerifier.create(this.userClient.getUserSummariesFallback(java.util.List.of(UUID.randomUUID()), boom))
			.expectErrorMatches(e -> e == boom)
			.verify();
	}

	// ---------- batched getUserSummaries via a scripted JSON body ----------

	private UserClient clientRespondingJson(String json) {
		ExchangeFunction exchange = request -> Mono.just(ClientResponse.create(HttpStatus.OK)
			.header("Content-Type", "application/json")
			.body(json)
			.build());
		UserClient client = new UserClient(WebClient.builder().exchangeFunction(exchange));
		ReflectionTestUtils.setField(client, "baseUrl", "http://user.local");
		client.init();
		return client;
	}

	@Test
	void getUserSummaries_decodesArray() {
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		String body = "[{\"id\":\"" + first + "\"},{\"id\":\"" + second + "\"}]";

		StepVerifier.create(this.clientRespondingJson(body).getUserSummaries(java.util.List.of(first, second)))
			.assertNext(users -> org.assertj.core.api.Assertions.assertThat(users)
				.extracting(ro.tweebyte.tweetservice.model.UserDto::getId)
				.containsExactly(first, second))
			.verifyComplete();
	}

	@Test
	void getUserSummaries_missingIdOmittedFromArray() {
		UUID present = UUID.randomUUID();
		UUID missing = UUID.randomUUID();
		// The batch endpoint never 404s — an unknown id is simply absent from the array.
		String body = "[{\"id\":\"" + present + "\"}]";

		StepVerifier.create(this.clientRespondingJson(body).getUserSummaries(java.util.List.of(present, missing)))
			.assertNext(users -> org.assertj.core.api.Assertions.assertThat(users)
				.extracting(ro.tweebyte.tweetservice.model.UserDto::getId)
				.containsExactly(present))
			.verifyComplete();
	}

	// ---------- onStatus(is4xxClientError, ...) lambdas via a scripted ExchangeFunction ----------

	private UserClient clientReturning(HttpStatus status) {
		ExchangeFunction exchange = request -> Mono.just(ClientResponse.create(status).build());
		UserClient client = new UserClient(WebClient.builder().exchangeFunction(exchange));
		ReflectionTestUtils.setField(client, "baseUrl", "http://user.local");
		client.init();
		return client;
	}

	@Test
	void getUserSummaryByName_4xxMapsToUserNotFound() {
		StepVerifier.create(this.clientReturning(HttpStatus.NOT_FOUND).getUserSummary("missing"))
			.expectErrorMatches(e -> e instanceof UserNotFoundException
					&& e.getMessage().equals("User not found for name: missing"))
			.verify();
	}

	@Test
	void getUserSummaryById_4xxMapsToUserNotFound() {
		UUID userId = UUID.randomUUID();
		StepVerifier.create(this.clientReturning(HttpStatus.NOT_FOUND).getUserSummary(userId))
			.expectErrorMatches(e -> e instanceof UserNotFoundException
					&& e.getMessage().equals("User not found for id: " + userId))
			.verify();
	}

}
