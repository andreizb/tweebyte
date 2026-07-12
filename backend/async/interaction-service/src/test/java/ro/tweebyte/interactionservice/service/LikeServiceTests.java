/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.LongSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import ro.tweebyte.interactionservice.cache.CountCache;
import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.mapper.LikeMapper;
import ro.tweebyte.interactionservice.model.LikeDto;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
class LikeServiceTests {

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
	@SuppressWarnings("unchecked")
	void wireReadThrough() {
		ReflectionTestUtils.setField(this.likeService, "executorService", java.util.concurrent.Executors.newSingleThreadExecutor());
		// The count cache is a read-through pass-through in these unit tests: a get runs its
		// database-loader supplier, and a getAll runs its loader over the requested ids so the
		// underlying repository interactions stay observable.
		given(this.countCache.get(anyString(), any(LongSupplier.class)))
			.willAnswer(invocation -> invocation.getArgument(1, LongSupplier.class).getAsLong());
		given(this.countCache.getAll(anyList(), any(Function.class), any(Function.class))).willAnswer(invocation -> {
			List<UUID> ids = invocation.getArgument(0);
			Function<List<UUID>, Map<UUID, Long>> loader = invocation.getArgument(2);
			return (ids == null || ids.isEmpty()) ? Map.of() : loader.apply(ids);
		});
	}

	@AfterEach
	void tearDown() {
		ExecutorService executorService = (ExecutorService) ReflectionTestUtils.getField(this.likeService,
				"executorService");
		executorService.shutdownNow();
	}

	@Test
	void testGetUserLikes() throws ExecutionException, InterruptedException {
		LikeEntity likeEntity = new LikeEntity();
		likeEntity.setLikeableId(this.tweetId);
		given(this.likeRepository.findByUserIdAndLikeableType(eq(this.userId), eq(LikeEntity.LikeableType.TWEET),
				anyInt(), anyInt()))
			.willReturn(Collections.singletonList(likeEntity));
		given(this.tweetService.getTweetSummary(this.tweetId)).willReturn(CompletableFuture.completedFuture(new TweetDto()));
		given(this.likeMapper.mapToDto(any(), any(TweetDto.class))).willReturn(new LikeDto());

		var result = this.likeService.getUserLikes(this.userId, 0, 10).get();

		verify(this.likeRepository).findByUserIdAndLikeableType(this.userId, LikeEntity.LikeableType.TWEET, 10, 0);
		verify(this.tweetService).getTweetSummary(this.tweetId);
		verify(this.likeMapper).mapToDto(any(LikeEntity.class), any(TweetDto.class));
		assertThat(result).isNotNull();
	}

	@Test
	void testGetTweetLikes() throws ExecutionException, InterruptedException {
		LikeEntity likeEntity = new LikeEntity();
		likeEntity.setUserId(this.userId);
		given(this.likeRepository.findByLikeableIdAndLikeableType(eq(this.tweetId), eq(LikeEntity.LikeableType.TWEET),
				anyInt(), anyInt()))
			.willReturn(Collections.singletonList(likeEntity));
		given(this.userService.getUserSummary(this.userId)).willReturn(CompletableFuture.completedFuture(new UserDto()));
		given(this.likeMapper.mapToDto(any(), any(UserDto.class))).willReturn(new LikeDto());

		var result = this.likeService.getTweetLikes(this.tweetId, 0, 10).get();

		verify(this.likeRepository).findByLikeableIdAndLikeableType(this.tweetId, LikeEntity.LikeableType.TWEET, 10, 0);
		verify(this.userService).getUserSummary(this.userId);
		verify(this.likeMapper).mapToDto(any(LikeEntity.class), any(UserDto.class));
		assertThat(result).isNotNull();
	}

	@Test
	void testGetTweetLikesCount() throws ExecutionException, InterruptedException {
		given(this.likeRepository.countByLikeableIdAndLikeableType(this.tweetId, LikeEntity.LikeableType.TWEET)).willReturn(10L);

		var result = this.likeService.getTweetLikesCount(this.tweetId).get();

		verify(this.likeRepository).countByLikeableIdAndLikeableType(this.tweetId, LikeEntity.LikeableType.TWEET);
		assertThat(result).isEqualTo(10L);
	}

	@Test
	void testLikeTweet() throws ExecutionException, InterruptedException {
		given(this.tweetService.getTweetSummary(this.tweetId)).willReturn(CompletableFuture.completedFuture(new TweetDto()));
		given(this.likeMapper.mapRequestToEntity(this.userId, this.tweetId, LikeEntity.LikeableType.TWEET))
			.willReturn(new LikeEntity());
		given(this.likeRepository.save(any(LikeEntity.class))).willReturn(new LikeEntity());
		given(this.likeMapper.mapEntityToDto(any(LikeEntity.class))).willReturn(new LikeDto());

		var result = this.likeService.likeTweet(this.userId, this.tweetId).get();

		verify(this.tweetService).getTweetSummary(this.tweetId);
		verify(this.likeRepository).save(any(LikeEntity.class));
		verify(this.likeMapper).mapEntityToDto(any(LikeEntity.class));
		// A genuine tweet-like insert applies the +1 count delta.
		verify(this.countCache).increment("like_count::" + this.tweetId);
		assertThat(result).isNotNull();
	}

	@Test
	void testUnlikeTweet() throws ExecutionException, InterruptedException {
		// unlikeTweet resolves the row first so the count delta only fires for a like that
		// was actually counted; a present row triggers the delete and the decrement.
		given(this.likeRepository.findByUserIdAndLikeableIdAndLikeableType(this.userId, this.tweetId,
				LikeEntity.LikeableType.TWEET))
			.willReturn(Optional.of(new LikeEntity()));

		this.likeService.unlikeTweet(this.userId, this.tweetId).get();

		verify(this.likeRepository).deleteByUserIdAndLikeableIdAndLikeableType(this.userId, this.tweetId,
				LikeEntity.LikeableType.TWEET);
		verify(this.countCache).decrement("like_count::" + this.tweetId);
	}

	@Test
	void testLikeReply() throws ExecutionException, InterruptedException {
		// production switched from findByIdAndUserId(replyId, userId)
		// to findById(replyId) so any user can like any reply.
		given(this.replyRepository.findById(this.replyId)).willReturn(Optional.of(new ReplyEntity()));
		given(this.likeMapper.mapRequestToEntity(this.userId, this.replyId, LikeEntity.LikeableType.REPLY))
			.willReturn(new LikeEntity());
		given(this.likeRepository.save(any(LikeEntity.class))).willReturn(new LikeEntity());
		given(this.likeMapper.mapEntityToDto(any(LikeEntity.class))).willReturn(new LikeDto());

		var result = this.likeService.likeReply(this.userId, this.replyId).get();

		verify(this.replyRepository).findById(this.replyId);
		verify(this.likeRepository).save(any(LikeEntity.class));
		verify(this.likeMapper).mapEntityToDto(any(LikeEntity.class));
		// Reply likes carry no cached counter, so no count delta is applied.
		verify(this.countCache, never()).increment(anyString());
		assertThat(result).isNotNull();
	}

	@Test
	void testUnlikeReply() throws ExecutionException, InterruptedException {
		this.likeService.unlikeReply(this.userId, this.replyId).get();

		verify(this.likeRepository).deleteByUserIdAndLikeableIdAndLikeableType(this.userId, this.replyId,
				LikeEntity.LikeableType.REPLY);
	}

}
