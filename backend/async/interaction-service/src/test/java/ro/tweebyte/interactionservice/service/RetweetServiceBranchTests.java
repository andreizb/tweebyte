/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ro.tweebyte.interactionservice.cache.CountCache;
import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.exception.TweetNotFoundException;
import ro.tweebyte.interactionservice.mapper.RetweetMapper;
import ro.tweebyte.interactionservice.model.RetweetCreateRequest;
import ro.tweebyte.interactionservice.model.RetweetUpdateRequest;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Branch-coverage tests for RetweetService — covers the null-tweet / not-found arms
 * missing from RetweetServiceTest's happy-path scenarios.
 */
@ExtendWith(MockitoExtension.class)
class RetweetServiceBranchTests {

	@Mock
	private TweetService tweetService;

	@Mock
	private RetweetRepository retweetRepository;

	@Mock
	private RetweetMapper retweetMapper;

	@Mock
	private UserService userService;

	@Mock
	private CountCache countCache;

	@Mock(lenient = true)
	private ExecutorService executorService;

	@InjectMocks
	private RetweetService retweetService;

	@BeforeEach
	void runAsyncInline() {
		// RetweetService runs its work on runAsync/supplyAsync(..., executorService) — run the
		// task inline so the CompletableFuture completes synchronously in these unit tests.
		willAnswer(invocation -> {
			((Runnable) invocation.getArgument(0)).run();
			return null;
		}).given(this.executorService).execute(any(Runnable.class));
	}

	@Test
	void createRetweet_missingTweet_throws() {
		RetweetCreateRequest request = new RetweetCreateRequest();
		request.setOriginalTweetId(UUID.randomUUID());

		given(this.tweetService.getTweetSummary(any()))
			.willReturn(CompletableFuture.<ro.tweebyte.interactionservice.model.TweetDto>failedFuture(
					new TweetNotFoundException("Tweet not found")));

		Throwable ex = catchThrowable(() -> this.retweetService.createRetweet(request).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);

		assertThat(ex.getCause()).isInstanceOf(TweetNotFoundException.class);
		verify(this.retweetRepository, never()).save(any());
	}

	@Test
	void updateRetweet_notFound_throws() {
		RetweetUpdateRequest request = new RetweetUpdateRequest();
		request.setId(UUID.randomUUID());

		given(this.retweetRepository.findById(any())).willReturn(Optional.empty());

		Throwable ex = catchThrowable(() -> this.retweetService.updateRetweet(request).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);

		assertThat(ex.getCause()).isInstanceOf(IllegalArgumentException.class);
		verify(this.retweetRepository, never()).save(any(RetweetEntity.class));
	}

}
