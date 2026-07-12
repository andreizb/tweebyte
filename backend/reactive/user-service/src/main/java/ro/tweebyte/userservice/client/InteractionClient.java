/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.client;

import java.util.List;
import java.util.UUID;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import ro.tweebyte.userservice.exception.FollowRetrievingException;
import ro.tweebyte.userservice.model.ProfileInteractionsDto;

@Slf4j
@Component
@RequiredArgsConstructor
public class InteractionClient {

	@Value("${app.interaction.base-url}")
	private String baseUrl;

	private final WebClient.Builder webClientBuilder;

	private WebClient webClient = null;

	@PostConstruct
	public void init() {
		this.webClient = this.webClientBuilder.baseUrl(this.baseUrl).build();
	}

	// Combined user-profile read: the user's follow counts AND the per-tweet interactions for the
	// supplied tweet-id page in one round-trip. Same resilience envelope and error mapping as the
	// other interaction reads.
	@CircuitBreaker(name = "interactionClient", fallbackMethod = "getProfileInteractionsFallback")
	@Retry(name = "interactionClient")
	@Bulkhead(name = "interactionClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "interactionClient")
	public Mono<ProfileInteractionsDto> getProfileInteractions(UUID userId, List<UUID> tweetIds) {
		return this.webClient.post()
			.uri(uriBuilder -> uriBuilder.path("/follows/{userId}/profile-interactions").build(userId.toString()))
			.bodyValue(tweetIds)
			.retrieve()
			.bodyToMono(ProfileInteractionsDto.class)
			.doOnError(e -> log.error("failed to retrieve profile interactions for user {}", userId, e))
			.onErrorMap(e -> new FollowRetrievingException("failed to retrieve profile interactions for user " + userId, e));
	}

	public Mono<ProfileInteractionsDto> getProfileInteractionsFallback(UUID userId, List<UUID> tweetIds, Throwable t) {
		return Mono.error(t);
	}

	// Cross-DB media references held by interaction-service (replies + retweets),
	// for the stale-media GC.
	@CircuitBreaker(name = "interactionClient", fallbackMethod = "getReferencedMediaIdsFallback")
	@Retry(name = "interactionClient")
	@Bulkhead(name = "interactionClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "interactionClient")
	public Flux<UUID> getReferencedMediaIds() {
		return this.webClient.get().uri("/media/referenced").retrieve().bodyToFlux(UUID.class);
	}

	public Flux<UUID> getReferencedMediaIdsFallback(Throwable t) {
		return Flux.error(t);
	}

}
