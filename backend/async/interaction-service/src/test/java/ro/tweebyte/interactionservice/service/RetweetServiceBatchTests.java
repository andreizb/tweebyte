/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import ro.tweebyte.interactionservice.cache.CountCache;
import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.exception.InteractionException;
import ro.tweebyte.interactionservice.mapper.RetweetMapper;
import ro.tweebyte.interactionservice.model.RetweetCreateRequest;
import ro.tweebyte.interactionservice.model.RetweetDto;
import ro.tweebyte.interactionservice.model.RetweetUpdateRequest;
import ro.tweebyte.interactionservice.model.TweetCount;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Covers RetweetService paths the happy/branch suites leave open: media validation on
 * createRetweet, the updateRetweet not-owner (403) arm, the getRetweetsByUser
 * mapper-exception wrapping, and the batched retweet-count read.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RetweetServiceBatchTests {

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

	@InjectMocks
	private RetweetService retweetService;

	private UUID originalTweetId;

	private UUID retweetId;

	private UUID userId;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		ReflectionTestUtils.setField(this.retweetService, "executorService", java.util.concurrent.Executors.newSingleThreadExecutor());
		this.originalTweetId = UUID.randomUUID();
		this.retweetId = UUID.randomUUID();
		this.userId = UUID.randomUUID();
		given(this.countCache.getAll(anyList(), any(Function.class), any(Function.class))).willAnswer(invocation -> {
			List<UUID> ids = invocation.getArgument(0);
			Function<List<UUID>, Map<UUID, Long>> loader = invocation.getArgument(2);
			return (ids == null || ids.isEmpty()) ? Map.of() : loader.apply(ids);
		});
	}

	@AfterEach
	void tearDown() {
		ExecutorService executorService = (ExecutorService) ReflectionTestUtils.getField(this.retweetService,
				"executorService");
		executorService.shutdownNow();
	}

	@Test
	void createRetweet_WithValidMedia_SavesAndMaps() throws Exception {
		UUID mediaId = UUID.randomUUID();
		RetweetCreateRequest request = new RetweetCreateRequest();
		request.setOriginalTweetId(this.originalTweetId);
		request.setMediaIds(new UUID[] { mediaId });

		given(this.tweetService.getTweetSummary(this.originalTweetId))
			.willReturn(CompletableFuture.completedFuture(new TweetDto()));
		given(this.userService.mediaExists(mediaId)).willReturn(CompletableFuture.completedFuture(true));
		given(this.retweetMapper.mapRequestToEntity(request)).willReturn(new RetweetEntity());
		given(this.retweetRepository.save(any(RetweetEntity.class))).willReturn(new RetweetEntity());
		given(this.retweetMapper.mapEntityToDto(any(RetweetEntity.class))).willReturn(new RetweetDto());

		this.retweetService.createRetweet(request).get();

		verify(this.userService).mediaExists(mediaId);
		verify(this.retweetRepository).save(any(RetweetEntity.class));
	}

	@Test
	void createRetweet_WithMissingMedia_ThrowsBadRequestAndDoesNotSave() {
		UUID mediaId = UUID.randomUUID();
		RetweetCreateRequest request = new RetweetCreateRequest();
		request.setOriginalTweetId(this.originalTweetId);
		request.setMediaIds(new UUID[] { mediaId });

		given(this.tweetService.getTweetSummary(this.originalTweetId))
			.willReturn(CompletableFuture.completedFuture(new TweetDto()));
		given(this.userService.mediaExists(mediaId)).willReturn(CompletableFuture.completedFuture(false));

		Throwable ex = catchThrowable(() -> this.retweetService.createRetweet(request).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(ResponseStatusException.class);
		verify(this.retweetRepository, never()).save(any());
	}

	@Test
	void updateRetweet_NotOwner_ThrowsForbidden() {
		RetweetUpdateRequest request = new RetweetUpdateRequest();
		request.setId(this.retweetId);
		request.setRetweeterId(this.userId);
		RetweetEntity otherOwner = new RetweetEntity();
		otherOwner.setId(this.retweetId);
		otherOwner.setRetweeterId(UUID.randomUUID());
		given(this.retweetRepository.findById(this.retweetId)).willReturn(Optional.of(otherOwner));

		Throwable ex = catchThrowable(() -> this.retweetService.updateRetweet(request).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(ResponseStatusException.class);
		assertThat(((ResponseStatusException) ex.getCause()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		verify(this.retweetRepository, never()).save(any());
	}

	@Test
	void getRetweetsByUser_MapperThrows_WrapsInInteractionException() {
		RetweetEntity entity = new RetweetEntity();
		entity.setOriginalTweetId(this.originalTweetId);
		entity.setRetweeterId(this.userId);
		given(this.retweetRepository.findByRetweeterId(eq(this.userId), anyInt(), anyInt())).willReturn(List.of(entity));
		given(this.tweetService.getTweetSummary(this.originalTweetId))
			.willReturn(CompletableFuture.completedFuture(new TweetDto()));
		given(this.userService.getUserSummary(this.userId)).willReturn(CompletableFuture.completedFuture(new UserDto()));
		given(this.retweetMapper.mapEntityToDto(any(RetweetEntity.class), any(UserDto.class), any(TweetDto.class)))
			.willThrow(new IllegalStateException("mapper boom"));

		Throwable ex = catchThrowable(() -> this.retweetService.getRetweetsByUser(this.userId, 0, 10).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(InteractionException.class);
	}

	@Test
	void getRetweetCountsForTweets_Empty_ReturnsEmptyMap() throws Exception {
		assertThat(this.retweetService.getRetweetCountsForTweets(List.of()).get()).isEmpty();
		assertThat(this.retweetService.getRetweetCountsForTweets(null).get()).isEmpty();
		verify(this.retweetRepository, never()).countByOriginalTweetIdIn(any());
	}

	@Test
	void getRetweetCountsForTweets_Populated_GroupsByTweet() throws Exception {
		given(this.retweetRepository.countByOriginalTweetIdIn(List.of(this.originalTweetId)))
			.willReturn(List.of(new TweetCount(this.originalTweetId, 6L)));

		Map<UUID, Long> counts = this.retweetService.getRetweetCountsForTweets(List.of(this.originalTweetId)).get();

		assertThat(counts).containsEntry(this.originalTweetId, 6L);
	}

}
