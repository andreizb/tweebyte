/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.client;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import ro.tweebyte.tweetservice.exception.UserNotFoundException;
import ro.tweebyte.tweetservice.model.UserDto;

@Component
@RequiredArgsConstructor
public class UserClient {

	@Value("${app.user.base-url}")
	private String baseUrl;

	private final WebClient.Builder webClientBuilder;

	private WebClient webClient = null;

	@PostConstruct
	public void init() {
		this.webClient = this.webClientBuilder.baseUrl(this.baseUrl).build();
	}

	@CircuitBreaker(name = "userClient", fallbackMethod = "getUserSummaryFallback")
	@Retry(name = "userClient")
	@Bulkhead(name = "userClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "userClient")
	public Mono<UserDto> getUserSummary(String userName) {
		return this.webClient.get()
			.uri("/users/summary/name/{userName}", userName)
			.retrieve()
			.onStatus(HttpStatusCode::is4xxClientError,
					response -> Mono.error(new UserNotFoundException("User not found for name: " + userName)))
			.bodyToMono(UserDto.class);
	}

	public Mono<UserDto> getUserSummaryFallback(String userName, Throwable t) {
		return Mono.error(t);
	}

	@CircuitBreaker(name = "userClient", fallbackMethod = "getUserSummaryFallback")
	@Retry(name = "userClient")
	@Bulkhead(name = "userClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "userClient")
	public Mono<UserDto> getUserSummary(UUID userId) {
		return this.webClient.get()
			.uri("/users/summary/{userId}", userId)
			.retrieve()
			.onStatus(HttpStatusCode::is4xxClientError,
					response -> Mono.error(new UserNotFoundException("User not found for id: " + userId)))
			.bodyToMono(UserDto.class);
	}

	public Mono<UserDto> getUserSummaryFallback(UUID userId, Throwable t) {
		return Mono.error(t);
	}

	// Batched counterpart of getUserSummary(UUID): one POST resolves a whole page of author
	// ids, collapsing the per-result GET the tweet-search enrichment issued one-per-tweet. The
	// id list rides in the request body and the response is decoded as an array of rows; a
	// missing id is simply absent from the array (the endpoint never 404s, unlike the single
	// read), so the caller reproduces the per-call not-found by detecting the absent id itself.
	// Same resilience envelope as the single read.
	@CircuitBreaker(name = "userClient", fallbackMethod = "getUserSummariesFallback")
	@Retry(name = "userClient")
	@Bulkhead(name = "userClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "userClient")
	public Mono<List<UserDto>> getUserSummaries(List<UUID> userIds) {
		return this.webClient.post()
			.uri("/users/summaries")
			.bodyValue(userIds)
			.retrieve()
			.bodyToFlux(UserDto.class)
			.collectList();
	}

	public Mono<List<UserDto>> getUserSummariesFallback(List<UUID> userIds, Throwable t) {
		return Mono.error(t);
	}

	@CircuitBreaker(name = "userClient", fallbackMethod = "mediaExistsFallback")
	@Retry(name = "userClient")
	@Bulkhead(name = "userClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "userClient")
	public Mono<Boolean> mediaExists(UUID mediaId) {
		// user-service returns 200 with {"exists": false} for unknown ids (never a
		// 404), so the answer rides on the body rather than the status code.
		return this.webClient.get()
			.uri("/media/{id}/exists", mediaId)
			.retrieve()
			.bodyToMono(new ParameterizedTypeReference<Map<String, Boolean>>() {
			})
			.map(body -> Boolean.TRUE.equals(body.get("exists")));
	}

	public Mono<Boolean> mediaExistsFallback(UUID mediaId, Throwable t) {
		return Mono.error(t);
	}

}
