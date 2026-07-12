/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

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
import ro.tweebyte.tweetservice.model.TweetRelation;
import ro.tweebyte.tweetservice.model.TweetRequest;
import ro.tweebyte.tweetservice.model.TweetSummaryDto;
import ro.tweebyte.tweetservice.model.TweetUpdateRequest;
import ro.tweebyte.tweetservice.model.UserDto;
import ro.tweebyte.tweetservice.repository.HashtagRepository;
import ro.tweebyte.tweetservice.repository.MentionRepository;
import ro.tweebyte.tweetservice.repository.TweetHashtagRepository;
import ro.tweebyte.tweetservice.repository.TweetRepository;
import ro.tweebyte.tweetservice.util.TweetTokenParser;

@Slf4j
@Service
@RequiredArgsConstructor
public class TweetService {

	private static final String FOLLOWED_CACHE = "followed_cache";

	// Default snapshot for a tweet absent from the consolidated interactions map: zero
	// counts and a null top reply (resolved to an empty ReplyDto at map time), matching the
	// shape the four-call enrichment produced for a tweet with no interactions.
	private static final TweetInteractionsDto EMPTY_INTERACTIONS = new TweetInteractionsDto(0L, 0L, 0L, null);

	// Deterministic page order shared by every paginated list query: newest first,
	// id as a stable tiebreaker so a given (page,size) always returns the same rows.
	private static final Sort PAGE_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

	private final TweetRepository tweetRepository;

	private final TweetMapper tweetMapper;

	private final UserService userService;

	private final MentionService mentionService;

	private final HashtagService hashtagService;

	private final InteractionClient interactionClient;

	private final HashtagRepository hashtagRepository;

	private final MentionRepository mentionRepository;

	private final TweetHashtagRepository tweetHashtagRepository;

	private final ReactiveRedisTemplate<String, Object> redisTemplate;

	private final org.springframework.beans.factory.ObjectProvider<Retry> tweetTokensRetry;

	private final TransactionalOperator txOperator;

	private final ObjectMapper objectMapper = new ObjectMapper();

	public Flux<UUID> getReferencedMediaIds() {
		return this.tweetRepository.findReferencedMediaIds();
	}

	@CircuitBreaker(name = "followedIdsCircuitBreaker", fallbackMethod = "getUserFeedWithCachedFollowed")
	public Flux<TweetDto> getUserFeed(UUID userId, int page, int size) {
		// Two-layer fallback, symmetric with async's getUserFeed:
		// 1. .onErrorResume — reactor-paradigm equivalent of async's
		// .exceptionally; always active, recovers getFollowedIds failures
		// from the Redis cache inline, matching the async fallback behavior.
		// 2. @CircuitBreaker(followedIdsCircuitBreaker) — broader guard for
		// downstream failures; inert under the benchmark profile via the
		// never-trip resilience4j.circuitbreaker.configs.default
		// (failureRateThreshold=100, minimumNumberOfCalls=Integer.MAX_VALUE).
		Pageable pageable = PageRequest.of(page, size, PAGE_SORT);
		Flux<TweetEntity> tweets = this.interactionClient.getFollowedIds(userId)
			.onErrorResume(e -> followedIdsFromCache(userId))
			.collectList()
			.flatMapMany(ids -> this.tweetRepository.findByUserIdIn(ids, pageable));
		return enrichTweetPage(tweets);
	}

	public Flux<TweetDto> getUserFeedWithCachedFollowed(UUID userId, int page, int size, Throwable t) {
		log.warn("getUserFeed circuit open for user {}; serving feed from followed-ids cache", userId, t);
		Pageable pageable = PageRequest.of(page, size, PAGE_SORT);
		Flux<TweetEntity> tweets = followedIdsFromCache(userId).collectList()
			.flatMapMany(ids -> this.tweetRepository.findByUserIdIn(ids, pageable));
		return enrichTweetPage(tweets);
	}

	// Shared cache read for both fallback layers, mirroring async's
	// getFollowedUsersFromCache: parse the followed-ids list from Redis and
	// recover to empty on any error (so the feed degrades to no followed users).
	private Flux<UUID> followedIdsFromCache(UUID userId) {
		String key = FOLLOWED_CACHE + "::" + userId;
		// The cache's value serializer (CacheConfiguration) is Jackson, so opsForValue().get
		// returns the already-deserialized List (as the InteractionClient writer stored it),
		// not a raw JSON String. convertValue retypes it to List<UUID> — mirrors UserService's
		// getUserSummary read. Recover to empty on any error so the feed degrades to no follows.
		return this.redisTemplate.opsForValue().get(key)
			.map(v -> this.objectMapper.convertValue(v, new TypeReference<List<UUID>>() {
			}))
			.flatMapMany(Flux::fromIterable)
			.onErrorResume(e -> Flux.empty());
	}

	// Batched enrichment for a whole page of tweets: collapses the per-tweet
	// N×6 fan-out into 3 calls total — 1 consolidated POST to interaction-service
	// (per-tweet like/reply/retweet counts + top reply, keyed by tweet id) and 2
	// local IN-list queries (hashtags, mentions). All run concurrently and join
	// via Mono.zip so the page assembles non-blocking. Counts/top-reply/relations
	// default to empty when a tweet has none, so every tweet maps to the same
	// shape the per-tweet path produced.
	private Flux<TweetDto> enrichTweetPage(Flux<TweetEntity> tweetsFlux) {
		return enrichTweetPage(tweetsFlux, true);
	}

	// enrich=false un-enriches the page: the consolidated interaction-service call is SKIPPED and
	// substituted with an empty interactions map, so every tweet resolves to zero counts and an
	// empty top reply via the getOrDefault(id, EMPTY_INTERACTIONS) below. The two local hashtag +
	// mention IN-list reads are unchanged. Used by the user-profile read, which resolves the
	// interactions itself in one combined call; the feed and tweets-get keep enrich=true.
	private Flux<TweetDto> enrichTweetPage(Flux<TweetEntity> tweetsFlux, boolean enrich) {
		return tweetsFlux.collectList().flatMapMany(tweets -> {
			if (tweets.isEmpty()) {
				return Flux.empty();
			}
			List<UUID> tweetIds = tweets.stream().map(TweetEntity::getId).toList();
			Mono<Map<UUID, TweetInteractionsDto>> interactionsMono = enrich
					? this.interactionClient.getTweetInteractions(tweetIds) : Mono.just(Map.of());
			// One combined hashtag+mention read (a UNION over the two relation tables) instead of two
			// separate IN-list queries — one r2dbc acquire for the page's relations rather than two.
			// The rows are split back per tweet by their kind discriminator.
			Mono<TweetRelations> relationsMono = this.hashtagRepository.findRelationsByTweetIdIn(tweetIds)
				.collectList()
				.map(TweetService::splitRelations);

			return Mono.zip(interactionsMono, relationsMono)
				.flatMapMany(data -> Flux.fromIterable(tweets).map(tweet -> {
					UUID id = tweet.getId();
					TweetInteractionsDto interaction = data.getT1().getOrDefault(id, EMPTY_INTERACTIONS);
					ReplyDto topReply = (interaction.getTopReply() != null) ? interaction.getTopReply()
							: new ReplyDto();
					List<HashtagEntity> hashtags = data.getT2().hashtags().getOrDefault(id, List.of());
					List<MentionEntity> mentions = data.getT2().mentions().getOrDefault(id, List.of());
					return this.tweetMapper.mapEntityToDto(tweet, interaction.getLikes(), interaction.getReplies(),
							interaction.getRetweets(), topReply, hashtags, mentions);
				}));
		});
	}

	// Split the combined relation rows into per-tweet hashtag and mention lists, keyed by tweet id.
	// 'H' rows rebuild HashtagEntity(id, text); 'M' rows rebuild MentionEntity(id, userId, text) —
	// the same shapes the two separate IN-list queries produced, in the UNION's row order.
	private static TweetRelations splitRelations(List<TweetRelation> rows) {
		Map<UUID, List<HashtagEntity>> hashtags = new HashMap<>();
		Map<UUID, List<MentionEntity>> mentions = new HashMap<>();
		for (TweetRelation row : rows) {
			if ("H".equals(row.kind())) {
				hashtags.computeIfAbsent(row.tweetId(), key -> new ArrayList<>())
					.add(HashtagEntity.builder().id(row.id()).text(row.text()).build());
			}
			else {
				mentions.computeIfAbsent(row.tweetId(), key -> new ArrayList<>())
					.add(MentionEntity.builder().id(row.id()).userId(row.userId()).text(row.text()).build());
			}
		}
		return new TweetRelations(hashtags, mentions);
	}

	private Mono<TweetDto> enrichSingleTweetDto(TweetEntity tweetEntity) {
		Mono<Long> likesMono = this.interactionClient.getLikesCount(tweetEntity.getId());
		Mono<Long> repliesMono = this.interactionClient.getRepliesCount(tweetEntity.getId());
		Mono<Long> retweetsMono = this.interactionClient.getRetweetsCount(tweetEntity.getId());
		Mono<List<ReplyDto>> repliesListMono = this.interactionClient.getRepliesForTweet(tweetEntity.getId())
			.collectList();
		Mono<List<HashtagEntity>> hashtagsMono = this.hashtagRepository.findHashtagsByTweetId(tweetEntity.getId())
			.collectList();
		Mono<List<MentionEntity>> mentionsMono = this.mentionRepository.findMentionsByTweetId(tweetEntity.getId())
			.collectList();

		return Mono.zip(likesMono, repliesMono, retweetsMono, repliesListMono, mentionsMono, hashtagsMono)
			.map(data -> this.tweetMapper.mapEntityToDto(tweetEntity, data.getT1(), data.getT2(), data.getT3(),
					data.getT4(), data.getT5(), data.getT6()));
	}

	public Mono<TweetDto> getTweet(UUID tweetId) {
		Mono<TweetEntity> tweetEntityMono = this.tweetRepository.findById(tweetId)
			.switchIfEmpty(Mono.error(new TweetNotFoundException("Tweet not found for id: " + tweetId)));

		return tweetEntityMono.flatMap(this::enrichSingleTweetDto);
	}

	// Unpaged per-user tweet summary feeding the interaction-service recommender:
	// every tweet id the user has posted, each with its hashtag/mention texts. The
	// ids drive scoring (likes + retweets aggregate over the whole set), so this
	// must NOT be capped by a page — a page-bounded summary scored only the first
	// page of tweets. Hashtags/mentions are resolved with the same batched IN-list
	// joins enrichTweetPage uses, so the whole feed costs 2 relation queries total.
	public Flux<TweetSummaryDto> getUserTweetsSummary(UUID userId) {
		return this.tweetRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId)
			.map(TweetEntity::getId)
			.collectList()
			.flatMapMany(tweetIds -> {
				if (tweetIds.isEmpty()) {
					return Flux.empty();
				}
				Mono<Map<UUID, Collection<String>>> hashtagsMono = this.hashtagRepository
					.findHashtagsByTweetIdIn(tweetIds)
					.collectMultimap(TweetHashtag::tweetId, TweetHashtag::text);
				Mono<Map<UUID, Collection<String>>> mentionsMono = this.mentionRepository.findByTweetIdIn(tweetIds)
					.collectMultimap(MentionEntity::getTweetId, MentionEntity::getText);
				return Mono.zip(hashtagsMono, mentionsMono)
					.flatMapMany(data -> Flux.fromIterable(tweetIds)
						.map(id -> new TweetSummaryDto(id, new ArrayList<>(data.getT1().getOrDefault(id, List.of())),
								new ArrayList<>(data.getT2().getOrDefault(id, List.of())))));
			});
	}

	public Mono<TweetDto> createTweet(TweetCreationRequest request) {
		return validateMediaIds(request.getMediaIds())
			.then(Mono.fromCallable(() -> this.tweetMapper.mapCreationRequestToEntity(request)))
			.flatMap(this.tweetRepository::save)
			.doOnSuccess(tweetEntity -> request.setId(tweetEntity.getId()))
			.flatMap(tweetEntity -> processTweetTokens(request, this.mentionService::handleTweetCreationMentions)
				.and(processTweetTokens(request, this.hashtagService::handleTweetCreationHashtags))
				.thenReturn(tweetEntity))
			.map(this.tweetMapper::mapEntityToCreationDto);
	}

	// Reject a create that references media ids the user-service does not know.
	// Empty/absent media_ids skip the round-trip. A missing asset is a client
	// error (400) carried by ResponseStatusException, which the
	// GlobalExceptionHandler maps to its status.
	private Mono<Void> validateMediaIds(UUID[] mediaIds) {
		if (mediaIds == null || mediaIds.length == 0) {
			return Mono.empty();
		}
		return Flux.fromArray(mediaIds)
			.flatMap(id -> this.userService.mediaExists(id)
				.flatMap(exists -> Boolean.TRUE.equals(exists) ? Mono.empty()
						: Mono.error(
								new ResponseStatusException(HttpStatus.BAD_REQUEST, "Media not found for id: " + id))))
			.then();
	}

	/**
	 * Rich tweet-update path used by {@code tweet-update} workload.
	 *
	 * <p>
	 * Steps, all explicit R2DBC operations (no JPA-style cascade magic):
	 * <ol>
	 * <li>{@code findWithRelationsByIdAndUserId} (1 SELECT, two {@code LEFT JOIN}s) — load
	 * the tweet (404 check) and its existing hashtags + mentions in one statement, the
	 * R2DBC stand-in for async's {@code @EntityGraph} load. The relations are the "before"
	 * side of the diff.</li>
	 * <li>{@code save} (1 UPDATE) — content + version bump</li>
	 * <li>Hashtag reconcile — {@code linkTweetToHashtagsCreatingMissing} resolves the
	 * newly-present texts with one {@code findByTextIn}, creates any missing hashtag rows in
	 * one multi-row INSERT, then reconciles the join table with one {@code replaceLinks}: a
	 * single data-modifying CTE that prunes the links whose text disappeared and adds the new
	 * links together (one round-trip) when there are both. Unchanged tags are left
	 * untouched.</li>
	 * <li>Mention reconcile — one {@code replaceMentions}: a single data-modifying CTE that
	 * drops the removed mentions and inserts the newly-present {@code @} tokens together,
	 * using ids resolved before the transaction (see {@link #resolveMentionUserIds}). The
	 * benchmark PUT flips its single trailing mention every request
	 * ({@code @benchmark_user_1} to {@code @benchmark_user_2}), so this runs on the hot path
	 * on every update (a combined delete+insert in one statement), symmetric with async —
	 * not a no-op.</li>
	 * </ol>
	 *
	 * <p>
	 * Both stacks read the existing relations and apply the same diff, so the eager load
	 * is load-bearing on each (it feeds the diff rather than being discarded). R2DBC has
	 * no {@code @EntityGraph}, so the load is one explicit join read. Mention usernames are
	 * resolved to ids <em>before</em> the transaction so the pooled connection is not held
	 * across the user-service round-trip; only the load + save + reconcile DML runs inside
	 * the reactive transaction boundary, so the diff stays atomic like async's
	 * {@code @Transactional} path.
	 * @param request the tweet-update payload carrying the new content and owner
	 * @return a completion signal that finishes once the tweet and its relations are
	 * saved
	 */
	public Mono<Void> updateTweet(TweetUpdateRequest request) {
		UUID tweetId = request.getId();
		String content = request.getContent();
		Set<String> hashtagTexts = TweetTokenParser.extractHashtags(content);
		Set<String> mentionUsernames = TweetTokenParser.extractMentions(content);

		// Resolve mention usernames -> ids OUTSIDE the transaction. The reconcile diff only
		// inserts the desired usernames not already present, but the existing set is known
		// only after the in-tx load, so every desired username is resolved up front.
		return resolveMentionUserIds(mentionUsernames)
			.flatMap(resolved -> this.tweetRepository.findWithRelationsByIdAndUserId(tweetId, request.getUserId())
				.switchIfEmpty(Mono.error(new TweetNotFoundException("Tweet not found for id " + tweetId)))
				.flatMap(loaded -> {
					TweetEntity tweetEntity = loaded.tweet();
					List<HashtagEntity> existingHashtags = loaded.hashtags();
					List<MentionEntity> existingMentions = loaded.mentions();
					return this.tweetRepository.save(this.tweetMapper.mapUpdateRequestToEntity(request, tweetEntity))
						.then(reconcileHashtags(tweetId, existingHashtags, hashtagTexts))
						.then(reconcileMentions(tweetId, existingMentions, mentionUsernames, resolved))
						.then();
				})
				.as(this.txOperator::transactional))
			.then();
	}

	/**
	 * Resolve each mention username to its user id via {@link UserService#getUserIdLive} (a
	 * live, uncached user-service read — write paths read live state, mirroring follow-create),
	 * collecting the successes into a {@code username -> userId} map. Run before the
	 * tweet-update transaction so the pooled DB connection is not held across the
	 * per-username user-service round-trip. An unknown username
	 * ({@link UserNotFoundException}) is silently dropped from the map (matching
	 * {@code MentionService.handleTweetUpdateMentions}); any other error propagates and
	 * fails the request.
	 * @param usernames the mention usernames parsed from the request content
	 * @return a map of the resolvable usernames to their user ids
	 */
	private Mono<Map<String, UUID>> resolveMentionUserIds(Set<String> usernames) {
		return Flux.fromIterable(usernames)
			.flatMap(u -> this.userService.getUserIdLive(u)
				.map(id -> Map.entry(u, id))
				.onErrorResume(e -> (e instanceof UserNotFoundException) ? Mono.empty() : Mono.error(e)))
			.collectMap(Map.Entry::getKey, Map.Entry::getValue);
	}

	/**
	 * Diff the tweet's existing hashtag links against the desired token set: delete the
	 * join row for each text no longer present, find-or-create + link each text not yet
	 * linked, leave unchanged links alone.
	 * @param tweetId the id of the tweet being reconciled
	 * @param existing the hashtag entities currently linked to the tweet
	 * @param desired the hashtag texts the tweet should be linked to after the update
	 * @return a completion signal that finishes once the links are reconciled
	 */
	private Mono<Void> reconcileHashtags(UUID tweetId, List<HashtagEntity> existing, Set<String> desired) {
		// Links whose text disappeared from the desired set are stale and must be pruned.
		List<UUID> staleHashtagIds = existing.stream()
			.filter(h -> !desired.contains(h.getText()))
			.map(HashtagEntity::getId)
			.toList();

		Set<String> existingTexts = existing.stream().map(HashtagEntity::getText).collect(Collectors.toSet());
		Set<String> toAdd = desired.stream()
			.filter(text -> !existingTexts.contains(text))
			.collect(Collectors.toSet());

		// When there are new links, the find-or-create path folds the stale-link prune into
		// the new-link insert (one CTE round-trip). With nothing to add, the prune is the
		// only DML, so issue it directly (a no-op when there is nothing stale either).
		return toAdd.isEmpty() ? this.tweetHashtagRepository.deleteLinks(tweetId, staleHashtagIds)
				: this.hashtagService.linkTweetToHashtagsCreatingMissing(tweetId, toAdd, staleHashtagIds);
	}

	/**
	 * Diff the tweet's existing mentions against the desired {@code @}-token set: delete
	 * each mention no longer present, insert each new one using the pre-resolved
	 * {@code username -> userId} map. Usernames that failed resolution (unknown user) are
	 * absent from the map and so silently skipped, matching
	 * {@code MentionService.handleTweetUpdateMentions}.
	 *
	 * <p>
	 * The diff is computed against {@code desired}: for a hashtag-only PUT it is empty,
	 * so every existing mention is removed and none added.
	 * @param tweetId the id of the tweet being reconciled
	 * @param existing the mention entities currently attached to the tweet
	 * @param desired the mention usernames the tweet should reference after the update
	 * @param resolved the {@code username -> userId} map resolved before the transaction
	 * @return a completion signal that finishes once the mentions are reconciled
	 */
	private Mono<Void> reconcileMentions(UUID tweetId, List<MentionEntity> existing, Set<String> desired,
			Map<String, UUID> resolved) {
		// Mentions whose text disappeared from the desired set are stale and must be removed.
		// Persistable.isNew() permits a null id, so the getter is nullable by contract; the
		// (unexpected) idless row is filtered out rather than risking an NPE / a null in the
		// IN list.
		List<UUID> staleMentionIds = existing.stream()
			.filter(m -> !desired.contains(m.getText()))
			.map(MentionEntity::getId)
			.filter(java.util.Objects::nonNull)
			.toList();

		Set<String> existingTexts = existing.stream().map(MentionEntity::getText).collect(Collectors.toSet());

		// Build the new rows from the pre-resolved map: a desired username not already
		// present and present in the map becomes one row. Unresolvable usernames are absent
		// from the map and so dropped. The id is client-generated — the mentions.id column
		// has no DB default (commit 6e31b41).
		List<MentionEntity> toInsert = desired.stream()
			.filter(u -> !existingTexts.contains(u))
			.filter(resolved::containsKey)
			.map(u -> MentionEntity.builder()
				.id(UUID.randomUUID())
				.userId(resolved.get(u))
				.text(u)
				.tweetId(tweetId)
				.isInsertable(true)
				.build())
			.toList();

		// Prune the stale rows and add the new ones in one statement (a CTE when both
		// directions have work, degrading to a single delete or insert otherwise).
		return this.mentionRepository.replaceMentions(staleMentionIds, toInsert);
	}

	public Mono<Void> deleteTweet(UUID userId, UUID tweetId) {
		// Owner-scoped: a non-owner (or missing tweet) yields 404 before any
		// delete runs, so one user can't delete another's tweet.
		// The find + relation-deletes + parent-delete run inside a single reactive
		// transaction (mirroring updateTweet) so a crash mid-way cannot leave
		// orphaned mention/hashtag rows without the tweet row.
		return this.tweetRepository.findByIdAndUserId(tweetId, userId)
			.switchIfEmpty(Mono.error(new TweetNotFoundException("Tweet not found for id " + tweetId)))
			.flatMap(tweet -> Mono
				.when(this.mentionRepository.deleteByTweetId(tweetId),
						this.tweetHashtagRepository.deleteByTweetId(tweetId))
				.then(this.tweetRepository.deleteById(tweetId)))
			.as(this.txOperator::transactional);
	}

	public Flux<TweetDto> searchTweets(String searchTerm, int page, int size) {
		return mapPageWithAuthors(this.tweetRepository.findBySimilarity(searchTerm, size, page * size));
	}

	public Flux<TweetDto> searchTweetsByHashtag(String searchTerm, int page, int size) {
		return mapPageWithAuthors(this.tweetRepository.findByHashtag(searchTerm, size, page * size));
	}

	// Resolve the page's tweet authors in ONE batched POST /users/summaries instead of one
	// GET per result (the former per-result N+1). The distinct author ids are collected across
	// the page, fetched in a single round-trip, and indexed by id; each tweet then maps against
	// that index in the page's original (repository) order. An author id absent from the batch
	// result (the endpoint omits unknown ids rather than 404ing) reproduces the per-call path's
	// not-found exactly: a UserNotFoundException with the same message, which fails the whole
	// search with 404 as the per-result getUserSummary(UUID) did. An empty page issues no call.
	private Flux<TweetDto> mapPageWithAuthors(Flux<TweetEntity> tweetsFlux) {
		return tweetsFlux.collectList().flatMapMany(tweets -> {
			if (tweets.isEmpty()) {
				return Flux.empty();
			}
			List<UUID> authorIds = tweets.stream().map(TweetEntity::getUserId).distinct().toList();
			return this.userService.getUserSummaries(authorIds).flatMapMany(users -> {
				Map<UUID, UserDto> usersById = users.stream()
					.collect(Collectors.toMap(UserDto::getId, Function.identity(), (first, second) -> first));
				return Flux.fromIterable(tweets).map(tweetEntity -> {
					UserDto userSummary = usersById.get(tweetEntity.getUserId());
					if (userSummary == null) {
						throw new UserNotFoundException("User not found for id: " + tweetEntity.getUserId());
					}
					return this.tweetMapper.mapEntityToDto(tweetEntity, userSummary);
				});
			});
		});
	}

	private Mono<Void> processTweetTokens(TweetRequest request, Function<TweetRequest, Mono<Void>> processor) {
		Mono<Void> chain = processor.apply(request);

		// Apply the retry operator only when the conditional Retry bean is registered
		// (app.resilience.retry.enabled=true, the default). In benchmark mode the bean
		// is not registered → no .retryWhen() added to the chain → tokenization runs
		// once on failure. The TweetNotFoundException filter is baked into the spec
		// inside RetryConfiguration (since the abstract Retry type doesn't expose
		// .filter()).
		Retry retrySpec = this.tweetTokensRetry.getIfAvailable();
		if (retrySpec != null) {
			chain = chain.retryWhen(retrySpec);
		}

		return chain
			.onErrorResume(throwable -> Mono.error(new TweetException("Tokenization failed for tweet: " + request)));
	}

	public Flux<TweetDto> getUserTweets(UUID userId, int page, int size, boolean enrich) {
		return enrichTweetPage(this.tweetRepository.findPageByUserId(userId, size, (long) page * size), enrich);
	}

	public Mono<TweetDto> getTweetSummary(UUID tweetId) {
		Mono<TweetEntity> tweetEntityMono = this.tweetRepository.findById(tweetId)
			.switchIfEmpty(Mono.error(new TweetNotFoundException("Tweet not found for id: " + tweetId)));

		return tweetEntityMono.map(this.tweetMapper::mapEntityToDto);
	}

	private record TweetRelations(Map<UUID, List<HashtagEntity>> hashtags,
			Map<UUID, List<MentionEntity>> mentions) {
	}

}
