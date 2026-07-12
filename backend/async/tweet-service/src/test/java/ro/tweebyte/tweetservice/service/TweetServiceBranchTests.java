/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import ro.tweebyte.tweetservice.client.InteractionClient;
import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.mapper.TweetMapper;
import ro.tweebyte.tweetservice.model.ReplyDto;
import ro.tweebyte.tweetservice.model.TweetCreationRequest;
import ro.tweebyte.tweetservice.model.TweetDto;
import ro.tweebyte.tweetservice.model.TweetHashtag;
import ro.tweebyte.tweetservice.model.TweetInteractionsDto;
import ro.tweebyte.tweetservice.model.TweetMention;
import ro.tweebyte.tweetservice.model.TweetSummaryDto;
import ro.tweebyte.tweetservice.model.TweetUpdateRequest;
import ro.tweebyte.tweetservice.repository.HashtagRepository;
import ro.tweebyte.tweetservice.repository.MentionRepository;
import ro.tweebyte.tweetservice.repository.TweetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Covers the async {@link TweetService} paths {@link TweetServiceTests} leaves uncovered:
 * the {@code getReferencedMediaIds} read, the {@code @CircuitBreaker} fallback
 * {@code getUserFeedWithCachedFollowed}, the populated {@code getUserTweetsSummary}
 * branch, and {@code validateMediaIds} for a create that does/doesn't reference known
 * media.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TweetServiceBranchTests {

	@Mock
	private InteractionClient interactionClient;

	@Mock
	private TweetRepository tweetRepository;

	@Mock
	private HashtagRepository hashtagRepository;

	@Mock
	private MentionRepository mentionRepository;

	@Mock
	private TweetMapper tweetMapper;

	@Mock
	private UserService userService;

	@Mock
	private HashtagService hashtagService;

	@Mock
	private MentionService mentionService;

	@Mock
	private RedisTemplate<String, String> redisTemplate;

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
	}

	@AfterEach
	void tearDown() {
		this.executorService.shutdownNow();
	}

	@Test
	void getReferencedMediaIds_returnsRepositoryResult() {
		List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
		given(this.tweetRepository.findReferencedMediaIds()).willReturn(ids);

		assertThat(this.tweetService.getReferencedMediaIds().join()).isEqualTo(ids);
	}

	@Test
	void getUserFeedWithCachedFollowed_servesFeedFromCacheOnOpenCircuit() throws Exception {
		// The @CircuitBreaker fallback reads followed-ids from the cache and
		// enriches the matching tweets page directly (no interaction-service
		// getFollowedIds round trip).
		UUID userId = UUID.randomUUID();
		List<UUID> cached = List.of(UUID.randomUUID());

		given(this.redisTemplate.opsForValue())
			.willReturn(org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class));
		given(this.redisTemplate.opsForValue().get("followed_cache::" + userId)).willReturn("[\"" + cached.get(0) + "\"]");
		given(this.objectMapper.readValue(org.mockito.ArgumentMatchers.anyString(),
				any(com.fasterxml.jackson.core.type.TypeReference.class)))
			.willReturn(cached);

		TweetEntity entity = new TweetEntity();
		entity.setId(UUID.randomUUID());
		given(this.tweetRepository.findByUserIdIn(any(), any(Pageable.class))).willReturn(List.of(entity));

		given(this.interactionClient.getTweetInteractions(anyList()))
			.willReturn(CompletableFuture.completedFuture(Map.of()));
		given(this.hashtagRepository.findRelationRowsByTweetIdIn(anyList())).willReturn(List.of());
		given(this.tweetMapper.mapEntityToDto(any(), any(), any(), any(), any(ReplyDto.class), anyList(), anyList()))
			.willReturn(new TweetDto());

		List<TweetDto> result = this.tweetService
			.getUserFeedWithCachedFollowed(userId, 0, 10, new RuntimeException("circuit open"))
			.get();

		assertThat(result).hasSize(1);
	}

	@Test
	void getUserTweetsSummary_populatedPathGroupsHashtagsAndMentions() {
		UUID userId = UUID.randomUUID();
		UUID tweetId = UUID.randomUUID();
		TweetEntity entity = new TweetEntity();
		entity.setId(tweetId);
		given(this.tweetRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId)).willReturn(List.of(entity));
		given(this.hashtagRepository.findHashtagsByTweetIdIn(anyList()))
			.willReturn(List.of(new TweetHashtag(tweetId, UUID.randomUUID(), "spring")));
		given(this.mentionRepository.findMentionsByTweetIdIn(anyList()))
			.willReturn(List.of(new TweetMention(tweetId, UUID.randomUUID(), UUID.randomUUID(), "alice")));

		List<TweetSummaryDto> result = this.tweetService.getUserTweetsSummary(userId).join();

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getId()).isEqualTo(tweetId);
		assertThat(result.get(0).getHashtags()).containsExactly("spring");
		assertThat(result.get(0).getMentions()).containsExactly("alice");
	}

	@Test
	void createTweet_validatesMediaIds_passesWhenAllExist() {
		UUID mediaId = UUID.randomUUID();
		TweetCreationRequest request = new TweetCreationRequest();
		request.setMediaIds(new UUID[] { mediaId });

		given(this.userService.mediaExists(mediaId)).willReturn(CompletableFuture.completedFuture(true));
		TweetEntity entity = new TweetEntity();
		entity.setId(UUID.randomUUID());
		given(this.tweetMapper.mapCreationRequestToEntity(any())).willReturn(entity);
		given(this.tweetRepository.save(any())).willReturn(entity);
		given(this.tweetMapper.mapEntityToCreationDto(any())).willReturn(new TweetDto());

		assertThat(this.tweetService.createTweet(request).join()).isNotNull();
	}

	@Test
	void createTweet_validatesMediaIds_rejectsWhenMediaMissing() {
		UUID mediaId = UUID.randomUUID();
		TweetCreationRequest request = new TweetCreationRequest();
		request.setMediaIds(new UUID[] { mediaId });

		given(this.userService.mediaExists(mediaId)).willReturn(CompletableFuture.completedFuture(false));

		Throwable ex = catchThrowable(() -> this.tweetService.createTweet(request).join());
		assertThat(ex).isInstanceOf(CompletionException.class);
		assertThat(ex.getCause()).isInstanceOf(ResponseStatusException.class);
	}

	@Test
	void createTweet_skipsMediaRoundTripWhenMediaIdsNull() {
		// Null media_ids → validateMediaIds short-circuits to a completed future, never
		// touching user-service (no mediaExists round-trip).
		TweetCreationRequest request = new TweetCreationRequest();
		request.setMediaIds(null);
		TweetEntity entity = new TweetEntity();
		entity.setId(UUID.randomUUID());
		given(this.tweetMapper.mapCreationRequestToEntity(any())).willReturn(entity);
		given(this.tweetRepository.save(any())).willReturn(entity);
		given(this.tweetMapper.mapEntityToCreationDto(any())).willReturn(new TweetDto());

		assertThat(this.tweetService.createTweet(request).join()).isNotNull();
		verify(this.userService, never()).mediaExists(any());
	}

	@Test
	void createTweet_skipsMediaRoundTripWhenMediaIdsEmpty() {
		// Empty (length 0) media_ids array trips the same short-circuit via the length arm.
		TweetCreationRequest request = new TweetCreationRequest();
		request.setMediaIds(new UUID[0]);
		TweetEntity entity = new TweetEntity();
		entity.setId(UUID.randomUUID());
		given(this.tweetMapper.mapCreationRequestToEntity(any())).willReturn(entity);
		given(this.tweetRepository.save(any())).willReturn(entity);
		given(this.tweetMapper.mapEntityToCreationDto(any())).willReturn(new TweetDto());

		assertThat(this.tweetService.createTweet(request).join()).isNotNull();
		verify(this.userService, never()).mediaExists(any());
	}

	@Test
	void updateTweet_resolvesKnownMentionToUserIdAndPassesItDownstream() {
		// A mention that resolves successfully: getUserIdLive returns a real id, so the handle()
		// success arm builds Map.entry(username, id) (ex == null) and the collect loop keeps it
		// (entry != null) — the two success arms the drop/propagate update tests never reach.
		UUID userId = UUID.randomUUID();
		UUID mentionedId = UUID.randomUUID();
		ReflectionTestUtils.setField(this.tweetService, "userService", this.userService);
		given(this.userService.getUserIdLive("alice")).willReturn(CompletableFuture.completedFuture(mentionedId));
		willDoNothing().given(this.tweetUpdateService).updateTweetWithRelations(any(), anyMap());

		TweetUpdateRequest request = new TweetUpdateRequest();
		request.setId(UUID.randomUUID());
		request.setUserId(userId);
		request.setContent("hi @alice");

		this.tweetService.updateTweet(request).join();

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, UUID>> captor = ArgumentCaptor.forClass(Map.class);
		verify(this.tweetUpdateService).updateTweetWithRelations(any(), captor.capture());
		assertThat(captor.getValue()).containsEntry("alice", mentionedId);
	}

	@Test
	void getUserFeed_splitsHashtagAndMentionRelationRowsAndKeepsNonNullTopReply() {
		// Drive enrichTweetsPage with BOTH a populated interactions map (non-null top reply →
		// L195 true arm) and populated relation rows of both kinds ('H' and 'M' → splitRelations
		// loop body + both branches), which the empty-stub feed tests never reach.
		UUID userId = UUID.randomUUID();
		UUID tweetId = UUID.randomUUID();
		List<UUID> followed = List.of(UUID.randomUUID());
		given(this.interactionClient.getFollowedIds(userId)).willReturn(CompletableFuture.completedFuture(followed));

		TweetEntity entity = new TweetEntity();
		entity.setId(tweetId);
		given(this.tweetRepository.findByUserIdIn(any(), any(Pageable.class))).willReturn(List.of(entity));

		ReplyDto topReply = ReplyDto.builder().id(UUID.randomUUID()).content("nice").build();
		given(this.interactionClient.getTweetInteractions(anyList())).willReturn(CompletableFuture
			.completedFuture(Map.of(tweetId, new TweetInteractionsDto(5L, 3L, 2L, topReply))));

		// One 'H' row and one 'M' row for the tweet: (tweetId, kind, id, userId, text).
		Object[] hashtagRow = { tweetId, "H", UUID.randomUUID(), null, "spring" };
		Object[] mentionRow = { tweetId, "M", UUID.randomUUID(), UUID.randomUUID(), "alice" };
		given(this.hashtagRepository.findRelationRowsByTweetIdIn(anyList()))
			.willReturn(List.of(hashtagRow, mentionRow));

		ArgumentCaptor<ReplyDto> replyCaptor = ArgumentCaptor.forClass(ReplyDto.class);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<HashtagEntity>> hashtagsCaptor = ArgumentCaptor.forClass(List.class);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<MentionEntity>> mentionsCaptor = ArgumentCaptor.forClass(List.class);
		given(this.tweetMapper.mapEntityToDto(any(), any(), any(), any(), replyCaptor.capture(),
				hashtagsCaptor.capture(), mentionsCaptor.capture())).willReturn(new TweetDto());

		List<TweetDto> result = this.tweetService.getUserFeed(userId, 0, 10).join();

		assertThat(result).hasSize(1);
		// Non-null top reply flows through unchanged (not replaced by the empty fallback).
		assertThat(replyCaptor.getValue()).isSameAs(topReply);
		// 'H' row split into the hashtag list, 'M' row into the mention list.
		assertThat(hashtagsCaptor.getValue()).hasSize(1);
		assertThat(hashtagsCaptor.getValue().get(0).getText()).isEqualTo("spring");
		assertThat(mentionsCaptor.getValue()).hasSize(1);
		assertThat(mentionsCaptor.getValue().get(0).getText()).isEqualTo("alice");
	}

}
