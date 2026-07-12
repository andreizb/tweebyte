/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import ro.tweebyte.tweetservice.client.InteractionClient;
import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.exception.TweetException;
import ro.tweebyte.tweetservice.exception.TweetNotFoundException;
import ro.tweebyte.tweetservice.exception.UserNotFoundException;
import ro.tweebyte.tweetservice.mapper.TweetMapper;
import ro.tweebyte.tweetservice.model.ReplyDto;
import ro.tweebyte.tweetservice.model.TweetCreationRequest;
import ro.tweebyte.tweetservice.model.TweetDto;
import ro.tweebyte.tweetservice.model.TweetHashtag;
import ro.tweebyte.tweetservice.model.TweetInteractionsDto;
import ro.tweebyte.tweetservice.model.TweetMention;
import ro.tweebyte.tweetservice.model.TweetRelation;
import ro.tweebyte.tweetservice.model.TweetRequest;
import ro.tweebyte.tweetservice.model.TweetSummaryDto;
import ro.tweebyte.tweetservice.model.TweetUpdateRequest;
import ro.tweebyte.tweetservice.model.UserDto;
import ro.tweebyte.tweetservice.repository.HashtagRepository;
import ro.tweebyte.tweetservice.repository.MentionRepository;
import ro.tweebyte.tweetservice.repository.TweetRepository;
import ro.tweebyte.tweetservice.util.TweetTokenParser;

@Slf4j
@Service
@RequiredArgsConstructor
public class TweetService {

	private static final int MAX_RETRIES = 10;

	private static final String FOLLOWED_CACHE = "followed_cache";

	// Default snapshot for a tweet absent from the consolidated interactions map: zero
	// counts and a null top reply (resolved to an empty ReplyDto at map time), matching the
	// shape the four-call enrichment produced for a tweet with no interactions.
	private static final TweetInteractionsDto EMPTY_INTERACTIONS = new TweetInteractionsDto(0L, 0L, 0L, null);

	// Deterministic page order shared by every paginated list query: newest first,
	// id as a stable tiebreaker so a given (page,size) always returns the same rows.
	private static final Sort PAGE_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

	// Gate tokenization retries (mirrors reactive RetryConfiguration's
	// @ConditionalOnProperty app.resilience.retry.enabled, matchIfMissing=true).
	@Value("${app.resilience.retry.enabled:true}")
	private boolean tokenizationRetryEnabled;

	private final TweetRepository tweetRepository;

	private final TweetMapper tweetMapper;

	private final UserService userService;

	private final MentionService mentionService;

	private final HashtagService hashtagService;

	private final InteractionClient interactionClient;

	private final HashtagRepository hashtagRepository;

	private final MentionRepository mentionRepository;

	private final RedisTemplate<String, String> redisTemplate;

	private final ObjectMapper objectMapper;

	private final TweetUpdateService tweetUpdateService;

	@Qualifier("ioExecutor")
	private final ExecutorService executorService;

	public CompletableFuture<List<UUID>> getReferencedMediaIds() {
		return CompletableFuture.supplyAsync(this.tweetRepository::findReferencedMediaIds, this.executorService);
	}

	@CircuitBreaker(name = "followedIdsCircuitBreaker", fallbackMethod = "getUserFeedWithCachedFollowed")
	public CompletableFuture<List<TweetDto>> getUserFeed(UUID userId, int page, int size) {
		// Two-layer fallback, symmetric with reactive's getUserFeed:
		// 1. .exceptionally — async-paradigm equivalent of reactive's
		// .onErrorResume; always active, recovers getFollowedIds failures
		// from the Redis cache for followed-ids inline.
		// 2. @CircuitBreaker(followedIdsCircuitBreaker) — broader guard for
		// downstream (tweetRepository / enrichTweetsPage) failures; inert
		// under the benchmark profile via the never-trip
		// resilience4j.circuitbreaker.configs.default (failureRateThreshold=100,
		// minimumNumberOfCalls=Integer.MAX_VALUE).
		Pageable pageable = PageRequest.of(page, size, PAGE_SORT);
		return this.interactionClient.getFollowedIds(userId)
			.exceptionally(v -> getFollowedUsersFromCache(userId))
			.thenApply(ids -> this.tweetRepository.findByUserIdIn(ids, pageable))
			.thenCompose(this::enrichTweetsPage);
	}

	public CompletableFuture<List<TweetDto>> getUserFeedWithCachedFollowed(UUID userId, int page, int size,
			Throwable t) {
		log.warn("getUserFeed circuit open for user {}; serving feed from followed-ids cache", userId, t);
		List<UUID> followedIds = getFollowedUsersFromCache(userId);
		List<TweetEntity> tweetEntities = this.tweetRepository.findByUserIdIn(followedIds,
				PageRequest.of(page, size, PAGE_SORT));
		return enrichTweetsPage(tweetEntities);
	}

	private List<UUID> getFollowedUsersFromCache(UUID userId) {
		try {
			return this.objectMapper.readValue(this.redisTemplate.opsForValue().get(FOLLOWED_CACHE + "::" + userId),
					new TypeReference<List<UUID>>() {
					});
		}
		catch (IOException | RuntimeException ex) {
			return new ArrayList<>();
		}
	}

	public CompletableFuture<List<TweetDto>> getUserTweets(UUID userId, int page, int size, boolean enrich) {
		Pageable pageable = PageRequest.of(page, size, PAGE_SORT);
		return CompletableFuture.supplyAsync(() -> this.tweetRepository.findByUserId(userId, pageable), this.executorService)
			.thenCompose(tweets -> enrichTweetsPage(tweets, enrich));
	}

	// Batched enrichment for a whole page of tweets: collapses the per-tweet
	// N×6 fan-out into 3 calls total — 1 consolidated POST to interaction-service
	// (per-tweet like/reply/retweet counts + top reply, keyed by tweet id) and 2
	// local IN-list queries (hashtags, mentions). All run concurrently and join
	// via CompletableFuture.allOf so the page assembles non-blocking. Counts/
	// top-reply/relations default to empty when a tweet has none, so every tweet
	// maps to the same shape the per-tweet path produced.
	private CompletableFuture<List<TweetDto>> enrichTweetsPage(List<TweetEntity> tweetEntitiesPage) {
		return enrichTweetsPage(tweetEntitiesPage, true);
	}

	// enrich=false un-enriches the page: the consolidated interaction-service call is SKIPPED and
	// substituted with an empty interactions map, so every tweet resolves to zero counts and an
	// empty top reply via the getOrDefault(id, EMPTY_INTERACTIONS) below. The two local hashtag +
	// mention IN-list reads are unchanged. Used by the user-profile read, which resolves the
	// interactions itself in one combined call; the feed and tweets-get keep enrich=true.
	private CompletableFuture<List<TweetDto>> enrichTweetsPage(List<TweetEntity> tweetEntitiesPage, boolean enrich) {
		if (tweetEntitiesPage.isEmpty()) {
			return CompletableFuture.completedFuture(List.of());
		}
		List<UUID> tweetIds = tweetEntitiesPage.stream().map(TweetEntity::getId).toList();

		CompletableFuture<Map<UUID, TweetInteractionsDto>> interactionsFuture = enrich
				? this.interactionClient.getTweetInteractions(tweetIds) : CompletableFuture.completedFuture(Map.of());
		// One combined hashtag+mention read (a UNION over the two relation tables) on a single
		// executor task — one Hikari acquire for the page's relations rather than two. The rows are
		// split back per tweet by their kind discriminator.
		CompletableFuture<TweetRelations> relationsFuture = CompletableFuture
			.supplyAsync(() -> splitRelations(this.hashtagRepository.findRelationRowsByTweetIdIn(tweetIds)),
					this.executorService);

		return CompletableFuture.allOf(interactionsFuture, relationsFuture).thenApply(v -> {
			Map<UUID, TweetInteractionsDto> interactions = interactionsFuture.join();
			TweetRelations relations = relationsFuture.join();
			return tweetEntitiesPage.stream().map(tweet -> {
				UUID id = tweet.getId();
				TweetInteractionsDto interaction = interactions.getOrDefault(id, EMPTY_INTERACTIONS);
				ReplyDto topReply = (interaction.getTopReply() != null) ? interaction.getTopReply() : new ReplyDto();
				return this.tweetMapper.mapEntityToDto(tweet, interaction.getLikes(), interaction.getReplies(),
						interaction.getRetweets(), topReply, relations.hashtags().getOrDefault(id, List.of()),
						relations.mentions().getOrDefault(id, List.of()));
			}).toList();
		});
	}

	// Split the combined relation rows (tweet_id, kind, id, user_id, text) into per-tweet hashtag
	// and mention lists, keyed by tweet id. 'H' rows rebuild HashtagEntity(id, text); 'M' rows
	// rebuild MentionEntity(id, userId, text) — the same shapes the two separate queries produced,
	// in the UNION's row order.
	private static TweetRelations splitRelations(List<Object[]> rows) {
		Map<UUID, List<HashtagEntity>> hashtags = new HashMap<>();
		Map<UUID, List<MentionEntity>> mentions = new HashMap<>();
		for (Object[] row : rows) {
			TweetRelation relation = new TweetRelation((UUID) row[0], (String) row[1], (UUID) row[2], (UUID) row[3],
					(String) row[4]);
			if ("H".equals(relation.kind())) {
				hashtags.computeIfAbsent(relation.tweetId(), key -> new ArrayList<>())
					.add(HashtagEntity.builder().id(relation.id()).text(relation.text()).build());
			}
			else {
				mentions.computeIfAbsent(relation.tweetId(), key -> new ArrayList<>())
					.add(MentionEntity.builder()
						.id(relation.id())
						.userId(relation.userId())
						.text(relation.text())
						.build());
			}
		}
		return new TweetRelations(hashtags, mentions);
	}

	// Single-tweet enrichment: full replies list + hashtags + mentions.
	// Mirrors reactive TweetService.enrichSingleTweetDto (likes/repliesCount/
	// retweets/repliesList/mentions/hashtags → 7-arg mapper).
	private CompletableFuture<TweetDto> enrichSingleTweetDto(TweetEntity tweetEntity) {
		UUID tweetId = tweetEntity.getId();
		CompletableFuture<Long> likesFuture = this.interactionClient.getLikesCount(tweetId);
		CompletableFuture<Long> repliesCountFuture = this.interactionClient.getRepliesCount(tweetId);
		CompletableFuture<Long> retweetsFuture = this.interactionClient.getRetweetsCount(tweetId);
		CompletableFuture<List<ReplyDto>> repliesFuture = this.interactionClient.getRepliesForTweet(tweetId);
		CompletableFuture<List<HashtagEntity>> hashtagsFuture = CompletableFuture
			.supplyAsync(() -> this.hashtagRepository.findHashtagsByTweetId(tweetId), this.executorService);
		CompletableFuture<List<MentionEntity>> mentionsFuture = CompletableFuture
			.supplyAsync(() -> this.mentionRepository.findMentionsByTweetId(tweetId), this.executorService);

		return CompletableFuture
			.allOf(likesFuture, repliesCountFuture, retweetsFuture, repliesFuture, hashtagsFuture, mentionsFuture)
			.thenApply(
					v -> this.tweetMapper.mapEntityToDto(tweetEntity, likesFuture.join(), repliesCountFuture.join(),
							retweetsFuture.join(), repliesFuture.join(), mentionsFuture.join(), hashtagsFuture.join()));
	}

	public CompletableFuture<TweetDto> getTweet(UUID tweetId) {
		return CompletableFuture
			.supplyAsync(() -> this.tweetRepository.findById(tweetId)
				.orElseThrow(() -> new TweetNotFoundException("Tweet not found for id: " + tweetId)), this.executorService)
			.thenCompose(this::enrichSingleTweetDto);
	}

	public CompletableFuture<TweetDto> getTweetSummary(UUID tweetId) {
		return CompletableFuture
			.supplyAsync(() -> this.tweetRepository.findById(tweetId)
				.orElseThrow(() -> new TweetNotFoundException("Tweet not found for id: " + tweetId)), this.executorService)
			.thenApply(this.tweetMapper::mapEntityToDto);
	}

	// Unpaged per-user tweet summary feeding the interaction-service recommender:
	// every tweet id the user has posted, each with its hashtag/mention texts. The
	// ids drive scoring (likes + retweets aggregate over the whole set), so this
	// must NOT be capped by a page — a page-bounded summary scored only the first
	// page of tweets. Hashtags/mentions are resolved with the same batched IN-list
	// joins enrichTweetsPage uses, so the whole feed costs 2 relation queries total.
	public CompletableFuture<List<TweetSummaryDto>> getUserTweetsSummary(UUID userId) {
		return CompletableFuture.supplyAsync(() -> {
			List<TweetEntity> tweets = this.tweetRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId);
			if (tweets.isEmpty()) {
				return List.<TweetSummaryDto>of();
			}
			List<UUID> tweetIds = tweets.stream().map(TweetEntity::getId).toList();
			Map<UUID, List<String>> hashtags = this.hashtagRepository.findHashtagsByTweetIdIn(tweetIds)
				.stream()
				.collect(Collectors.groupingBy(TweetHashtag::tweetId,
						Collectors.mapping(TweetHashtag::text, Collectors.toList())));
			Map<UUID, List<String>> mentions = this.mentionRepository.findMentionsByTweetIdIn(tweetIds)
				.stream()
				.collect(Collectors.groupingBy(TweetMention::tweetId,
						Collectors.mapping(TweetMention::text, Collectors.toList())));
			return tweetIds.stream()
				.map(id -> new TweetSummaryDto(id, hashtags.getOrDefault(id, List.of()),
						mentions.getOrDefault(id, List.of())))
				.toList();
		}, this.executorService);
	}

	public CompletableFuture<TweetDto> createTweet(TweetCreationRequest request) {
		return validateMediaIds(request.getMediaIds()).thenCompose(ignored -> CompletableFuture.supplyAsync(
				() -> this.tweetRepository.save(this.tweetMapper.mapCreationRequestToEntity(request)),
				this.executorService))
			.thenApply(tweetEntity -> {
				request.setId(tweetEntity.getId());
				return tweetEntity;
			})
			// Wait for the mention and hashtag handlers to complete BEFORE the
			// outer chain returns its response, so an immediate edit on the same
			// tweet does not race their @Version-bump → 500
			// "Row was updated or deleted by another transaction". The reactive
			// stack composes the same handlers via Mono.when (`.and(...)`); this
			// thenComposeAsync + allOf gives the async stack the same waiting
			// semantics. Trade-off: createTweet's response p99 carries
			// max(mentions, hashtags) handler latency.
			.thenComposeAsync(tweetEntity -> {
				CompletableFuture<Void> mentions = CompletableFuture.runAsync(
						() -> processTweetTokens(request, this.mentionService::handleTweetCreationMentions),
						this.executorService);
				CompletableFuture<Void> hashtags = CompletableFuture.runAsync(
						() -> processTweetTokens(request, this.hashtagService::handleTweetCreationHashtags),
						this.executorService);
				return CompletableFuture.allOf(mentions, hashtags).thenApply(v -> tweetEntity);
			}, this.executorService)
			.thenApplyAsync(this.tweetMapper::mapEntityToCreationDto, this.executorService);
	}

	// Reject a create that references media ids the user-service does not know.
	// Empty/absent media_ids skip the round-trip. A missing asset is a client
	// error (400) carried by ResponseStatusException, which the
	// GlobalExceptionHandler maps to its status.
	private CompletableFuture<Void> validateMediaIds(UUID[] mediaIds) {
		if (mediaIds == null || mediaIds.length == 0) {
			return CompletableFuture.completedFuture(null);
		}
		CompletableFuture<?>[] checks = Arrays.stream(mediaIds)
			.map(id -> this.userService.mediaExists(id).thenAccept(exists -> {
				if (!Boolean.TRUE.equals(exists)) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Media not found for id: " + id);
				}
			}))
			.toArray(CompletableFuture[]::new);
		return CompletableFuture.allOf(checks);
	}

	public CompletableFuture<Void> updateTweet(TweetUpdateRequest request) {
		// Rich update path used by tweet-update workload: loads the tweet
		// via JpaRepository.findById (which carries @EntityGraph(mentions,
		// hashtags)) and lets Hibernate dirty-checking + collection-mutation
		// cascade drive the join-table DELETE/INSERT for tweet_hashtag plus
		// orphan-removal for mentions. The actual transactional work lives in
		// TweetUpdateService — @Transactional cannot be honoured directly on a
		// CompletableFuture-returning method because the supplyAsync lambda
		// runs on a separate thread without TX context. Invoking through the
		// proxied bean ensures the entity load, collection mutation, and flush
		// are all inside one transaction.
		//
		// Mention usernames are resolved to ids on the ioExecutor thread but
		// BEFORE the @Transactional bean call, so the pooled DB connection is
		// not held across the user-service round-trip. The resolution runs on
		// the same thread context getUserId().join() already ran on, so it adds
		// no new deadlock risk.
		return CompletableFuture.runAsync(() -> {
			Map<String, UUID> resolved = resolveMentionUserIds(request.getContent());
			this.tweetUpdateService.updateTweetWithRelations(request, resolved);
		}, this.executorService);
	}

	// Resolve each mention username parsed from the request content to its user id via the
	// live, uncached userService.getUserIdLive (write paths read live state, mirroring
	// follow-create), before the @Transactional update bean is invoked. Null-safe: null
	// content resolves to an empty map. An unknown username (UserNotFoundException) is
	// silently dropped from the map — matching reactive MentionService.handleTweetUpdateMentions;
	// any other error propagates and fails the request.
	//
	// All resolves are issued UP FRONT and joined once, so the per-username user-service
	// round-trips run concurrently rather than serially — mirroring reactive's
	// Flux.fromIterable(...).flatMap(getUserIdLive). For M distinct mentions this is ~1×RTT
	// instead of M×RTT under downstream latency.
	private Map<String, UUID> resolveMentionUserIds(String content) {
		Set<String> desired = (content != null) ? TweetTokenParser.extractMentions(content) : Set.of();
		if (desired.isEmpty()) {
			return Map.of();
		}
		// Issue every live resolve before joining any, so they are in flight concurrently.
		// Each handle() maps an unknown user (UserNotFoundException) to a null entry that
		// drops out of the map without failing the batch; any other error is re-raised so it
		// stays exceptional and propagates through allOf, exactly as the former serial loop's
		// drop/propagate semantics did.
		Map<String, CompletableFuture<Map.Entry<String, UUID>>> entryFutures = new HashMap<>();
		for (String username : desired) {
			entryFutures.put(username, this.userService.getUserIdLive(username).handle((id, ex) -> {
				if (ex == null) {
					return Map.entry(username, id);
				}
				Throwable cause = (ex instanceof CompletionException && ex.getCause() != null) ? ex.getCause() : ex;
				if (cause instanceof UserNotFoundException) {
					return null;
				}
				throw (cause instanceof RuntimeException runtime) ? runtime : new CompletionException(cause);
			}));
		}
		// Join once: propagates the first non-not-found failure, otherwise waits for all.
		CompletableFuture.allOf(entryFutures.values().toArray(new CompletableFuture[0])).join();
		Map<String, UUID> resolved = new HashMap<>();
		for (CompletableFuture<Map.Entry<String, UUID>> future : entryFutures.values()) {
			Map.Entry<String, UUID> entry = future.join();
			if (entry != null) {
				resolved.put(entry.getKey(), entry.getValue());
			}
		}
		return resolved;
	}

	public CompletableFuture<Void> deleteTweet(UUID userId, UUID tweetId) {
		// Delegate to the @Transactional, owner-scoped delete in TweetUpdateService.
		// @Transactional cannot be honoured on a CompletableFuture-returning method
		// (the runAsync lambda runs off the TX thread), so we invoke through the
		// proxied bean to keep the entity load + cascade delete in one transaction.
		return CompletableFuture.runAsync(() -> this.tweetUpdateService.deleteOwnedTweet(userId, tweetId),
				this.executorService);
	}

	public CompletableFuture<List<TweetDto>> searchTweets(String searchTerm, int page, int size) {
		return CompletableFuture
			.supplyAsync(() -> this.tweetRepository.findBySimilarity(searchTerm, size, page * size), this.executorService)
			.thenCompose(this::computeTweetsFromPage);
	}

	public CompletableFuture<List<TweetDto>> searchTweetsByHashtag(String searchTerm, int page, int size) {
		return CompletableFuture.supplyAsync(() -> this.tweetRepository.findByHashtag(searchTerm, size, page * size), this.executorService)
			.thenCompose(this::computeTweetsFromPage);
	}

	// Resolve the page's tweet authors in ONE batched POST /users/summaries instead of one
	// GET per result (the former per-result N+1). The distinct author ids are collected across
	// the page, fetched in a single round-trip, and indexed by id; each tweet then maps against
	// that index in the page's original order. An author id absent from the batch result (the
	// endpoint omits unknown ids rather than 404ing) reproduces the per-call path's not-found
	// exactly: a UserNotFoundException with the same message, which fails the whole search with
	// 404 as the per-result getUserSummary(UUID) did. An empty page issues no call.
	private CompletionStage<List<TweetDto>> computeTweetsFromPage(List<TweetEntity> page) {
		if (page.isEmpty()) {
			return CompletableFuture.completedFuture(List.of());
		}
		List<UUID> authorIds = page.stream().map(TweetEntity::getUserId).distinct().toList();
		return this.userService.getUserSummaries(authorIds).thenApply(users -> {
			Map<UUID, UserDto> usersById = users.stream()
				.collect(Collectors.toMap(UserDto::getId, Function.identity(), (first, second) -> first));
			return page.stream().map(tweetEntity -> {
				UserDto userSummary = usersById.get(tweetEntity.getUserId());
				if (userSummary == null) {
					throw new UserNotFoundException("User not found for id: " + tweetEntity.getUserId());
				}
				return this.tweetMapper.mapEntityToDto(tweetEntity, userSummary);
			}).toList();
		});
	}

	private void processTweetTokens(TweetRequest request, Consumer<TweetRequest> consumer) {
		// Reactive's Retry.fixedDelay(MAX_RETRIES) allows MAX_RETRIES retries on
		// top of the initial attempt = MAX_RETRIES + 1 total executions. Match
		// that total here so both stacks exhaust after the same attempt count.
		int maxAttempts = this.tokenizationRetryEnabled ? MAX_RETRIES + 1 : 1;
		int attempt = 0;
		boolean success = false;

		while (attempt < maxAttempts && !success) {
			try {
				consumer.accept(request);
				success = true;
			}
			catch (TweetNotFoundException ex) {
				// Match reactive: a not-found tweet during tokenization is a
				// terminal error (no retry) that fails createTweet as a
				// TweetException, rather than being silently swallowed. Reactive
				// excludes TweetNotFoundException from retry and its onErrorResume
				// maps it to TweetException -> 500.
				throw new TweetException("Tokenization failed for tweet: " + request);
			}
			catch (Exception ignored) {
				// Transient tokenization failure: swallow and let the loop retry up to
				// maxAttempts. Terminal exhaustion surfaces as the TweetException thrown
				// after the loop; TweetNotFoundException is handled above as non-retryable.
			}
			finally {
				attempt++;
			}
		}

		if (!success) {
			throw new TweetException("Tokenization failed for tweet: " + request);
		}
	}

	private record TweetRelations(Map<UUID, List<HashtagEntity>> hashtags,
			Map<UUID, List<MentionEntity>> mentions) {
	}

}
