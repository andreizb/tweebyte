/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.client;

import java.util.UUID;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

	private final WebClient.Builder webClientBuilder;

	private WebClient webClient = null;

	@PostConstruct
	public void init() {
		this.webClient = this.webClientBuilder.baseUrl(this.baseUrl).build();
	}

	@CircuitBreaker(name = "tweetClient", fallbackMethod = "getTweetSummaryFallback")
	@Retry(name = "tweetClient")
	@Bulkhead(name = "tweetClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "tweetClient")
	public Mono<TweetDto> getTweetSummary(UUID tweetId) {
		return this.webClient.get()
			.uri(uriBuilder -> uriBuilder.path("tweets/{tweetId}/summary").build(tweetId))
			.retrieve()
			.bodyToMono(TweetDto.class)
			.onErrorMap(WebClientResponseException.class, e -> {
				if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
					return new TweetNotFoundException("Tweet not found with id: " + tweetId);
				}
				return new InteractionException(e);
			});
	}

	public Mono<TweetDto> getTweetSummaryFallback(UUID tweetId, Throwable t) {
		return Mono.error(t);
	}

	@CircuitBreaker(name = "tweetClient", fallbackMethod = "getUserTweetsSummaryFallback")
	@Retry(name = "tweetClient")
	@Bulkhead(name = "tweetClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "tweetClient")
	public Flux<TweetSummaryDto> getUserTweetsSummary(UUID userId) {
		return this.webClient.get()
			.uri(uriBuilder -> uriBuilder.path("tweets/user/{userId}/summary").build(userId))
			.retrieve()
			.bodyToFlux(TweetSummaryDto.class)
			.onErrorMap(WebClientResponseException.class, e -> {
				if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
					return new TweetNotFoundException("Tweets not found for user id: " + userId);
				}
				return new InteractionException(e);
			});
	}

	public Flux<TweetSummaryDto> getUserTweetsSummaryFallback(UUID userId, Throwable t) {
		return Flux.error(t);
	}

	@CircuitBreaker(name = "tweetClient", fallbackMethod = "getPopularHashtagsFallback")
	@Retry(name = "tweetClient")
	@Bulkhead(name = "tweetClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "tweetClient")
	public Flux<TweetDto.HashtagDto> getPopularHashtags() {
		return this.webClient.get()
			// tweet-service maps `/tweets/hashtag/popular` (singular).
			.uri(uriBuilder -> uriBuilder.path("tweets/hashtag/popular").build())
			.retrieve()
			.bodyToFlux(TweetDto.HashtagDto.class)
			.onErrorMap(e -> {
				if (e instanceof WebClientResponseException w && w.getStatusCode() == HttpStatus.NOT_FOUND) {
					return new TweetNotFoundException("Popular hashtags not found");
				}
				if (e instanceof InteractionException) {
					return e;
				}
				log.error("failed to retrieve popular hashtags", e);
				return new InteractionException(e);
			});
	}

	public Flux<TweetDto.HashtagDto> getPopularHashtagsFallback(Throwable t) {
		return Flux.error(t);
	}

}
