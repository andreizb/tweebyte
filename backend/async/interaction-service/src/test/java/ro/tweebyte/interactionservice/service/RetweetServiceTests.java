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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;

import ro.tweebyte.interactionservice.cache.CountCache;
import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.mapper.RetweetMapper;
import ro.tweebyte.interactionservice.model.RetweetCreateRequest;
import ro.tweebyte.interactionservice.model.RetweetDto;
import ro.tweebyte.interactionservice.model.RetweetUpdateRequest;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class RetweetServiceTests {

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

	private final UUID userId = UUID.randomUUID();

	private final UUID tweetId = UUID.randomUUID();

	private final UUID retweetId = UUID.randomUUID();

	@BeforeEach
	@SuppressWarnings("unchecked")
	void wireReadThrough() {
		given(this.countCache.get(anyString(), any(LongSupplier.class)))
			.willAnswer(invocation -> invocation.getArgument(1, LongSupplier.class).getAsLong());
		// RetweetService runs its work on runAsync/supplyAsync(..., executorService) — run the
		// task inline so the CompletableFuture completes synchronously in these unit tests.
		willAnswer(invocation -> {
			((Runnable) invocation.getArgument(0)).run();
			return null;
		}).given(this.executorService).execute(any(Runnable.class));
	}

	@Test
	void testCreateRetweet() throws ExecutionException, InterruptedException {
		UUID originalTweetId = UUID.randomUUID();
		RetweetCreateRequest request = new RetweetCreateRequest();
		request.setOriginalTweetId(originalTweetId);
		TweetDto tweet = new TweetDto();

		given(this.tweetService.getTweetSummary(any())).willReturn(CompletableFuture.completedFuture(tweet));
		given(this.retweetMapper.mapRequestToEntity(any())).willReturn(new RetweetEntity());
		given(this.retweetRepository.save(any())).willReturn(new RetweetEntity());
		given(this.retweetMapper.mapEntityToDto(any())).willReturn(new RetweetDto());

		var result = this.retweetService.createRetweet(request).get();

		verify(this.tweetService).getTweetSummary(any());
		verify(this.retweetRepository).save(any());
		// A persisted retweet applies the +1 retweet-count delta.
		verify(this.countCache).increment("retweet_count::" + originalTweetId);
		assertThat(result).isNotNull();
	}

	@Test
	void testUpdateRetweet() throws ExecutionException, InterruptedException {
		// Happy path = caller owns the retweet (retweeterId == userId); otherwise 403.
		RetweetUpdateRequest request = new RetweetUpdateRequest();
		request.setRetweeterId(this.userId);

		RetweetEntity retweet = new RetweetEntity();
		retweet.setId(this.retweetId);
		retweet.setRetweeterId(this.userId);

		given(this.retweetRepository.findById(any())).willReturn(Optional.of(retweet));

		this.retweetService.updateRetweet(request).get();

		verify(this.retweetRepository).findById(any());
		verify(this.retweetRepository).save(any());
	}

	@Test
	void testDeleteRetweet() throws ExecutionException, InterruptedException {
		// deleteRetweet resolves the retweet first: 404 if missing, 403 if owned by
		// another user. Happy path = caller owns the retweet (retweeterId == userId).
		RetweetEntity retweet = new RetweetEntity();
		retweet.setId(this.retweetId);
		retweet.setRetweeterId(this.userId);
		retweet.setOriginalTweetId(this.tweetId);
		given(this.retweetRepository.findById(this.retweetId)).willReturn(Optional.of(retweet));

		this.retweetService.deleteRetweet(this.retweetId, this.userId).get();

		verify(this.retweetRepository).deleteById(this.retweetId);
		// Deleting the owner's retweet applies the -1 retweet-count delta.
		verify(this.countCache).decrement("retweet_count::" + this.tweetId);
	}

	@Test
	void testDeleteRetweet_NotOwner_Forbidden() {
		// A retweet owned by a different user yields 403 Forbidden — owner-scoped.
		RetweetEntity retweet = new RetweetEntity();
		retweet.setId(this.retweetId);
		retweet.setRetweeterId(UUID.randomUUID());
		given(this.retweetRepository.findById(this.retweetId)).willReturn(Optional.of(retweet));

		Throwable ex = catchThrowable(() -> this.retweetService.deleteRetweet(this.retweetId, this.userId).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
		assertThat(((org.springframework.web.server.ResponseStatusException) ex.getCause()).getStatusCode())
			.isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
		verify(this.retweetRepository, never()).deleteById(any());
	}

	@Test
	void testGetRetweetsByUser() throws ExecutionException, InterruptedException {
		RetweetEntity retweetEntity = new RetweetEntity();
		retweetEntity.setOriginalTweetId(this.tweetId);

		given(this.retweetRepository.findByRetweeterId(eq(this.userId), anyInt(), anyInt()))
			.willReturn(Collections.singletonList(retweetEntity));
		given(this.tweetService.getTweetSummary(any())).willReturn(CompletableFuture.completedFuture(new TweetDto()));
		given(this.userService.getUserSummary(any())).willReturn(CompletableFuture.completedFuture(new UserDto()));
		given(this.retweetMapper.mapEntityToDto(any(), any(), any())).willReturn(new RetweetDto());

		var result = this.retweetService.getRetweetsByUser(this.userId, 0, 10).get();

		verify(this.retweetRepository).findByRetweeterId(this.userId, 10, 0);
		verify(this.tweetService).getTweetSummary(any());
		verify(this.userService).getUserSummary(any());
		assertThat(result).isNotNull();
	}

	@Test
	void testGetRetweetsByUserResolvesUserSummaryOncePerCall() throws ExecutionException, InterruptedException {
		// Every row's retweeterId equals the method parameter, so the retweeter
		// summary is resolved once and reused across all rows.
		RetweetEntity rowA = new RetweetEntity();
		rowA.setOriginalTweetId(UUID.randomUUID());
		rowA.setRetweeterId(this.userId);
		RetweetEntity rowB = new RetweetEntity();
		rowB.setOriginalTweetId(UUID.randomUUID());
		rowB.setRetweeterId(this.userId);

		given(this.retweetRepository.findByRetweeterId(eq(this.userId), anyInt(), anyInt()))
			.willReturn(List.of(rowA, rowB));
		given(this.tweetService.getTweetSummary(any())).willReturn(CompletableFuture.completedFuture(new TweetDto()));
		given(this.userService.getUserSummary(any())).willReturn(CompletableFuture.completedFuture(new UserDto()));
		given(this.retweetMapper.mapEntityToDto(any(), any(), any())).willReturn(new RetweetDto());

		var result = this.retweetService.getRetweetsByUser(this.userId, 0, 10).get();

		assertThat(result).hasSize(2);
		verify(this.userService, times(1)).getUserSummary(this.userId);
	}

	@Test
	void testGetRetweetsOfTweet() throws ExecutionException, InterruptedException {
		RetweetEntity retweetEntity = new RetweetEntity();
		retweetEntity.setRetweeterId(this.userId);

		given(this.retweetRepository.findByOriginalTweetId(eq(this.tweetId), anyInt(), anyInt()))
			.willReturn(Collections.singletonList(retweetEntity));
		given(this.userService.getUserSummary(any())).willReturn(CompletableFuture.completedFuture(new UserDto()));
		given(this.retweetMapper.mapEntityToDto(any(), any())).willReturn(new RetweetDto());

		var result = this.retweetService.getRetweetsOfTweet(this.tweetId, 0, 10).get();

		verify(this.retweetRepository).findByOriginalTweetId(this.tweetId, 10, 0);
		verify(this.userService).getUserSummary(any());
		assertThat(result).isNotNull();
	}

	@Test
	void testGetRetweetCountOfTweet() throws ExecutionException, InterruptedException {
		given(this.retweetRepository.countByOriginalTweetId(this.tweetId)).willReturn(5L);

		var result = this.retweetService.getRetweetCountOfTweet(this.tweetId).get();

		verify(this.retweetRepository).countByOriginalTweetId(this.tweetId);
		assertThat(result).isNotNull();
	}

}
