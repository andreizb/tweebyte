/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import ro.tweebyte.interactionservice.cache.CountCache;
import ro.tweebyte.interactionservice.cache.TopReplyCache;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.exception.TweetNotFoundException;
import ro.tweebyte.interactionservice.mapper.ReplyMapper;
import ro.tweebyte.interactionservice.model.ReplyCreateRequest;
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.ReplyUpdateRequest;
import ro.tweebyte.interactionservice.repository.ReplyRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Branch-coverage tests for ReplyService — covers null-tweet, mismatched-user delete,
 * missing-reply update, and empty top-reply page arms not exercised by ReplyServiceTest.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReplyServiceBranchTests {

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
	void createReply_missingTweet_throws() {
		ReplyCreateRequest request = new ReplyCreateRequest();
		given(this.tweetService.getTweetSummary(any()))
			.willReturn(CompletableFuture.<ro.tweebyte.interactionservice.model.TweetDto>failedFuture(
					new TweetNotFoundException("Tweet not found")));

		Throwable ex = catchThrowable(() -> this.replyService.createReply(request).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(TweetNotFoundException.class);
		verify(this.replyRepository, never()).save(any());
	}

	@Test
	void updateReply_notFound_throws() {
		ReplyUpdateRequest request = new ReplyUpdateRequest();
		request.setId(UUID.randomUUID());
		request.setUserId(UUID.randomUUID());

		given(this.replyRepository.findById(request.getId())).willReturn(Optional.empty());

		Throwable ex = catchThrowable(() -> this.replyService.updateReply(request).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(IllegalArgumentException.class);
		verify(this.replyRepository, never()).save(any());
	}

	@Test
	void updateReply_userMismatch_throwsForbidden() {
		// Non-author update now raises ResponseStatusException(FORBIDDEN) — mirrors
		// RetweetService and the reactive stack. The GlobalExceptionHandler maps this to
		// HTTP 403, not the former 404/500 produced by IllegalArgumentException.
		ReplyUpdateRequest request = new ReplyUpdateRequest();
		request.setId(UUID.randomUUID());
		request.setUserId(UUID.randomUUID());

		ReplyEntity reply = new ReplyEntity();
		reply.setUserId(UUID.randomUUID()); // different user

		given(this.replyRepository.findById(request.getId())).willReturn(Optional.of(reply));

		Throwable ex = catchThrowable(() -> this.replyService.updateReply(request).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(ResponseStatusException.class);
		assertThat(((ResponseStatusException) ex.getCause()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		verify(this.replyRepository, never()).save(any());
	}

	@Test
	void deleteReply_userMismatch_throwsForbidden() {
		UUID userId = UUID.randomUUID();
		UUID replyId = UUID.randomUUID();
		ReplyEntity reply = new ReplyEntity();
		reply.setUserId(UUID.randomUUID()); // different user

		given(this.replyRepository.findById(replyId)).willReturn(Optional.of(reply));

		// Non-author delete now raises ResponseStatusException(FORBIDDEN) — mirrors
		// RetweetService and the reactive stack.
		Throwable ex = catchThrowable(() -> this.replyService.deleteReply(userId, replyId).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(ResponseStatusException.class);
		assertThat(((ResponseStatusException) ex.getCause()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		verify(this.replyRepository, never()).deleteById(any());
	}

	@Test
	void deleteReply_replyMissing_noOp() {
		UUID userId = UUID.randomUUID();
		UUID replyId = UUID.randomUUID();
		given(this.replyRepository.findById(replyId)).willReturn(Optional.empty());

		// missing reply now throws IllegalArgumentException (the test name's
		// "noOp" predates the fix).
		Throwable ex = catchThrowable(() -> this.replyService.deleteReply(userId, replyId).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(IllegalArgumentException.class);
		verify(this.replyRepository, never()).deleteById(any());
	}

	@Test
	void getTopReplyForTweet_emptyPage_returnsEmptyDto() throws Exception {
		UUID tweetId = UUID.randomUUID();
		given(this.replyRepository.findTopReplyByLikesForTweetId(any(), any()))
			.willReturn(new PageImpl<>(Collections.emptyList()));

		ReplyDto dto = this.replyService.getTopReplyForTweet(tweetId).get();

		assertThat(dto).isNotNull();
		verify(this.userService, never()).getUserSummary(any());
	}

}
