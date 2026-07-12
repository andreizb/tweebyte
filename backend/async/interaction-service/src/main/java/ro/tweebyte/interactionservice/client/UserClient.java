/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.client;

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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import ro.tweebyte.interactionservice.exception.InteractionException;
import ro.tweebyte.interactionservice.exception.UserNotFoundException;
import ro.tweebyte.interactionservice.model.UserDto;

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
	public CompletableFuture<UserDto> getUserSummary(UUID userId) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return this.restClient.get().uri("users/summary/{userId}", userId).retrieve().body(UserDto.class);
			}
			catch (RestClientResponseException ex) {
				if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
					throw new UserNotFoundException("User not found with id: " + userId);
				}
				throw new InteractionException(ex);
			}
			catch (Exception ex) {
				throw new InteractionException(ex);
			}
		}, this.httpClientExecutor);
	}

	public CompletableFuture<UserDto> getUserSummaryFallback(UUID userId, Throwable t) {
		return CompletableFuture.failedFuture(t);
	}

	// Batched counterpart of getUserSummary: one POST resolves a whole page of cold users,
	// replacing the per-id GET the following-cache fill issued one-per-user. The id list rides
	// in the request body (unbounded in prod) and the response is decoded as an array of rows;
	// a missing id is simply absent from the array. Same resilience envelope as the single read.
	@CircuitBreaker(name = "userClient", fallbackMethod = "getUserSummariesFallback")
	@Retry(name = "userClient")
	@Bulkhead(name = "userClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "userClient")
	public CompletableFuture<List<UserDto>> getUserSummaries(List<UUID> userIds) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return this.restClient.post()
					.uri("users/summaries")
					.body(userIds)
					.retrieve()
					.body(new ParameterizedTypeReference<List<UserDto>>() {
					});
			}
			catch (Exception ex) {
				// The batch endpoint never 404s — an unknown id is simply omitted from the
				// array — so there is no per-id not-found case to translate (unlike the single
				// read); any transport failure wraps uniformly.
				throw new InteractionException(ex);
			}
		}, this.httpClientExecutor);
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
			try {
				Map<String, Boolean> body = this.restClient.get()
					.uri("media/{id}/exists", mediaId)
					.retrieve()
					.body(new ParameterizedTypeReference<Map<String, Boolean>>() {
					});
				return body != null && Boolean.TRUE.equals(body.get("exists"));
			}
			catch (Exception ex) {
				throw new InteractionException(ex);
			}
		}, this.httpClientExecutor);
	}

	public CompletableFuture<Boolean> mediaExistsFallback(UUID mediaId, Throwable t) {
		return CompletableFuture.failedFuture(t);
	}

}
