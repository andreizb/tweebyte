/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import ro.tweebyte.interactionservice.cache.CountCache;
import ro.tweebyte.interactionservice.cache.TopReplyCache;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.mapper.ReplyMapper;
import ro.tweebyte.interactionservice.model.ReplyCreateRequest;
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.TopReply;
import ro.tweebyte.interactionservice.model.TweetCount;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.ReplyRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Covers ReplyService paths the happy/branch suites leave open: media validation on
 * createReply (valid, missing, empty), and the batched reply-count / top-reply enrichment
 * including the short-circuit on a null/empty id list.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReplyServiceBatchTests {

	@Mock
	private TweetService tweetService;

	@Mock
	private UserService userService;

	@Mock
	private ReplyRepository replyRepository;

	@Mock
	private ReplyMapper replyMapper;

	@Mock
	private CountCache countCache;

	@Mock
	private TopReplyCache topReplyCache;

	@InjectMocks
	private ReplyService replyService;

	private UUID tweetId;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		ReflectionTestUtils.setField(this.replyService, "executorService", java.util.concurrent.Executors.newSingleThreadExecutor());
		this.tweetId = UUID.randomUUID();
		given(this.countCache.getAll(anyList(), any(Function.class), any(Function.class))).willAnswer(invocation -> {
			List<UUID> ids = invocation.getArgument(0);
			Function<List<UUID>, Map<UUID, Long>> loader = invocation.getArgument(2);
			return (ids == null || ids.isEmpty()) ? Map.of() : loader.apply(ids);
		});
		given(this.topReplyCache.getAll(anyList(), any(Function.class), any(Function.class))).willAnswer(invocation -> {
			List<UUID> ids = invocation.getArgument(0);
			Function<List<UUID>, Map<UUID, ReplyDto>> loader = invocation.getArgument(2);
			return (ids == null || ids.isEmpty()) ? Map.of() : loader.apply(ids);
		});
	}

	@AfterEach
	void tearDown() {
		ExecutorService executorService = (ExecutorService) ReflectionTestUtils.getField(this.replyService,
				"executorService");
		executorService.shutdownNow();
	}

	@Test
	void createReply_WithValidMedia_SavesAndMaps() throws Exception {
		UUID mediaId = UUID.randomUUID();
		ReplyCreateRequest request = new ReplyCreateRequest();
		request.setTweetId(this.tweetId);
		request.setMediaIds(new UUID[] { mediaId });

		given(this.tweetService.getTweetSummary(this.tweetId)).willReturn(CompletableFuture.completedFuture(new TweetDto()));
		given(this.userService.mediaExists(mediaId)).willReturn(CompletableFuture.completedFuture(true));
		given(this.replyMapper.mapRequestToEntity(request)).willReturn(new ReplyEntity());
		given(this.replyRepository.save(any(ReplyEntity.class))).willReturn(new ReplyEntity());
		given(this.replyMapper.mapEntityToCreationDto(any())).willReturn(new ReplyDto());

		this.replyService.createReply(request).get();

		verify(this.userService).mediaExists(mediaId);
		verify(this.replyRepository).save(any(ReplyEntity.class));
	}

	@Test
	void createReply_WithMissingMedia_ThrowsBadRequestAndDoesNotSave() {
		UUID mediaId = UUID.randomUUID();
		ReplyCreateRequest request = new ReplyCreateRequest();
		request.setTweetId(this.tweetId);
		request.setMediaIds(new UUID[] { mediaId });

		given(this.tweetService.getTweetSummary(this.tweetId)).willReturn(CompletableFuture.completedFuture(new TweetDto()));
		given(this.userService.mediaExists(mediaId)).willReturn(CompletableFuture.completedFuture(false));

		Throwable ex = catchThrowable(() -> this.replyService.createReply(request).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(ResponseStatusException.class);
		verify(this.replyRepository, never()).save(any());
	}

	@Test
	void createReply_NoMedia_SkipsMediaRoundTrip() throws Exception {
		ReplyCreateRequest request = new ReplyCreateRequest();
		request.setTweetId(this.tweetId);
		request.setMediaIds(new UUID[0]);

		given(this.tweetService.getTweetSummary(this.tweetId)).willReturn(CompletableFuture.completedFuture(new TweetDto()));
		given(this.replyMapper.mapRequestToEntity(request)).willReturn(new ReplyEntity());
		given(this.replyRepository.save(any(ReplyEntity.class))).willReturn(new ReplyEntity());
		given(this.replyMapper.mapEntityToCreationDto(any())).willReturn(new ReplyDto());

		this.replyService.createReply(request).get();

		verify(this.userService, never()).mediaExists(any());
	}

	@Test
	void getReplyCountsForTweets_Empty_ReturnsEmptyMap() throws Exception {
		assertThat(this.replyService.getReplyCountsForTweets(List.of()).get()).isEmpty();
		assertThat(this.replyService.getReplyCountsForTweets(null).get()).isEmpty();
		verify(this.replyRepository, never()).countByTweetIdIn(any());
	}

	@Test
	void getReplyCountsForTweets_Populated_GroupsByTweet() throws Exception {
		given(this.replyRepository.countByTweetIdIn(List.of(this.tweetId)))
			.willReturn(List.of(new TweetCount(this.tweetId, 5L)));

		Map<UUID, Long> counts = this.replyService.getReplyCountsForTweets(List.of(this.tweetId)).get();

		assertThat(counts).containsEntry(this.tweetId, 5L);
	}

	@Test
	void getTopRepliesForTweets_Empty_ReturnsEmptyMap() throws Exception {
		assertThat(this.replyService.getTopRepliesForTweets(List.of()).get()).isEmpty();
		assertThat(this.replyService.getTopRepliesForTweets(null).get()).isEmpty();
		verify(this.replyRepository, never()).findRepliesByLikesForTweetIds(any());
	}

	@Test
	void getTopRepliesForTweets_KeepsFirstRowPerTweetAndResolvesUserName() throws Exception {
		UUID replyId = UUID.randomUUID();
		UUID authorId = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 0, 0);
		// Two rows for the same tweet: putIfAbsent keeps the first (highest-ranked).
		TopReply top = new TopReply(this.tweetId, replyId, authorId, "winner", createdAt, 9L);
		TopReply runnerUp = new TopReply(this.tweetId, UUID.randomUUID(), authorId, "loser", createdAt, 2L);
		given(this.replyRepository.findRepliesByLikesForTweetIds(List.of(this.tweetId)))
			.willReturn(List.of(top, runnerUp));
		UserDto author = new UserDto();
		author.setId(authorId);
		author.setUserName("bob");
		given(this.userService.getUserSummary(authorId)).willReturn(CompletableFuture.completedFuture(author));

		Map<UUID, ReplyDto> result = this.replyService.getTopRepliesForTweets(List.of(this.tweetId)).get();

		assertThat(result).containsKey(this.tweetId);
		assertThat(result.get(this.tweetId).getId()).isEqualTo(replyId);
		assertThat(result.get(this.tweetId).getUserName()).isEqualTo("bob");
	}

}
