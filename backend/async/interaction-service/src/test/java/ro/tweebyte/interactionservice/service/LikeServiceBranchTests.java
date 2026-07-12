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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import ro.tweebyte.interactionservice.cache.CountCache;
import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.exception.TweetNotFoundException;
import ro.tweebyte.interactionservice.mapper.LikeMapper;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Branch-coverage focused tests for LikeService — exercises the negative paths that the
 * happy-path LikeServiceTest does not cover (null-tweet, missing reply, etc.).
 */
@ExtendWith(MockitoExtension.class)
class LikeServiceBranchTests {

	@Mock
	private UserService userService;

	@Mock
	private TweetService tweetService;

	@Mock
	private LikeRepository likeRepository;

	@Mock
	private ReplyRepository replyRepository;

	@Mock
	private LikeMapper likeMapper;

	@Mock
	private CountCache countCache;

	@InjectMocks
	private LikeService likeService;

	private final UUID userId = UUID.randomUUID();

	private final UUID tweetId = UUID.randomUUID();

	private final UUID replyId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(this.likeService, "executorService", java.util.concurrent.Executors.newSingleThreadExecutor());
	}

	@AfterEach
	void tearDown() {
		ExecutorService executorService = (ExecutorService) ReflectionTestUtils.getField(this.likeService,
				"executorService");
		executorService.shutdownNow();
	}

	@Test
	void likeTweet_missingTweet_throws() {
		given(this.tweetService.getTweetSummary(this.tweetId))
			.willReturn(CompletableFuture.<ro.tweebyte.interactionservice.model.TweetDto>failedFuture(
					new TweetNotFoundException("Tweet not found with id: " + this.tweetId)));

		Throwable ex = catchThrowable(() -> this.likeService.likeTweet(this.userId, this.tweetId).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);

		assertThat(ex.getCause()).isInstanceOf(TweetNotFoundException.class);
		verify(this.likeRepository, never()).save(any());
	}

	@Test
	void likeReply_replyMissing_throws() {
		// Negative branch: reply not present → IllegalArgumentException wrapped
		// production now uses findById(replyId), not findByIdAndUserId.
		given(this.replyRepository.findById(this.replyId)).willReturn(Optional.empty());

		Throwable ex = catchThrowable(() -> this.likeService.likeReply(this.userId, this.replyId).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);

		assertThat(ex.getCause()).isInstanceOf(IllegalArgumentException.class);
		verify(this.likeRepository, never()).save(any());
	}

	@Test
	void likeReply_replyPresent_savesAndMaps() {
		// Positive branch — sister to negative above, ensures both arms hit.
		given(this.replyRepository.findById(this.replyId)).willReturn(Optional.of(new ReplyEntity()));
		given(this.likeMapper.mapRequestToEntity(this.userId, this.replyId, LikeEntity.LikeableType.REPLY))
			.willReturn(new LikeEntity());
		given(this.likeRepository.save(any(LikeEntity.class))).willReturn(new LikeEntity());

		assertThatCode(() -> this.likeService.likeReply(this.userId, this.replyId).get()).doesNotThrowAnyException();
		verify(this.likeRepository).save(any(LikeEntity.class));
	}

}
