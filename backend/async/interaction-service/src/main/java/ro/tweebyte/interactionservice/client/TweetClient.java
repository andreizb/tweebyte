/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.client;

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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import ro.tweebyte.interactionservice.exception.InteractionException;
import ro.tweebyte.interactionservice.exception.TweetNotFoundException;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.TweetSummaryDto;

@Slf4j
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

	@CircuitBreaker(name = "tweetClient", fallbackMethod = "getTweetSummaryFallback")
	@Retry(name = "tweetClient")
	@Bulkhead(name = "tweetClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "tweetClient")
	public CompletableFuture<TweetDto> getTweetSummary(UUID tweetId) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return this.restClient.get().uri("tweets/{tweetId}/summary", tweetId).retrieve().body(TweetDto.class);
			}
			catch (RestClientResponseException ex) {
				if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
					throw new TweetNotFoundException("Tweet not found with id: " + tweetId);
				}
				throw new InteractionException(ex);
			}
			catch (Exception ex) {
				throw new InteractionException(ex);
			}
		}, this.httpClientExecutor);
	}

	public CompletableFuture<TweetDto> getTweetSummaryFallback(UUID tweetId, Throwable t) {
		return CompletableFuture.failedFuture(t);
	}

	@CircuitBreaker(name = "tweetClient", fallbackMethod = "getUserTweetsSummaryFallback")
	@Retry(name = "tweetClient")
	@Bulkhead(name = "tweetClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "tweetClient")
	public CompletableFuture<List<TweetSummaryDto>> getUserTweetsSummary(UUID userId) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return this.restClient.get()
					.uri("tweets/user/{userId}/summary", userId)
					.retrieve()
					.body(new ParameterizedTypeReference<List<TweetSummaryDto>>() {
					});
			}
			catch (RestClientResponseException ex) {
				if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
					throw new TweetNotFoundException("Tweets not found for user id: " + userId);
				}
				throw new InteractionException(ex);
			}
			catch (Exception ex) {
				throw new InteractionException(ex);
			}
		}, this.httpClientExecutor);
	}

	public CompletableFuture<List<TweetSummaryDto>> getUserTweetsSummaryFallback(UUID userId, Throwable t) {
		return CompletableFuture.failedFuture(t);
	}

	@CircuitBreaker(name = "tweetClient", fallbackMethod = "getPopularHashtagsFallback")
	@Retry(name = "tweetClient")
	@Bulkhead(name = "tweetClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "tweetClient")
	public CompletableFuture<List<TweetDto.HashtagDto>> getPopularHashtags() {
		return CompletableFuture.supplyAsync(() -> {
			try {
				// tweet-service maps `/tweets/hashtag/popular` (singular); the
				// path here must match exactly or every hashtag-rec request 500s.
				return this.restClient.get()
					.uri("tweets/hashtag/popular")
					.retrieve()
					.body(new ParameterizedTypeReference<List<TweetDto.HashtagDto>>() {
					});
			}
			catch (RestClientResponseException ex) {
				if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
					throw new TweetNotFoundException("Popular hashtags not found");
				}
				log.error("failed to retrieve popular hashtags", ex);
				throw new InteractionException(ex);
			}
			catch (Exception ex) {
				log.error("failed to retrieve popular hashtags", ex);
				throw new InteractionException(ex);
			}
		}, this.httpClientExecutor);
	}

	public CompletableFuture<List<TweetDto.HashtagDto>> getPopularHashtagsFallback(Throwable t) {
		return CompletableFuture.failedFuture(t);
	}

}
