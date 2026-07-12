/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.client;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import ro.tweebyte.tweetservice.exception.FollowRetrievingException;
import ro.tweebyte.tweetservice.model.ReplyDto;
import ro.tweebyte.tweetservice.model.TweetInteractionsDto;
import ro.tweebyte.tweetservice.model.TweetInteractionsEntryDto;

/**
 * Blocking {@link RestClient} fan-out wrapped in {@link CompletableFuture#supplyAsync} on
 * the dedicated {@code httpClientExecutor}. Each call parks a real pool thread for the
 * cross-service round-trip — unlike the prior {@code HttpClient.sendAsync} on
 * {@code ForkJoinPool.commonPool()}, which did the I/O on a shared NIO selector thread
 * and so made the async fan-out artificially cheap.
 *
 * @author Andrei Zbarcea
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InteractionClient {

	private static final String FOLLOWED_CACHE = "followed_cache";

	@Value("${app.interaction.base-url}")
	private String baseUrl;

	private final RedisTemplate<String, Object> redisTemplate;

	private final ObjectMapper objectMapper;

	@Qualifier("httpClientExecutor")
	private final ExecutorService httpClientExecutor;

	private final RestClient.Builder restClientBuilder;

	private RestClient restClient;

	@PostConstruct
	void init() {
		this.restClient = this.restClientBuilder.baseUrl(this.baseUrl).build();
	}

	@CircuitBreaker(name = "interactionClient", fallbackMethod = "getRepliesCountFallback")
	@Retry(name = "interactionClient")
	@Bulkhead(name = "interactionClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "interactionClient")
	public CompletableFuture<Long> getRepliesCount(UUID tweetId) {
		return CompletableFuture.supplyAsync(
				() -> this.restClient.get().uri("replies/tweet/{tweetId}/count", tweetId).retrieve().body(Long.class),
				this.httpClientExecutor);
	}

	public CompletableFuture<Long> getRepliesCountFallback(UUID tweetId, Throwable t) {
		return CompletableFuture.failedFuture(t);
	}

	@CircuitBreaker(name = "interactionClient", fallbackMethod = "getLikesCountFallback")
	@Retry(name = "interactionClient")
	@Bulkhead(name = "interactionClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "interactionClient")
	public CompletableFuture<Long> getLikesCount(UUID tweetId) {
		return CompletableFuture.supplyAsync(
				() -> this.restClient.get().uri("likes/{tweetId}/count", tweetId).retrieve().body(Long.class),
				this.httpClientExecutor);
	}

	public CompletableFuture<Long> getLikesCountFallback(UUID tweetId, Throwable t) {
		return CompletableFuture.failedFuture(t);
	}

	@CircuitBreaker(name = "interactionClient", fallbackMethod = "getRetweetsCountFallback")
	@Retry(name = "interactionClient")
	@Bulkhead(name = "interactionClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "interactionClient")
	public CompletableFuture<Long> getRetweetsCount(UUID tweetId) {
		return CompletableFuture.supplyAsync(
				() -> this.restClient.get().uri("retweets/tweet/{tweetId}/count", tweetId).retrieve().body(Long.class),
				this.httpClientExecutor);
	}

	public CompletableFuture<Long> getRetweetsCountFallback(UUID tweetId, Throwable t) {
		return CompletableFuture.failedFuture(t);
	}

	@CircuitBreaker(name = "interactionClient", fallbackMethod = "getTopReplyFallback")
	@Retry(name = "interactionClient")
	@Bulkhead(name = "interactionClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "interactionClient")
	public CompletableFuture<ReplyDto> getTopReply(UUID tweetId) {
		return CompletableFuture.supplyAsync(
				() -> this.restClient.get().uri("replies/tweet/{tweetId}/top", tweetId).retrieve().body(ReplyDto.class),
				this.httpClientExecutor);
	}

	public CompletableFuture<ReplyDto> getTopReplyFallback(UUID tweetId, Throwable t) {
		return CompletableFuture.failedFuture(t);
	}

	// Consolidated per-tweet enrichment: one POST resolves the like/reply/retweet counts and
	// top reply for a whole page. Same resilience envelope and fail-the-future fallback as the
	// single-id calls above.
	@CircuitBreaker(name = "interactionClient", fallbackMethod = "getTweetInteractionsFallback")
	@Retry(name = "interactionClient")
	@Bulkhead(name = "interactionClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "interactionClient")
	public CompletableFuture<Map<UUID, TweetInteractionsDto>> getTweetInteractions(List<UUID> tweetIds) {
		return CompletableFuture.supplyAsync(() -> {
			List<TweetInteractionsEntryDto> entries = this.restClient.post()
				.uri("tweets/interactions")
				.body(tweetIds)
				.retrieve()
				.body(new ParameterizedTypeReference<List<TweetInteractionsEntryDto>>() {
				});
			// RestClient.body(...) is @Nullable; an empty response maps to no
			// interactions, matching the reactive stack's bodyToFlux→collectMap
			// (which yields an empty map on an empty body).
			if (entries == null) {
				return Map.of();
			}
			return entries.stream()
				.collect(Collectors.toMap(TweetInteractionsEntryDto::tweetId, TweetInteractionsEntryDto::toInteractions,
						(existing, replacement) -> replacement));
		}, this.httpClientExecutor);
	}

	public CompletableFuture<Map<UUID, TweetInteractionsDto>> getTweetInteractionsFallback(List<UUID> tweetIds,
			Throwable t) {
		return CompletableFuture.failedFuture(t);
	}

	@CircuitBreaker(name = "interactionClient", fallbackMethod = "getRepliesForTweetFallback")
	@Retry(name = "interactionClient")
	@Bulkhead(name = "interactionClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "interactionClient")
	public CompletableFuture<List<ReplyDto>> getRepliesForTweet(UUID tweetId) {
		return CompletableFuture.supplyAsync(() -> this.restClient.get()
			.uri("replies/tweet/{tweetId}", tweetId)
			.retrieve()
			.body(new ParameterizedTypeReference<List<ReplyDto>>() {
			}), this.httpClientExecutor);
	}

	public CompletableFuture<List<ReplyDto>> getRepliesForTweetFallback(UUID tweetId, Throwable t) {
		return CompletableFuture.failedFuture(t);
	}

	@CircuitBreaker(name = "interactionClient", fallbackMethod = "getFollowedIdsFallback")
	@Retry(name = "interactionClient")
	@Bulkhead(name = "interactionClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "interactionClient")
	public CompletableFuture<List<UUID>> getFollowedIds(UUID userId) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				String body = this.restClient.get()
					.uri("follows/{userId}/followers/identifiers", userId)
					.retrieve()
					.body(String.class);
				if (body == null) {
					throw new IllegalStateException("empty followed-ids response for user " + userId);
				}
				this.redisTemplate.opsForValue().set(FOLLOWED_CACHE + "::" + userId, body);
				return this.objectMapper.readValue(body, new TypeReference<List<UUID>>() {
				});
			}
			catch (IOException | RuntimeException ex) {
				log.error("failed to retrieve followed ids for user {}", userId, ex);
				throw new FollowRetrievingException("failed to retrieve followed ids for user " + userId, ex);
			}
		}, this.httpClientExecutor);
	}

	public CompletableFuture<List<UUID>> getFollowedIdsFallback(UUID userId, Throwable t) {
		return CompletableFuture.failedFuture(t);
	}

}
