/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.client;

import java.util.UUID;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import ro.tweebyte.userservice.model.TweetDto;

@Component
@RequiredArgsConstructor
public class TweetClient {

	@Value("${app.tweet.base-url}")
	private String baseUrl;

	private final WebClient.Builder webClientBuilder;

	private WebClient webClient = null;

	@PostConstruct
	public void init() {
		this.webClient = this.webClientBuilder.baseUrl(this.baseUrl).build();
	}

	// Un-enriched tweet page for the user-profile read: ?enrich=false skips tweet-service's
	// interaction-service enrichment, so the tweets come back with zero counts / empty top reply.
	// The profile read resolves the interactions itself in one combined call and merges them in,
	// trimming one cross-service round-trip.
	@CircuitBreaker(name = "tweetClient", fallbackMethod = "getUserProfileTweetsFallback")
	@Retry(name = "tweetClient")
	@Bulkhead(name = "tweetClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "tweetClient")
	public Flux<TweetDto> getUserProfileTweets(UUID userId) {
		return this.webClient.get()
			.uri(uriBuilder -> uriBuilder.path("/tweets/user/{userId}").queryParam("enrich", false).build(userId.toString()))
			.retrieve()
			.bodyToFlux(TweetDto.class);
	}

	public Flux<TweetDto> getUserProfileTweetsFallback(UUID userId, Throwable t) {
		return Flux.error(t);
	}

	// Cross-DB media references held by tweet-service, for the stale-media GC.
	@CircuitBreaker(name = "tweetClient", fallbackMethod = "getReferencedMediaIdsFallback")
	@Retry(name = "tweetClient")
	@Bulkhead(name = "tweetClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "tweetClient")
	public Flux<UUID> getReferencedMediaIds() {
		return this.webClient.get().uri("/tweets/media/referenced").retrieve().bodyToFlux(UUID.class);
	}

	public Flux<UUID> getReferencedMediaIdsFallback(Throwable t) {
		return Flux.error(t);
	}

}
