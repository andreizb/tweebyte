/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import ro.tweebyte.tweetservice.client.InteractionClient;
import ro.tweebyte.tweetservice.client.UserClient;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.exception.TweetException;
import ro.tweebyte.tweetservice.exception.TweetNotFoundException;
import ro.tweebyte.tweetservice.exception.UserNotFoundException;
import ro.tweebyte.tweetservice.mapper.TweetMapper;
import ro.tweebyte.tweetservice.model.ReplyDto;
import ro.tweebyte.tweetservice.model.TweetCreationRequest;
import ro.tweebyte.tweetservice.model.TweetDto;
import ro.tweebyte.tweetservice.model.TweetSummaryDto;
import ro.tweebyte.tweetservice.model.TweetUpdateRequest;
import ro.tweebyte.tweetservice.model.UserDto;
import ro.tweebyte.tweetservice.repository.HashtagRepository;
import ro.tweebyte.tweetservice.repository.MentionRepository;
import ro.tweebyte.tweetservice.repository.TweetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class TweetServiceTests {

	@Mock
	private InteractionClient interactionClient;

	@Mock
	private UserClient userClient;

	@Mock
	private TweetRepository tweetRepository;

	@Mock
	private HashtagRepository hashtagRepository;

	@Mock
	private MentionRepository mentionRepository;

	@Mock
	private TweetMapper tweetMapper;

	@InjectMocks
	private UserService userService;

	@Mock
	private HashtagService hashtagService;

	@Mock
	private MentionService mentionService;

	@Mock
	private RedisTemplate<String, String> redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@Mock
	private ObjectMapper objectMapper;

	@Mock
	private TweetUpdateService tweetUpdateService;

	@InjectMocks
	private TweetService tweetService;

	private final ExecutorService executorService = Executors.newFixedThreadPool(4);

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(this.tweetService, "executorService", this.executorService);
		ReflectionTestUtils.setField(this.tweetService, "tokenizationRetryEnabled", true);
		given(this.redisTemplate.opsForValue()).willReturn(this.valueOperations);
	}

	@Test
	void testGetUserFeed() {
		UUID userId = UUID.randomUUID();
		List<UUID> followedIds = Arrays.asList(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
		given(this.interactionClient.getFollowedIds(userId)).willReturn(CompletableFuture.completedFuture(followedIds));

		TweetEntity tweetEntity1 = new TweetEntity();
		tweetEntity1.setId(UUID.randomUUID());

		TweetEntity tweetEntity2 = new TweetEntity();
		tweetEntity2.setId(UUID.randomUUID());

		List<TweetEntity> tweetEntities = Arrays.asList(tweetEntity1, tweetEntity2);
		given(this.tweetRepository.findByUserIdIn(eq(followedIds), any(Pageable.class))).willReturn(tweetEntities);

		stubBatchEnrichment();

		given(this.tweetMapper.mapEntityToDto(any(), anyLong(), anyLong(), anyLong(), any(ReplyDto.class), anyList(),
				anyList()))
			.willReturn(new TweetDto());

		CompletableFuture<List<TweetDto>> result = this.tweetService.getUserFeed(userId, 0, 10);

		assertThat(result.join()).hasSize(2);
		verify(this.interactionClient).getFollowedIds(userId);
		verify(this.tweetRepository).findByUserIdIn(eq(followedIds), any(Pageable.class));
		verify(this.hashtagRepository).findRelationRowsByTweetIdIn(anyList());
		verify(this.tweetMapper, times(2)).mapEntityToDto(any(), anyLong(), anyLong(), anyLong(), any(ReplyDto.class),
				anyList(), anyList());
	}

	// Stubs the batched page enrichment fan-out (1 consolidated interactions call +
	// 1 combined relation read) mirroring TweetService.enrichTweetsPage. An empty
	// interactions map/relations are enough: the mapper is stubbed to return a fresh
	// TweetDto, so the test asserts page assembly, not per-field values.
	private void stubBatchEnrichment() {
		given(this.interactionClient.getTweetInteractions(anyList()))
			.willReturn(CompletableFuture.completedFuture(Map.of()));
		given(this.hashtagRepository.findRelationRowsByTweetIdIn(anyList())).willReturn(List.of());
	}

	@Test
	void testGetUserFeedFallbackToCache() throws Exception {
		UUID userId = UUID.randomUUID();
		List<UUID> followedIdsFromCache = Arrays.asList(UUID.randomUUID(), UUID.randomUUID());

		given(this.interactionClient.getFollowedIds(userId))
			.willReturn(CompletableFuture.failedFuture(new RuntimeException("Service error")));

		String redisCacheKey = "followed_cache::" + userId;
		given(this.valueOperations.get(redisCacheKey))
			.willReturn("[\"" + followedIdsFromCache.get(0) + "\", \"" + followedIdsFromCache.get(1) + "\"]");
		given(this.objectMapper.readValue(anyString(), any(TypeReference.class))).willReturn(followedIdsFromCache);

		TweetEntity tweetEntity1 = new TweetEntity();
		tweetEntity1.setId(UUID.randomUUID());

		TweetEntity tweetEntity2 = new TweetEntity();
		tweetEntity2.setId(UUID.randomUUID());

		List<TweetEntity> tweetEntities = Arrays.asList(tweetEntity1, tweetEntity2);
		given(this.tweetRepository.findByUserIdIn(eq(followedIdsFromCache), any(Pageable.class)))
			.willReturn(tweetEntities);

		stubBatchEnrichment();

		given(this.tweetMapper.mapEntityToDto(any(), anyLong(), anyLong(), anyLong(), any(ReplyDto.class), anyList(),
				anyList()))
			.willReturn(new TweetDto());

		CompletableFuture<List<TweetDto>> result = this.tweetService.getUserFeed(userId, 0, 10);

		assertThat(result.join()).hasSize(2);
		verify(this.redisTemplate).opsForValue();
		verify(this.valueOperations).get(redisCacheKey);
		verify(this.objectMapper).readValue(anyString(), any(TypeReference.class));
		verify(this.tweetRepository).findByUserIdIn(eq(followedIdsFromCache), any(Pageable.class));
		verify(this.tweetMapper, times(2)).mapEntityToDto(any(), anyLong(), anyLong(), anyLong(), any(ReplyDto.class),
				anyList(), anyList());
	}

	@Test
	void testGetUserTweets() {
		UUID userId = UUID.randomUUID();

		TweetEntity tweetEntity1 = new TweetEntity();
		tweetEntity1.setId(UUID.randomUUID());

		TweetEntity tweetEntity2 = new TweetEntity();
		tweetEntity2.setId(UUID.randomUUID());

		List<TweetEntity> tweetEntities = Arrays.asList(tweetEntity1, tweetEntity2);
		given(this.tweetRepository.findByUserId(eq(userId), any(Pageable.class))).willReturn(tweetEntities);

		stubBatchEnrichment();

		given(this.tweetMapper.mapEntityToDto(any(), anyLong(), anyLong(), anyLong(), any(ReplyDto.class), anyList(),
				anyList()))
			.willReturn(new TweetDto());

		CompletableFuture<List<TweetDto>> result = this.tweetService.getUserTweets(userId, 0, 10, true);

		assertThat(result.join()).hasSize(2);
		verify(this.tweetRepository).findByUserId(eq(userId), any(Pageable.class));
		verify(this.hashtagRepository).findRelationRowsByTweetIdIn(anyList());
		verify(this.tweetMapper, times(2)).mapEntityToDto(any(), anyLong(), anyLong(), anyLong(), any(ReplyDto.class),
				anyList(), anyList());
	}

	@Test
	void testGetUserTweetsUnenrichedSkipsInteractions() {
		UUID userId = UUID.randomUUID();

		TweetEntity tweetEntity1 = new TweetEntity();
		tweetEntity1.setId(UUID.randomUUID());

		TweetEntity tweetEntity2 = new TweetEntity();
		tweetEntity2.setId(UUID.randomUUID());

		List<TweetEntity> tweetEntities = Arrays.asList(tweetEntity1, tweetEntity2);
		given(this.tweetRepository.findByUserId(eq(userId), any(Pageable.class))).willReturn(tweetEntities);

		// enrich=false must NOT call interaction-service for the per-tweet interactions, but the
		// combined hashtag+mention relation read still runs. Every tweet resolves to zero counts /
		// empty top reply via the EMPTY_INTERACTIONS default.
		given(this.hashtagRepository.findRelationRowsByTweetIdIn(anyList())).willReturn(List.of());
		given(this.tweetMapper.mapEntityToDto(any(), anyLong(), anyLong(), anyLong(), any(ReplyDto.class), anyList(),
				anyList()))
			.willReturn(new TweetDto());

		CompletableFuture<List<TweetDto>> result = this.tweetService.getUserTweets(userId, 0, 10, false);

		assertThat(result.join()).hasSize(2);
		verify(this.interactionClient, never()).getTweetInteractions(anyList());
		verify(this.hashtagRepository).findRelationRowsByTweetIdIn(anyList());
		verify(this.tweetMapper, times(2)).mapEntityToDto(any(), anyLong(), anyLong(), anyLong(), any(ReplyDto.class),
				anyList(), anyList());
	}

	@Test
	void testGetTweet() {
		UUID tweetId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		ReplyDto replyDto = new ReplyDto();
		replyDto.setUserId(userId);
		replyDto.setContent("test comment");

		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setId(tweetId);
		tweetEntity.setUserId(userId);
		given(this.tweetRepository.findById(tweetId)).willReturn(Optional.of(tweetEntity));

		given(this.interactionClient.getLikesCount(tweetEntity.getId()))
			.willReturn(CompletableFuture.completedFuture(0L));
		given(this.interactionClient.getRepliesCount(tweetEntity.getId()))
			.willReturn(CompletableFuture.completedFuture(0L));
		given(this.interactionClient.getRetweetsCount(tweetEntity.getId()))
			.willReturn(CompletableFuture.completedFuture(0L));
		given(this.interactionClient.getRepliesForTweet(tweetEntity.getId()))
			.willReturn(CompletableFuture.completedFuture(List.of(replyDto)));
		given(this.hashtagRepository.findHashtagsByTweetId(tweetEntity.getId())).willReturn(List.of());
		given(this.mentionRepository.findMentionsByTweetId(tweetEntity.getId())).willReturn(List.of());

		TweetDto tweetDto = new TweetDto();
		given(this.tweetMapper.mapEntityToDto(any(TweetEntity.class), any(), any(), any(), any(List.class), anyList(),
				anyList()))
			.willReturn(tweetDto);

		CompletableFuture<TweetDto> result = this.tweetService.getTweet(tweetId);

		assertThat(result.join()).isEqualTo(tweetDto);
		verify(this.hashtagRepository).findHashtagsByTweetId(tweetEntity.getId());
		verify(this.mentionRepository).findMentionsByTweetId(tweetEntity.getId());
	}

	@Test
	void testCreateTweet() {
		TweetCreationRequest request = new TweetCreationRequest();
		UUID tweetId = UUID.randomUUID();

		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setId(tweetId);

		given(this.tweetMapper.mapCreationRequestToEntity(any())).willReturn(tweetEntity);
		given(this.tweetRepository.save(any())).willReturn(new TweetEntity());
		given(this.tweetMapper.mapEntityToCreationDto(any())).willReturn(new TweetDto());

		willDoNothing().given(this.mentionService).handleTweetCreationMentions(request);
		willDoNothing().given(this.hashtagService).handleTweetCreationHashtags(request);

		CompletableFuture<TweetDto> result = this.tweetService.createTweet(request);

		assertThat(result).isNotNull();
		assertThatCode(() -> result.get()).doesNotThrowAnyException();
		assertThat(result.isDone()).isTrue();
		assertThat(result.isCompletedExceptionally()).isFalse();
		assertThat(result.isCancelled()).isFalse();
	}

	@Test
	void testUpdateTweet() {
		// updateTweet delegates the rich semantics (entity-graph load, content
		// update, hashtag/mention rebuild) to
		// TweetUpdateService.updateTweetWithRelations.
		// This test verifies the delegation; TweetUpdateServiceTest covers the
		// relation-rebuild semantics.
		UUID userId = UUID.randomUUID();
		UUID tweetId = UUID.randomUUID();
		TweetUpdateRequest request = new TweetUpdateRequest();
		request.setId(tweetId);
		request.setUserId(userId);

		// Null content → resolveMentionUserIds yields an empty map, passed through to the
		// transactional bean as the second arg.
		willDoNothing().given(this.tweetUpdateService).updateTweetWithRelations(eq(request), anyMap());

		CompletableFuture<Void> result = this.tweetService.updateTweet(request);

		assertThatCode(() -> result.get()).doesNotThrowAnyException();
		assertThat(result).isNotNull();
		assertThat(result.isDone()).isTrue();
		assertThat(result.isCompletedExceptionally()).isFalse();
		assertThat(result.isCancelled()).isFalse();
		verify(this.tweetUpdateService).updateTweetWithRelations(eq(request), anyMap());
	}

	@Test
	void testUpdateTweetTweetNotFoundException() {
		// TweetNotFoundException propagation via the CompletableFuture wrapping
		// — the underlying TweetUpdateService throws synchronously.
		TweetUpdateRequest request = new TweetUpdateRequest();
		UUID tweetId = UUID.randomUUID();
		request.setId(tweetId);

		willThrow(new TweetNotFoundException("Tweet not found for id " + tweetId)).given(this.tweetUpdateService)
			.updateTweetWithRelations(eq(request), anyMap());

		Throwable e = catchThrowable(() -> this.tweetService.updateTweet(request).join());
		assertThat(e).isInstanceOf(CompletionException.class);
		assertThat(e.getCause()).isNotNull();
		assertThat(e.getCause()).isInstanceOf(TweetNotFoundException.class);
	}

	@Test
	void testUpdateTweetSkipsUnknownMentionUsername() {
		// Mention-username resolution moved to TweetService.updateTweet, ahead of the
		// transactional bean. An unknown username (UserNotFoundException) is silently
		// dropped from the resolved map; the update still proceeds, and the bean is invoked
		// with a map NOT containing the unknown username. userService is the real
		// UserService over the mocked userClient (@Cacheable is inert in this unit context),
		// so getUserIdLive("ghost") flows through userClient.getUserSummary("ghost").
		UUID userId = UUID.randomUUID();
		UUID tweetId = UUID.randomUUID();
		TweetUpdateRequest request = new TweetUpdateRequest();
		request.setId(tweetId);
		request.setUserId(userId);
		request.setContent("hi @ghost");

		// Mockito does not inject one @InjectMocks object into another, so wire the real
		// UserService (over the mocked userClient) into tweetService explicitly, as the
		// search-path tests do.
		ReflectionTestUtils.setField(this.tweetService, "userService", this.userService);
		given(this.userClient.getUserSummary("ghost"))
			.willReturn(CompletableFuture.failedFuture(new UserNotFoundException("ghost")));
		willDoNothing().given(this.tweetUpdateService).updateTweetWithRelations(eq(request), anyMap());

		CompletableFuture<Void> result = this.tweetService.updateTweet(request);

		assertThatCode(result::join).doesNotThrowAnyException();
		ArgumentCaptor<Map<String, UUID>> captor = ArgumentCaptor.forClass(Map.class);
		verify(this.tweetUpdateService).updateTweetWithRelations(eq(request), captor.capture());
		assertThat(captor.getValue()).doesNotContainKey("ghost");
	}

	@Test
	void testUpdateTweetMentionResolutionNonNotFoundErrorPropagates() {
		// A non-UserNotFoundException during pre-transaction resolution propagates: the
		// returned future completes exceptionally and the transactional bean is never
		// invoked (the connection is never acquired).
		UUID userId = UUID.randomUUID();
		UUID tweetId = UUID.randomUUID();
		TweetUpdateRequest request = new TweetUpdateRequest();
		request.setId(tweetId);
		request.setUserId(userId);
		request.setContent("hi @alice");

		// See sibling test: explicitly wire the real UserService (over the mocked
		// userClient) into tweetService, since @InjectMocks does not cross-inject.
		ReflectionTestUtils.setField(this.tweetService, "userService", this.userService);
		given(this.userClient.getUserSummary("alice"))
			.willReturn(CompletableFuture.failedFuture(new IllegalStateException("user-service down")));

		Throwable e = catchThrowable(() -> this.tweetService.updateTweet(request).join());

		assertThat(e).isInstanceOf(CompletionException.class);
		verify(this.tweetUpdateService, never()).updateTweetWithRelations(any(), anyMap());
	}

	@Test
	void testDeleteTweet() {
		UUID userId = UUID.randomUUID();
		UUID tweetId = UUID.randomUUID();

		this.tweetService.deleteTweet(userId, tweetId).join();

		verify(this.tweetUpdateService).deleteOwnedTweet(userId, tweetId);
	}

	@Test
	void testSearchTweets() {
		String searchTerm = "example";

		given(this.tweetRepository.findBySimilarity(anyString(), anyInt(), anyInt())).willReturn(new ArrayList<>());

		CompletableFuture<List<TweetDto>> result = this.tweetService.searchTweets(searchTerm, 0, 10);

		assertThat(result.join()).isEmpty();
		verify(this.tweetRepository).findBySimilarity(eq(searchTerm), eq(10), eq(0));
	}

	@Test
	void testSearchTweetsByHashtag() {
		String searchTerm = "#example";

		given(this.tweetRepository.findByHashtag(anyString(), anyInt(), anyInt())).willReturn(new ArrayList<>());

		CompletableFuture<List<TweetDto>> result = this.tweetService.searchTweetsByHashtag(searchTerm, 0, 10);

		assertThat(result.join()).isEmpty();
	}

	@Test
	void testGetTweetSummary() {
		UUID tweetId = UUID.randomUUID();

		TweetEntity tweetEntity = new TweetEntity();
		given(this.tweetRepository.findById(tweetId)).willReturn(Optional.of(tweetEntity));

		TweetDto tweetDto = new TweetDto();
		given(this.tweetMapper.mapEntityToDto(any())).willReturn(tweetDto);

		CompletableFuture<TweetDto> result = this.tweetService.getTweetSummary(tweetId);

		assertThat(result.join()).isEqualTo(tweetDto);
	}

	@Test
	void testGetUserTweetsSummary() {
		UUID userId = UUID.randomUUID();

		List<TweetEntity> tweetEntities = new ArrayList<>();
		given(this.tweetRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId)).willReturn(tweetEntities);

		CompletableFuture<List<TweetSummaryDto>> result = this.tweetService.getUserTweetsSummary(userId);

		assertThat(result.join()).isEmpty();
	}

	// ---------- additional branch-coverage tests ----------

	@Test
	void testGetUserFeedFallbackCacheReadValueThrows() throws Exception {
		// Covers the catch-block inside getFollowedUsersFromCache returning empty list.
		UUID userId = UUID.randomUUID();
		given(this.interactionClient.getFollowedIds(userId))
			.willReturn(CompletableFuture.failedFuture(new RuntimeException("downstream")));
		given(this.valueOperations.get(anyString())).willReturn("garbage");
		given(this.objectMapper.readValue(anyString(), any(TypeReference.class)))
			.willThrow(new RuntimeException("parse-failure"));
		given(this.tweetRepository.findByUserIdIn(eq(new ArrayList<>()), any(Pageable.class)))
			.willReturn(new ArrayList<>());

		List<TweetDto> result = this.tweetService.getUserFeed(userId, 0, 10).join();
		assertThat(result).isNotNull().isEmpty();
	}

	@Test
	void testGetTweetNotFoundSurfacesAs404Cause() {
		// the exceptionally branch must rethrow TweetNotFoundException
		// rather than wrapping it in a generic TweetException.
		UUID tweetId = UUID.randomUUID();
		given(this.tweetRepository.findById(tweetId)).willReturn(Optional.empty());

		Throwable ex = catchThrowable(() -> this.tweetService.getTweet(tweetId).join());
		assertThat(ex).isInstanceOf(CompletionException.class);
		Throwable cause = ex.getCause();
		assertThat(cause).isNotNull().isInstanceOf(TweetNotFoundException.class);
	}

	@Test
	void testGetTweetPropagatesDownstreamError() {
		// getTweet mirrors reactive's enrichSingleTweetDto: downstream failures
		// propagate as-is (no "Failed to fetch tweet details" wrapping). A failed
		// interaction call surfaces the original RuntimeException as the cause.
		UUID tweetId = UUID.randomUUID();
		TweetEntity entity = new TweetEntity();
		entity.setId(tweetId);
		given(this.tweetRepository.findById(tweetId)).willReturn(Optional.of(entity));

		given(this.interactionClient.getLikesCount(tweetId))
			.willReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));
		given(this.interactionClient.getRepliesCount(tweetId)).willReturn(CompletableFuture.completedFuture(0L));
		given(this.interactionClient.getRetweetsCount(tweetId)).willReturn(CompletableFuture.completedFuture(0L));
		given(this.interactionClient.getRepliesForTweet(tweetId))
			.willReturn(CompletableFuture.completedFuture(List.of()));
		given(this.hashtagRepository.findHashtagsByTweetId(tweetId)).willReturn(List.of());
		given(this.mentionRepository.findMentionsByTweetId(tweetId)).willReturn(List.of());

		Throwable ex = catchThrowable(() -> this.tweetService.getTweet(tweetId).join());
		assertThat(ex).isInstanceOf(CompletionException.class);
		Throwable cause = ex.getCause();
		assertThat(cause).isNotNull().isInstanceOf(RuntimeException.class);
		assertThat(cause.getMessage()).isEqualTo("boom");
	}

	@Test
	void testGetTweetSummaryNotFound() {
		UUID tweetId = UUID.randomUUID();
		given(this.tweetRepository.findById(tweetId)).willReturn(Optional.empty());

		Throwable ex = catchThrowable(() -> this.tweetService.getTweetSummary(tweetId).join());
		assertThat(ex).isInstanceOf(CompletionException.class);
		assertThat(ex.getCause()).isInstanceOf(TweetNotFoundException.class);
	}

	@Test
	void testSearchTweetsWithResultsExercisesPerEntityBranch() {
		// Covers the per-result mapping branch inside computeTweetsFromPage when the page is
		// non-empty: the page's distinct authors are resolved in ONE batched getUserSummaries
		// call and each tweet maps against that index in page order.
		UUID authorId = UUID.randomUUID();
		TweetEntity entity = new TweetEntity();
		entity.setId(UUID.randomUUID());
		entity.setUserId(authorId);

		UserDto author = new UserDto();
		author.setId(authorId);

		// Replace TweetService's `userService` field with a Mockito mock so the batched
		// computeTweetsFromPage path can run without NPEs.
		UserService userServiceMock = Mockito.mock(UserService.class);
		given(userServiceMock.getUserSummaries(List.of(authorId)))
			.willReturn(CompletableFuture.completedFuture(List.of(author)));
		ReflectionTestUtils.setField(this.tweetService, "userService", userServiceMock);

		given(this.tweetRepository.findBySimilarity(anyString(), anyInt(), anyInt()))
			.willReturn(new ArrayList<>(List.of(entity)));
		given(this.tweetMapper.mapEntityToDto(any(TweetEntity.class), any(UserDto.class))).willReturn(new TweetDto());

		List<TweetDto> result = this.tweetService.searchTweets("foo", 0, 10).join();
		assertThat(result).hasSize(1);
		// One batched author call for the whole page, not one-per-result.
		verify(userServiceMock, times(1)).getUserSummaries(List.of(authorId));

		// Restore (other tests don't touch this field).
		ReflectionTestUtils.setField(this.tweetService, "userService", this.userService);
	}

	@Test
	void testSearchTweetsByHashtagWithResults() {
		UUID authorId = UUID.randomUUID();
		TweetEntity entity = new TweetEntity();
		entity.setId(UUID.randomUUID());
		entity.setUserId(authorId);

		UserDto author = new UserDto();
		author.setId(authorId);

		UserService userServiceMock = Mockito.mock(UserService.class);
		given(userServiceMock.getUserSummaries(List.of(authorId)))
			.willReturn(CompletableFuture.completedFuture(List.of(author)));
		ReflectionTestUtils.setField(this.tweetService, "userService", userServiceMock);

		given(this.tweetRepository.findByHashtag(anyString(), anyInt(), anyInt()))
			.willReturn(new ArrayList<>(List.of(entity)));
		given(this.tweetMapper.mapEntityToDto(any(TweetEntity.class), any(UserDto.class))).willReturn(new TweetDto());

		List<TweetDto> result = this.tweetService.searchTweetsByHashtag("#foo", 0, 10).join();
		assertThat(result).hasSize(1);

		ReflectionTestUtils.setField(this.tweetService, "userService", this.userService);
	}

	@Test
	void testSearchTweetsMissingAuthorFailsWithUserNotFound() {
		// An author id absent from the batch result (the endpoint omits unknown ids) reproduces
		// the per-call path's not-found: UserNotFoundException with the same message, failing the
		// whole search (mapped to 404), rather than nulling/omitting the author.
		UUID authorId = UUID.randomUUID();
		TweetEntity entity = new TweetEntity();
		entity.setId(UUID.randomUUID());
		entity.setUserId(authorId);

		UserService userServiceMock = Mockito.mock(UserService.class);
		// Batch returns empty -> the requested author is missing.
		given(userServiceMock.getUserSummaries(List.of(authorId)))
			.willReturn(CompletableFuture.completedFuture(List.of()));
		ReflectionTestUtils.setField(this.tweetService, "userService", userServiceMock);

		given(this.tweetRepository.findBySimilarity(anyString(), anyInt(), anyInt()))
			.willReturn(new ArrayList<>(List.of(entity)));

		Throwable ex = catchThrowable(() -> this.tweetService.searchTweets("foo", 0, 10).join());
		assertThat(ex).isInstanceOf(CompletionException.class);
		assertThat(ex.getCause()).isInstanceOf(UserNotFoundException.class);
		assertThat(ex.getCause()).hasMessage("User not found for id: " + authorId);

		ReflectionTestUtils.setField(this.tweetService, "userService", this.userService);
	}

	@Test
	void testCreateTweetTokenizationTweetNotFoundFailsWithTweetException() throws Exception {
		// Forces the consumer call to throw TweetNotFoundException so
		// processTweetTokens hits the terminal catch branch: no retry, and the
		// error surfaces as a TweetException that fails createTweet. Mirrors the
		// reactive stack, which excludes TweetNotFoundException from retry and
		// maps it to TweetException -> 500. Uses a dedicated single-thread
		// executor so we can join on the async tokenization tasks safely.
		ExecutorService localExec = Executors.newSingleThreadExecutor();
		ReflectionTestUtils.setField(this.tweetService, "executorService", localExec);

		TweetCreationRequest request = new TweetCreationRequest();
		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setId(UUID.randomUUID());

		given(this.tweetMapper.mapCreationRequestToEntity(any())).willReturn(tweetEntity);
		given(this.tweetRepository.save(any())).willReturn(tweetEntity);
		given(this.tweetMapper.mapEntityToCreationDto(any())).willReturn(new TweetDto());

		willThrow(new TweetNotFoundException("missing")).given(this.mentionService).handleTweetCreationMentions(any());
		willThrow(new TweetNotFoundException("missing")).given(this.hashtagService).handleTweetCreationHashtags(any());

		CompletableFuture<TweetDto> future = this.tweetService.createTweet(request);
		Throwable thrown = catchThrowable(future::get);
		assertThat(thrown).isInstanceOf(ExecutionException.class);
		assertThat(thrown.getCause()).isInstanceOf(TweetException.class);

		localExec.shutdown();
		assertThat(localExec.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

		// Each consumer invoked exactly once: TweetNotFoundException is terminal
		// (no retry) but now fails the create instead of being swallowed.
		verify(this.mentionService).handleTweetCreationMentions(any());
		verify(this.hashtagService).handleTweetCreationHashtags(any());

		// Restore default executor for any subsequent tests.
		ReflectionTestUtils.setField(this.tweetService, "executorService", this.executorService);
	}

	@Test
	void testCreateTweetTokenizationGenericExceptionRetriesThenWraps() throws Exception {
		// createTweet waits for both hashtag/mention handlers via
		// thenComposeAsync + allOf, so a TweetException raised inside either
		// handler propagates up the chain and surfaces as the
		// CompletableFuture's exceptional completion. Both handlers are still
		// invoked MAX_RETRIES + 1 (=11) times — matching reactive's
		// Retry.fixedDelay(MAX_RETRIES) total executions (1 initial + 10
		// retries) — exercising the generic-exception catch branch +
		// retry-exhaustion final throw.
		ExecutorService localExec = Executors.newSingleThreadExecutor();
		ReflectionTestUtils.setField(this.tweetService, "executorService", localExec);

		TweetCreationRequest request = new TweetCreationRequest();
		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setId(UUID.randomUUID());

		given(this.tweetMapper.mapCreationRequestToEntity(any())).willReturn(tweetEntity);
		given(this.tweetRepository.save(any())).willReturn(tweetEntity);
		given(this.tweetMapper.mapEntityToCreationDto(any())).willReturn(new TweetDto());

		willThrow(new RuntimeException("flaky")).given(this.mentionService).handleTweetCreationMentions(any());
		willThrow(new RuntimeException("flaky")).given(this.hashtagService).handleTweetCreationHashtags(any());

		CompletableFuture<TweetDto> future = this.tweetService.createTweet(request);
		Throwable thrown = catchThrowable(future::get);
		assertThat(thrown).isInstanceOf(ExecutionException.class);
		assertThat(thrown.getCause()).isInstanceOf(TweetException.class);

		localExec.shutdown();
		assertThat(localExec.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

		// 11 total attempts (1 initial + 10 retries) × 1 token-type per consumer.
		verify(this.mentionService, times(11)).handleTweetCreationMentions(any());
		verify(this.hashtagService, times(11)).handleTweetCreationHashtags(any());

		ReflectionTestUtils.setField(this.tweetService, "executorService", this.executorService);
	}

}
