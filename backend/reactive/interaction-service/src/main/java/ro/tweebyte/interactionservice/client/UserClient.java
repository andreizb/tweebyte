/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.client;

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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import ro.tweebyte.interactionservice.exception.InteractionException;
import ro.tweebyte.interactionservice.exception.UserNotFoundException;
import ro.tweebyte.interactionservice.model.UserDto;

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
	public Mono<UserDto> getUserSummary(UUID userId) {
		return this.webClient.get()
			.uri(uriBuilder -> uriBuilder.path("users/summary/{userId}").build(userId))
			.retrieve()
			.bodyToMono(UserDto.class)
			.onErrorMap(WebClientResponseException.class, e -> {
				if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
					return new UserNotFoundException("User not found with id: " + userId);
				}
				return new InteractionException(e);
			});
	}

	public Mono<UserDto> getUserSummaryFallback(UUID userId, Throwable t) {
		return Mono.error(t);
	}

	// Batched counterpart of getUserSummary: one POST resolves a whole page of cold users,
	// replacing the per-id GET the following-cache fill issued one-per-user. The id list rides
	// in the request body (unbounded in prod) and the response is decoded as an array of rows;
	// a missing id is simply absent from the array. Same resilience envelope as the single read.
	@CircuitBreaker(name = "userClient", fallbackMethod = "getUserSummariesFallback")
	@Retry(name = "userClient")
	@Bulkhead(name = "userClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "userClient")
	public Mono<List<UserDto>> getUserSummaries(List<UUID> userIds) {
		return this.webClient.post()
			.uri("users/summaries")
			.bodyValue(userIds)
			.retrieve()
			.bodyToFlux(UserDto.class)
			.collectList()
			.onErrorMap(WebClientResponseException.class, InteractionException::new);
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
			.uri(uriBuilder -> uriBuilder.path("media/{id}/exists").build(mediaId))
			.retrieve()
			.bodyToMono(new ParameterizedTypeReference<Map<String, Boolean>>() {
			})
			.map(body -> Boolean.TRUE.equals(body.get("exists")))
			.onErrorMap(WebClientResponseException.class, InteractionException::new);
	}

	public Mono<Boolean> mediaExistsFallback(UUID mediaId, Throwable t) {
		return Mono.error(t);
	}

}
