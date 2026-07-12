/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.client;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import ro.tweebyte.tweetservice.exception.UserNotFoundException;
import ro.tweebyte.tweetservice.model.UserDto;

@Component
@RequiredArgsConstructor
public class UserClient {

	@Value("${app.user.base-url}")
	private String baseUrl;

	@Qualifier("httpClientExecutor")
	private final ExecutorService httpClientExecutor;

	private final RestClient.Builder restClientBuilder;

	private RestClient restClient;

	@PostConstruct
	void init() {
		this.restClient = this.restClientBuilder.baseUrl(this.baseUrl).build();
	}

	@CircuitBreaker(name = "userClient", fallbackMethod = "getUserSummaryFallback")
	@Retry(name = "userClient")
	@Bulkhead(name = "userClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "userClient")
	public CompletableFuture<UserDto> getUserSummary(String userName) {
		// Only a 4xx maps to UserNotFoundException (404). 5xx and transport errors
		// propagate as-is instead of being masked as not-found — mirrors the
		// reactive client's onStatus(is4xxClientError, ...).
		return CompletableFuture.supplyAsync(() -> this.restClient.get()
			.uri("users/summary/name/{name}", userName)
			.retrieve()
			.onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
				throw new UserNotFoundException("User not found for name: " + userName);
			})
			.body(UserDto.class), this.httpClientExecutor);
	}

	public CompletableFuture<UserDto> getUserSummaryFallback(String userName, Throwable t) {
		return CompletableFuture.failedFuture(t);
	}

	@CircuitBreaker(name = "userClient", fallbackMethod = "getUserSummaryFallback")
	@Retry(name = "userClient")
	@Bulkhead(name = "userClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "userClient")
	public CompletableFuture<UserDto> getUserSummary(UUID userId) {
		return CompletableFuture.supplyAsync(() -> this.restClient.get()
			.uri("users/summary/{id}", userId)
			.retrieve()
			.onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
				throw new UserNotFoundException("User not found for id: " + userId);
			})
			.body(UserDto.class), this.httpClientExecutor);
	}

	public CompletableFuture<UserDto> getUserSummaryFallback(UUID userId, Throwable t) {
		return CompletableFuture.failedFuture(t);
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
	public CompletableFuture<List<UserDto>> getUserSummaries(List<UUID> userIds) {
		return CompletableFuture.supplyAsync(() -> this.restClient.post()
			.uri("users/summaries")
			.body(userIds)
			.retrieve()
			.body(new ParameterizedTypeReference<List<UserDto>>() {
			}), this.httpClientExecutor);
	}

	public CompletableFuture<List<UserDto>> getUserSummariesFallback(List<UUID> userIds, Throwable t) {
		return CompletableFuture.failedFuture(t);
	}

	@CircuitBreaker(name = "userClient", fallbackMethod = "mediaExistsFallback")
	@Retry(name = "userClient")
	@Bulkhead(name = "userClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "userClient")
	public CompletableFuture<Boolean> mediaExists(UUID mediaId) {
		// user-service returns 200 with {"exists": false} for unknown ids (never a
		// 404), so the answer rides on the body rather than the status code.
		return CompletableFuture.supplyAsync(() -> {
			Map<String, Boolean> body = this.restClient.get()
				.uri("media/{id}/exists", mediaId)
				.retrieve()
				.body(new ParameterizedTypeReference<Map<String, Boolean>>() {
				});
			return body != null && Boolean.TRUE.equals(body.get("exists"));
		}, this.httpClientExecutor);
	}

	public CompletableFuture<Boolean> mediaExistsFallback(UUID mediaId, Throwable t) {
		return CompletableFuture.failedFuture(t);
	}

}
