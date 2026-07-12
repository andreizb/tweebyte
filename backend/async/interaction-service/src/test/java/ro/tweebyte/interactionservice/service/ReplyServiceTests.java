/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import ro.tweebyte.interactionservice.cache.CountCache;
import ro.tweebyte.interactionservice.cache.TopReplyCache;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.mapper.ReplyMapper;
import ro.tweebyte.interactionservice.model.ReplyCreateRequest;
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.ReplyUpdateRequest;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.ReplyRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@SpringBootTest
class ReplyServiceTests {

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

	@BeforeEach
	@SuppressWarnings("unchecked")
	void wireReadThrough() {
		ReflectionTestUtils.setField(this.replyService, "executorService", java.util.concurrent.Executors.newSingleThreadExecutor());
		// Pass-through caches: a count get/getAll runs its database loader, and a top-reply
		// get runs its loader so the underlying repository interactions stay observable.
		given(this.countCache.get(anyString(), any(LongSupplier.class)))
			.willAnswer(invocation -> invocation.getArgument(1, LongSupplier.class).getAsLong());
		given(this.topReplyCache.get(anyString(), any(Supplier.class)))
			.willAnswer(invocation -> invocation.getArgument(1, Supplier.class).get());
	}

	@AfterEach
	void tearDown() {
		ExecutorService executorService = (ExecutorService) ReflectionTestUtils.getField(this.replyService,
				"executorService");
		executorService.shutdownNow();
	}

	@Test
	void testCreateReply() throws ExecutionException, InterruptedException {
		UUID tweetId = UUID.randomUUID();
		ReplyCreateRequest request = new ReplyCreateRequest();
		request.setTweetId(tweetId);
		TweetDto tweet = new TweetDto();

		given(this.tweetService.getTweetSummary(any())).willReturn(CompletableFuture.completedFuture(tweet));
		given(this.replyMapper.mapRequestToEntity(any())).willReturn(new ReplyEntity());
		given(this.replyRepository.save(any())).willReturn(new ReplyEntity());
		given(this.replyMapper.mapEntityToCreationDto(any())).willReturn(new ReplyDto());

		this.replyService.createReply(request).get();

		verify(this.tweetService).getTweetSummary(any());
		verify(this.replyRepository).save(any());
		// A persisted reply applies the +1 reply-count delta.
		verify(this.countCache).increment("reply_count::" + tweetId);
	}

	@Test
	void testUpdateReply() throws ExecutionException, InterruptedException {
		ReplyUpdateRequest request = new ReplyUpdateRequest();
		request.setUserId(UUID.randomUUID());

		ReplyEntity reply = new ReplyEntity();
		reply.setUserId(request.getUserId());

		given(this.replyRepository.findById(any())).willReturn(Optional.of(reply));

		this.replyService.updateReply(request).get();

		verify(this.replyRepository).findById(any());
		verify(this.replyRepository).save(any());
	}

	@Test
	void testDeleteReply() throws ExecutionException, InterruptedException {
		UUID userId = UUID.randomUUID();
		UUID replyId = UUID.randomUUID();
		UUID tweetId = UUID.randomUUID();

		Optional<ReplyEntity> optReply = Optional.of(new ReplyEntity());
		optReply.get().setUserId(userId);
		optReply.get().setTweetId(tweetId);

		given(this.replyRepository.findById(replyId)).willReturn(optReply);

		this.replyService.deleteReply(userId, replyId).get();

		verify(this.replyRepository).findById(replyId);
		verify(this.replyRepository).deleteById(replyId);
		// Deleting the author's reply applies the -1 reply-count delta.
		verify(this.countCache).decrement("reply_count::" + tweetId);
	}

	@Test
	void testGetReplyCountForTweet() throws ExecutionException, InterruptedException {
		UUID tweetId = UUID.randomUUID();

		given(this.replyRepository.countByTweetId(any())).willReturn(5L);

		this.replyService.getReplyCountForTweet(tweetId).get();

		verify(this.replyRepository).countByTweetId(tweetId);
	}

	@Test
	void testGetTopReplyForTweet() throws ExecutionException, InterruptedException {
		UUID tweetId = UUID.randomUUID();

		Page<ReplyDto> page = new PageImpl<>(Collections.singletonList(new ReplyDto()));
		given(this.replyRepository.findTopReplyByLikesForTweetId(any(), any())).willReturn(page);

		given(this.userService.getUserSummary(any())).willReturn(CompletableFuture.completedFuture(new UserDto()));

		this.replyService.getTopReplyForTweet(tweetId).get();

		verify(this.replyRepository).findTopReplyByLikesForTweetId(any(), any());
		verify(this.userService).getUserSummary(any());
	}

	@Test
	void testGetRepliesForTweet() throws ExecutionException, InterruptedException {
		UUID tweetId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		ReplyEntity replyEntity = new ReplyEntity();
		replyEntity.setUserId(userId);
		replyEntity.setContent("Sample reply");
		List<ReplyEntity> replyEntities = List.of(replyEntity);

		UserDto userDto = new UserDto();
		userDto.setId(userId);
		userDto.setUserName("TestUser");

		ReplyDto replyDto = new ReplyDto();
		replyDto.setContent("Sample reply");
		replyDto.setUserName("TestUser");

		given(this.replyRepository.findByTweetIdOrderByCreatedAtDescIdDesc(eq(tweetId), anyInt(), anyInt()))
			.willReturn(replyEntities);
		given(this.userService.getUserSummary(userId)).willReturn(CompletableFuture.completedFuture(userDto));
		given(this.replyMapper.mapEntityToDto(replyEntity, "TestUser")).willReturn(replyDto);

		List<ReplyDto> result = this.replyService.getRepliesForTweet(tweetId, 0, 10).get();

		assertThat(result).isNotNull().hasSize(1);
		assertThat(result.get(0).getContent()).isEqualTo("Sample reply");
		assertThat(result.get(0).getUserName()).isEqualTo("TestUser");

		verify(this.replyRepository).findByTweetIdOrderByCreatedAtDescIdDesc(tweetId, 10, 0);
		verify(this.userService).getUserSummary(userId);
		verify(this.replyMapper).mapEntityToDto(replyEntity, "TestUser");
	}

}
