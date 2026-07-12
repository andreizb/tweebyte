/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;

import ro.tweebyte.tweetservice.client.InteractionClient;
import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.exception.TweetException;
import ro.tweebyte.tweetservice.exception.UserNotFoundException;
import ro.tweebyte.tweetservice.mapper.TweetMapper;
import ro.tweebyte.tweetservice.model.TweetCreationRequest;
import ro.tweebyte.tweetservice.model.TweetDto;
import ro.tweebyte.tweetservice.model.TweetUpdateRequest;
import ro.tweebyte.tweetservice.repository.HashtagRepository;
import ro.tweebyte.tweetservice.repository.MentionRepository;
import ro.tweebyte.tweetservice.repository.TweetHashtagRepository;
import ro.tweebyte.tweetservice.repository.TweetRepository;
import ro.tweebyte.tweetservice.repository.TweetWithRelations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Branch-coverage companion to {@link TweetServiceTests}: drives the reactive
 * {@link TweetService} error/empty/diff paths the happy-path suite leaves uncovered —
 * media validation, hashtag/mention reconcile diffs, the cache-parse error fallback, the
 * tokenization retry/wrap operator, and the simple read pass-throughs.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TweetServiceBranchTests {

	@InjectMocks
	private TweetService tweetService;

	@Mock
	private TweetRepository tweetRepository;

	@Mock
	private TweetMapper tweetMapper;

	@Mock
	private UserService userService;

	@Mock
	private MentionService mentionService;

	@Mock
	private HashtagService hashtagService;

	@Mock
	private InteractionClient interactionClient;

	@Mock
	private HashtagRepository hashtagRepository;

	@Mock
	private MentionRepository mentionRepository;

	@Mock
	private TweetHashtagRepository tweetHashtagRepository;

	@Mock
	private ReactiveRedisTemplate<String, String> redisTemplate;

	@Mock
	private ReactiveValueOperations<String, String> reactiveValueOperations;

	@Mock
	private ObjectProvider<Retry> tweetTokensRetry;

	@Mock
	private TransactionalOperator txOperator;

	private UUID tweetId;

	private UUID userId;

	private TweetEntity tweetEntity;

	@BeforeEach
	void setUp() {
		this.tweetId = UUID.randomUUID();
		this.userId = UUID.randomUUID();
		this.tweetEntity = new TweetEntity();
		this.tweetEntity.setId(this.tweetId);
		this.tweetEntity.setUserId(this.userId);
		this.tweetEntity.setContent("Sample Tweet Content");
		this.tweetEntity.setCreatedAt(LocalDateTime.now());

		// updateTweet wraps only the load + save + reconcile Mono with the operator; stub
		// it as identity so the diff/error paths are observed unwrapped.
		given(this.txOperator.transactional(any(Mono.class))).willAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void getReferencedMediaIds_passesThroughRepository() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		given(this.tweetRepository.findReferencedMediaIds()).willReturn(Flux.just(a, b));

		StepVerifier.create(this.tweetService.getReferencedMediaIds()).expectNext(a, b).verifyComplete();
	}

	@Test
	void getUserTweets_passesThroughEnrichment() {
		given(this.tweetRepository.findPageByUserId(this.userId, 10, 0L)).willReturn(Flux.empty());

		StepVerifier.create(this.tweetService.getUserTweets(this.userId, 0, 10, true)).verifyComplete();
	}

	@Test
	void getUserTweets_unenriched_skipsInteractionsButKeepsRelationReads() {
		// enrich=false must NOT call interaction-service for the per-tweet interactions, but the
		// combined hashtag+mention relation read still runs. Every tweet resolves to zero counts /
		// empty top reply via the EMPTY_INTERACTIONS default.
		given(this.tweetRepository.findPageByUserId(this.userId, 10, 0L)).willReturn(Flux.just(this.tweetEntity));
		given(this.hashtagRepository.findRelationsByTweetIdIn(anyList())).willReturn(Flux.empty());
		given(this.tweetMapper.mapEntityToDto(any(), any(), any(), any(),
				any(ro.tweebyte.tweetservice.model.ReplyDto.class), anyList(), anyList()))
			.willReturn(new TweetDto());

		StepVerifier.create(this.tweetService.getUserTweets(this.userId, 0, 10, false))
			.expectNextCount(1)
			.verifyComplete();

		verify(this.interactionClient, never()).getTweetInteractions(anyList());
		verify(this.hashtagRepository).findRelationsByTweetIdIn(anyList());
	}

	@Test
	void searchTweets_mapsEachEntityWithUserSummary() {
		// The page's distinct authors are resolved in ONE batched getUserSummaries call; each
		// tweet maps against that index by author id, in page order.
		ro.tweebyte.tweetservice.model.UserDto user = new ro.tweebyte.tweetservice.model.UserDto();
		user.setId(this.userId);
		TweetDto dto = new TweetDto();
		given(this.tweetRepository.findBySimilarity(eq("foo"), anyInt(), anyInt()))
			.willReturn(Flux.just(this.tweetEntity));
		given(this.userService.getUserSummaries(List.of(this.userId))).willReturn(Mono.just(List.of(user)));
		given(this.tweetMapper.mapEntityToDto(this.tweetEntity, user)).willReturn(dto);

		StepVerifier.create(this.tweetService.searchTweets("foo", 0, 10)).expectNext(dto).verifyComplete();
		// One batched author call for the whole page, not one-per-result.
		verify(this.userService).getUserSummaries(List.of(this.userId));
	}

	@Test
	void searchTweets_missingAuthorFailsWithUserNotFound() {
		// An author id absent from the batch result (the endpoint omits unknown ids) reproduces
		// the per-call path's not-found: UserNotFoundException with the same message, failing the
		// whole search (mapped to 404), rather than nulling/omitting the author.
		given(this.tweetRepository.findBySimilarity(eq("foo"), anyInt(), anyInt()))
			.willReturn(Flux.just(this.tweetEntity));
		given(this.userService.getUserSummaries(List.of(this.userId))).willReturn(Mono.just(List.of()));

		StepVerifier.create(this.tweetService.searchTweets("foo", 0, 10))
			.expectErrorSatisfies(error -> org.assertj.core.api.Assertions.assertThat(error)
				.isInstanceOf(UserNotFoundException.class)
				.hasMessage("User not found for id: " + this.userId))
			.verify();
	}

	@Test
	void getUserTweetsSummary_emptyWhenNoTweets() {
		given(this.tweetRepository.findByUserIdOrderByCreatedAtDescIdDesc(this.userId)).willReturn(Flux.empty());

		StepVerifier.create(this.tweetService.getUserTweetsSummary(this.userId)).verifyComplete();
	}

	@Test
	void getUserFeed_emptyPageShortCircuitsEnrichment() {
		given(this.interactionClient.getFollowedIds(this.userId)).willReturn(Flux.just(UUID.randomUUID()));
		given(this.tweetRepository.findByUserIdIn(any(), any(Pageable.class))).willReturn(Flux.empty());

		StepVerifier.create(this.tweetService.getUserFeed(this.userId, 0, 10)).verifyComplete();
		verify(this.interactionClient, never()).getTweetInteractions(any());
	}

	@Test
	void getUserFeed_cacheParseFailureDegradesToEmptyFollowed() {
		// getFollowedIds fails → onErrorResume reads cache; the cached JSON is
		// malformed, so the map() throws TweetException which the inner
		// onErrorResume swallows to an empty followed-ids list (empty feed).
		String cacheKey = "followed_cache::" + this.userId;
		given(this.interactionClient.getFollowedIds(this.userId))
			.willReturn(Flux.error(new RuntimeException("downstream")));
		given(this.redisTemplate.opsForValue()).willReturn(this.reactiveValueOperations);
		given(this.reactiveValueOperations.get(cacheKey)).willReturn(Mono.just("not-json"));
		given(this.tweetRepository.findByUserIdIn(any(), any(Pageable.class))).willReturn(Flux.empty());

		StepVerifier.create(this.tweetService.getUserFeed(this.userId, 0, 10)).verifyComplete();
	}

	@Test
	void createTweet_validatesMediaIds_rejectsMissingMedia() {
		UUID mediaId = UUID.randomUUID();
		TweetCreationRequest request = new TweetCreationRequest();
		request.setMediaIds(new UUID[] { mediaId });
		given(this.userService.mediaExists(mediaId)).willReturn(Mono.just(false));

		StepVerifier.create(this.tweetService.createTweet(request))
			.expectError(ResponseStatusException.class)
			.verify();
	}

	@Test
	void createTweet_validatesMediaIds_passesWhenAllExist() {
		UUID mediaId = UUID.randomUUID();
		TweetCreationRequest request = new TweetCreationRequest();
		request.setMediaIds(new UUID[] { mediaId });
		given(this.userService.mediaExists(mediaId)).willReturn(Mono.just(true));
		given(this.tweetMapper.mapCreationRequestToEntity(request)).willReturn(this.tweetEntity);
		given(this.tweetRepository.save(any())).willReturn(Mono.just(this.tweetEntity));
		given(this.mentionService.handleTweetCreationMentions(request)).willReturn(Mono.empty());
		given(this.hashtagService.handleTweetCreationHashtags(request)).willReturn(Mono.empty());
		given(this.tweetMapper.mapEntityToCreationDto(any())).willReturn(new TweetDto());

		StepVerifier.create(this.tweetService.createTweet(request)).expectNextCount(1).verifyComplete();
	}

	@Test
	void createTweet_tokenizationFailureRetriesThenWrapsAsTweetException() {
		// A registered Retry bean adds .retryWhen() to the tokenization chain; the
		// handler keeps failing, so after retries exhaust the onErrorResume maps the
		// error to a TweetException. Exercises both the retry branch and the wrap.
		TweetCreationRequest request = new TweetCreationRequest();
		given(this.tweetMapper.mapCreationRequestToEntity(request)).willReturn(this.tweetEntity);
		given(this.tweetRepository.save(any())).willReturn(Mono.just(this.tweetEntity));
		given(this.tweetTokensRetry.getIfAvailable()).willReturn(Retry.max(2));
		given(this.mentionService.handleTweetCreationMentions(request))
			.willReturn(Mono.error(new RuntimeException("flaky")));
		given(this.hashtagService.handleTweetCreationHashtags(request)).willReturn(Mono.empty());

		StepVerifier.create(this.tweetService.createTweet(request)).expectError(TweetException.class).verify();

		// The handler Mono is produced once and re-subscribed by retryWhen; the
		// terminal error after exhaustion is wrapped as a TweetException.
		verify(this.mentionService).handleTweetCreationMentions(request);
	}

	@Test
	void updateTweet_removesStaleHashtagAndMentionLinks() {
		// Existing relations whose text disappears from the new content are removed: with
		// no new tokens to add, the hashtag prune is a standalone deleteLinks and the
		// mention reconcile is a replaceMentions carrying only the stale id (empty insert).
		TweetUpdateRequest request = new TweetUpdateRequest();
		request.setId(this.tweetId);
		request.setUserId(this.userId);
		request.setContent("plain content no tokens");

		HashtagEntity staleTag = HashtagEntity.builder().id(UUID.randomUUID()).text("old").build();
		MentionEntity staleMention = new MentionEntity();
		staleMention.setId(UUID.randomUUID());
		staleMention.setText("ghost");

		given(this.tweetRepository.findWithRelationsByIdAndUserId(this.tweetId, this.userId))
			.willReturn(Mono.just(new TweetWithRelations(this.tweetEntity, List.of(staleTag), List.of(staleMention))));
		given(this.tweetMapper.mapUpdateRequestToEntity(request, this.tweetEntity)).willReturn(this.tweetEntity);
		given(this.tweetRepository.save(any())).willReturn(Mono.just(this.tweetEntity));
		given(this.tweetHashtagRepository.deleteLinks(this.tweetId, List.of(staleTag.getId())))
			.willReturn(Mono.empty());
		given(this.mentionRepository.replaceMentions(List.of(staleMention.getId()), List.of()))
			.willReturn(Mono.empty());

		StepVerifier.create(this.tweetService.updateTweet(request)).verifyComplete();

		verify(this.tweetHashtagRepository).deleteLinks(this.tweetId, List.of(staleTag.getId()));
		verify(this.mentionRepository).replaceMentions(List.of(staleMention.getId()), List.of());
	}

	@Test
	void updateTweet_skipsIdlessStaleMention() {
		// A stale mention with a null id is filtered out of the delete id list (the
		// Objects::nonNull filter), so replaceMentions receives an empty stale list (no-op
		// delete) — an idless row is never sent as a null in the IN clause.
		TweetUpdateRequest request = new TweetUpdateRequest();
		request.setId(this.tweetId);
		request.setUserId(this.userId);
		request.setContent("plain content");

		MentionEntity idless = new MentionEntity();
		idless.setText("ghost");

		given(this.tweetRepository.findWithRelationsByIdAndUserId(this.tweetId, this.userId))
			.willReturn(Mono.just(new TweetWithRelations(this.tweetEntity, List.of(), List.of(idless))));
		given(this.tweetMapper.mapUpdateRequestToEntity(request, this.tweetEntity)).willReturn(this.tweetEntity);
		given(this.tweetRepository.save(any())).willReturn(Mono.just(this.tweetEntity));
		given(this.tweetHashtagRepository.deleteLinks(any(), any())).willReturn(Mono.empty());
		given(this.mentionRepository.replaceMentions(anyList(), anyList())).willReturn(Mono.empty());

		StepVerifier.create(this.tweetService.updateTweet(request)).verifyComplete();

		verify(this.mentionRepository).replaceMentions(List.of(), List.of());
	}

	@Test
	void updateTweet_skipsUnknownMentionUserButPropagatesOtherErrors() {
		// UserNotFoundException during mention resolution is swallowed (Mono.empty);
		// the addition for that username is simply dropped, so the reconcile's insert side
		// is empty (no row inserted) without failing the update.
		TweetUpdateRequest request = new TweetUpdateRequest();
		request.setId(this.tweetId);
		request.setUserId(this.userId);
		request.setContent("hi @ghost");

		given(this.tweetRepository.findWithRelationsByIdAndUserId(this.tweetId, this.userId))
			.willReturn(Mono.just(new TweetWithRelations(this.tweetEntity, List.of(), List.of())));
		given(this.tweetMapper.mapUpdateRequestToEntity(request, this.tweetEntity)).willReturn(this.tweetEntity);
		given(this.tweetRepository.save(any())).willReturn(Mono.just(this.tweetEntity));
		given(this.tweetHashtagRepository.deleteLinks(any(), any())).willReturn(Mono.empty());
		given(this.mentionRepository.replaceMentions(anyList(), anyList())).willReturn(Mono.empty());
		given(this.userService.getUserIdLive("ghost")).willReturn(Mono.error(new UserNotFoundException("ghost")));

		StepVerifier.create(this.tweetService.updateTweet(request)).verifyComplete();

		verify(this.mentionRepository).replaceMentions(List.of(), List.of());
	}

	@Test
	void updateTweet_mentionResolutionNonNotFoundErrorPropagates() {
		// A non-UserNotFoundException during resolution propagates and fails the update.
		TweetUpdateRequest request = new TweetUpdateRequest();
		request.setId(this.tweetId);
		request.setUserId(this.userId);
		request.setContent("hi @alice");

		given(this.tweetRepository.findWithRelationsByIdAndUserId(this.tweetId, this.userId))
			.willReturn(Mono.just(new TweetWithRelations(this.tweetEntity, List.of(), List.of())));
		given(this.tweetMapper.mapUpdateRequestToEntity(request, this.tweetEntity)).willReturn(this.tweetEntity);
		given(this.tweetRepository.save(any())).willReturn(Mono.just(this.tweetEntity));
		given(this.tweetHashtagRepository.deleteLinks(any(), any())).willReturn(Mono.empty());
		given(this.mentionRepository.replaceMentions(anyList(), anyList())).willReturn(Mono.empty());
		given(this.userService.getUserIdLive("alice")).willReturn(Mono.error(new IllegalStateException("user-svc down")));

		StepVerifier.create(this.tweetService.updateTweet(request)).expectError(IllegalStateException.class).verify();
	}

	@Test
	void deleteTweet_notFoundPropagatesException() {
		given(this.tweetRepository.findByIdAndUserId(this.tweetId, this.userId)).willReturn(Mono.empty());

		StepVerifier.create(this.tweetService.deleteTweet(this.userId, this.tweetId))
			.expectError(ro.tweebyte.tweetservice.exception.TweetNotFoundException.class)
			.verify();
	}

	@Test
	void getTweetSummary_notFoundPropagatesException() {
		given(this.tweetRepository.findById(this.tweetId)).willReturn(Mono.empty());

		StepVerifier.create(this.tweetService.getTweetSummary(this.tweetId))
			.expectError(ro.tweebyte.tweetservice.exception.TweetNotFoundException.class)
			.verify();
	}

	@Test
	void searchTweets_emptyWhenNoMatches() {
		given(this.tweetRepository.findBySimilarity(any(), anyInt(), anyInt())).willReturn(Flux.empty());

		StepVerifier.create(this.tweetService.searchTweets("none", 0, 10)).verifyComplete();
	}

	@Test
	void getUserTweetsSummary_dropsTweetsWithNoRelations() {
		// Non-empty tweets but no hashtags/mentions → getOrDefault(empty) on both
		// multimaps, exercising the populated-path with default-empty relations.
		given(this.tweetRepository.findByUserIdOrderByCreatedAtDescIdDesc(this.userId))
			.willReturn(Flux.just(this.tweetEntity));
		given(this.hashtagRepository.findHashtagsByTweetIdIn(any())).willReturn(Flux.empty());
		given(this.mentionRepository.findByTweetIdIn(any())).willReturn(Flux.empty());

		StepVerifier.create(this.tweetService.getUserTweetsSummary(this.userId))
			.expectNextMatches(s -> s.getId().equals(this.tweetId) && s.getHashtags().isEmpty()
					&& s.getMentions().isEmpty())
			.verifyComplete();
	}

}
