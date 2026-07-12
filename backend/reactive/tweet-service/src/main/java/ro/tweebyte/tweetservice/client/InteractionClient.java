/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.client;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import ro.tweebyte.tweetservice.exception.FollowRetrievingException;
import ro.tweebyte.tweetservice.model.ReplyDto;
import ro.tweebyte.tweetservice.model.TweetInteractionsDto;
import ro.tweebyte.tweetservice.model.TweetInteractionsEntryDto;

@Slf4j
@Component
@RequiredArgsConstructor
public class InteractionClient {

	private static final String FOLLOWED_CACHE = "followed_cache";

	@Value("${app.interaction.base-url}")
	private String baseUrl;

	private final ReactiveRedisTemplate<String, Object> redisTemplate;

	private final WebClient.Builder webClientBuilder;

	private WebClient webClient = null;

	@PostConstruct
	public void init() {
		this.webClient = this.webClientBuilder.baseUrl(this.baseUrl).build();
	}

	@CircuitBreaker(name = "interactionClient", fallbackMethod = "getRepliesCountFallback")
	@Retry(name = "interactionClient")
	@Bulkhead(name = "interactionClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "interactionClient")
	public Mono<Long> getRepliesCount(UUID tweetId) {
		return this.webClient.get()
			.uri(uriBuilder -> uriBuilder.path("/replies/tweet/{tweetId}/count").build(tweetId.toString()))
			.retrieve()
			.bodyToMono(Long.class);
	}

	public Mono<Long> getRepliesCountFallback(UUID tweetId, Throwable t) {
		return Mono.error(t);
	}

	@CircuitBreaker(name = "interactionClient", fallbackMethod = "getLikesCountFallback")
	@Retry(name = "interactionClient")
	@Bulkhead(name = "interactionClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "interactionClient")
	public Mono<Long> getLikesCount(UUID tweetId) {
		return this.webClient.get()
			.uri(uriBuilder -> uriBuilder.path("/likes/{tweetId}/count").build(tweetId.toString()))
			.retrieve()
			.bodyToMono(Long.class);
	}

	public Mono<Long> getLikesCountFallback(UUID tweetId, Throwable t) {
		return Mono.error(t);
	}

	@CircuitBreaker(name = "interactionClient", fallbackMethod = "getRetweetsCountFallback")
	@Retry(name = "interactionClient")
	@Bulkhead(name = "interactionClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "interactionClient")
	public Mono<Long> getRetweetsCount(UUID tweetId) {
		return this.webClient.get()
			.uri(uriBuilder -> uriBuilder.path("/retweets/tweet/{tweetId}/count").build(tweetId.toString()))
			.retrieve()
			.bodyToMono(Long.class);
	}

	public Mono<Long> getRetweetsCountFallback(UUID tweetId, Throwable t) {
		return Mono.error(t);
	}

	@CircuitBreaker(name = "interactionClient", fallbackMethod = "getTopReplyFallback")
	@Retry(name = "interactionClient")
	@Bulkhead(name = "interactionClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "interactionClient")
	public Mono<ReplyDto> getTopReply(UUID tweetId) {
		return this.webClient.get()
			.uri(uriBuilder -> uriBuilder.path("/replies/tweet/{tweetId}/top").build(tweetId.toString()))
			.retrieve()
			.bodyToMono(ReplyDto.class);
	}

	public Mono<ReplyDto> getTopReplyFallback(UUID tweetId, Throwable t) {
		return Mono.error(t);
	}

	// Consolidated per-tweet enrichment: one POST resolves the like/reply/retweet counts and
	// top reply for a whole page. Same resilience envelope and error fallback as the single-id
	// calls above.
	@CircuitBreaker(name = "interactionClient", fallbackMethod = "getTweetInteractionsFallback")
	@Retry(name = "interactionClient")
	@Bulkhead(name = "interactionClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "interactionClient")
	public Mono<Map<UUID, TweetInteractionsDto>> getTweetInteractions(List<UUID> tweetIds) {
		return this.webClient.post()
			.uri("/tweets/interactions")
			.bodyValue(tweetIds)
			.retrieve()
			.bodyToFlux(TweetInteractionsEntryDto.class)
			.collectMap(TweetInteractionsEntryDto::tweetId, TweetInteractionsEntryDto::toInteractions);
	}

	public Mono<Map<UUID, TweetInteractionsDto>> getTweetInteractionsFallback(List<UUID> tweetIds, Throwable t) {
		return Mono.error(t);
	}

	@CircuitBreaker(name = "interactionClient", fallbackMethod = "getRepliesForTweetFallback")
	@Retry(name = "interactionClient")
	@Bulkhead(name = "interactionClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "interactionClient")
	public Flux<ReplyDto> getRepliesForTweet(UUID tweetId) {
		return this.webClient.get()
			.uri(uriBuilder -> uriBuilder.path("/replies/tweet/{tweetId}").build(tweetId.toString()))
			.retrieve()
			.bodyToFlux(ReplyDto.class);
	}

	public Flux<ReplyDto> getRepliesForTweetFallback(UUID tweetId, Throwable t) {
		return Flux.error(t);
	}

	@CircuitBreaker(name = "interactionClient", fallbackMethod = "getFollowedIdsFallback")
	@Retry(name = "interactionClient")
	@Bulkhead(name = "interactionClient", type = Bulkhead.Type.SEMAPHORE)
	@TimeLimiter(name = "interactionClient")
	public Flux<UUID> getFollowedIds(UUID userId) {
		String cacheKey = FOLLOWED_CACHE + "::" + userId;
		return this.webClient.get()
			.uri(uriBuilder -> uriBuilder.path("/follows/{userId}/followers/identifiers").build(userId.toString()))
			.retrieve()
			.bodyToMono(new ParameterizedTypeReference<List<UUID>>() {
			})
			.flatMap(followerIds -> this.redisTemplate.opsForValue().set(cacheKey, followerIds).thenReturn(followerIds))
			.flatMapMany(Flux::fromIterable)
			.doOnError(e -> log.error("failed to retrieve followed ids for user {}", userId, e))
			.onErrorMap(e -> !(e instanceof FollowRetrievingException),
					e -> new FollowRetrievingException("failed to retrieve followed ids for user " + userId, e));
	}

	public Flux<UUID> getFollowedIdsFallback(UUID userId, Throwable t) {
		return Flux.error(t);
	}

}
