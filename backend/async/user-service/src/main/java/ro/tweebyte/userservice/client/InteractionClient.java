/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.client;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import ro.tweebyte.userservice.exception.FollowRetrievingException;
import ro.tweebyte.userservice.model.ProfileInteractionsDto;

@Slf4j
@Component
@RequiredArgsConstructor
public class InteractionClient {

	@Value("${app.interaction.base-url}")
	private String baseUrl;

	@Qualifier("httpClientExecutor")
	private final ExecutorService httpClientExecutor;

	private final RestClient.Builder restClientBuilder;

	private RestClient restClient;

	@PostConstruct
	void init() {
		this.restClient = this.restClientBuilder.baseUrl(this.baseUrl).build();
	}

	// Combined user-profile read: the user's follow counts AND the per-tweet interactions for the
	// supplied tweet-id page in one round-trip. Same resilience envelope and error mapping as the
	// other interaction reads.
	@CircuitBreaker(name = "interactionClient", fallbackMethod = "getProfileInteractionsFallback")
	@Retry(name = "interactionClient")
	@Bulkhead(name = "interactionClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "interactionClient")
	public CompletableFuture<ProfileInteractionsDto> getProfileInteractions(UUID userId, List<UUID> tweetIds) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return this.restClient.post()
					.uri("/follows/{userId}/profile-interactions", userId)
					.body(tweetIds)
					.retrieve()
					.body(ProfileInteractionsDto.class);
			}
			catch (Exception ex) {
				log.error("failed to retrieve profile interactions for user {}", userId, ex);
				throw new FollowRetrievingException("failed to retrieve profile interactions for user " + userId, ex);
			}
		}, this.httpClientExecutor);
	}

	public CompletableFuture<ProfileInteractionsDto> getProfileInteractionsFallback(UUID userId, List<UUID> tweetIds,
			Throwable t) {
		return CompletableFuture.failedFuture(t);
	}

	// Cross-DB media references held by interaction-service (replies + retweets),
	// for the stale-media GC.
	@CircuitBreaker(name = "interactionClient", fallbackMethod = "getReferencedMediaIdsFallback")
	@Retry(name = "interactionClient")
	@Bulkhead(name = "interactionClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "interactionClient")
	public CompletableFuture<List<UUID>> getReferencedMediaIds() {
		return CompletableFuture.supplyAsync(() -> this.restClient.get()
			.uri("/media/referenced")
			.retrieve()
			.body(new ParameterizedTypeReference<List<UUID>>() {
			}), this.httpClientExecutor);
	}

	public CompletableFuture<List<UUID>> getReferencedMediaIdsFallback(Throwable t) {
		return CompletableFuture.failedFuture(t);
	}

}
