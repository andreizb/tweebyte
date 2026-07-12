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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import ro.tweebyte.userservice.model.TweetDto;

@Component
@RequiredArgsConstructor
public class TweetClient {

	@Value("${app.tweet.base-url}")
	private String baseUrl;

	@Qualifier("httpClientExecutor")
	private final ExecutorService httpClientExecutor;

	private final RestClient.Builder restClientBuilder;

	private RestClient restClient;

	@PostConstruct
	void init() {
		this.restClient = this.restClientBuilder.baseUrl(this.baseUrl).build();
	}

	// Un-enriched tweet page for the user-profile read: ?enrich=false skips tweet-service's
	// interaction-service enrichment, so the tweets come back with zero counts / empty top reply.
	// The profile read resolves the interactions itself in one combined call and merges them in,
	// trimming one cross-service round-trip.
	@CircuitBreaker(name = "tweetClient", fallbackMethod = "getUserProfileTweetsFallback")
	@Retry(name = "tweetClient")
	@Bulkhead(name = "tweetClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "tweetClient")
	public CompletableFuture<List<TweetDto>> getUserProfileTweets(UUID userId) {
		return CompletableFuture.supplyAsync(() -> this.restClient.get()
			.uri("/tweets/user/{userId}?enrich=false", userId)
			.retrieve()
			.body(new ParameterizedTypeReference<List<TweetDto>>() {
			}), this.httpClientExecutor);
	}

	public CompletableFuture<List<TweetDto>> getUserProfileTweetsFallback(UUID userId, Throwable t) {
		return CompletableFuture.failedFuture(t);
	}

	// Cross-DB media references held by tweet-service, for the stale-media GC.
	@CircuitBreaker(name = "tweetClient", fallbackMethod = "getReferencedMediaIdsFallback")
	@Retry(name = "tweetClient")
	@Bulkhead(name = "tweetClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "tweetClient")
	public CompletableFuture<List<UUID>> getReferencedMediaIds() {
		return CompletableFuture.supplyAsync(() -> this.restClient.get()
			.uri("/tweets/media/referenced")
			.retrieve()
			.body(new ParameterizedTypeReference<List<UUID>>() {
			}), this.httpClientExecutor);
	}

	public CompletableFuture<List<UUID>> getReferencedMediaIdsFallback(Throwable t) {
		return CompletableFuture.failedFuture(t);
	}

}
