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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import ro.tweebyte.interactionservice.cache.CountCache;
import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.mapper.LikeMapper;
import ro.tweebyte.interactionservice.model.LikeDto;
import ro.tweebyte.interactionservice.model.TweetCount;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Covers LikeService paths the happy/branch suites leave open: the batched like-count read
 * (empty short-circuit + GROUP BY), and the idempotent-like recovery that swallows a
 * unique-constraint violation and returns the row that already exists.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LikeServiceBatchTests {

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

	private UUID userId;

	private UUID tweetId;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		ReflectionTestUtils.setField(this.likeService, "executorService", java.util.concurrent.Executors.newSingleThreadExecutor());
		this.userId = UUID.randomUUID();
		this.tweetId = UUID.randomUUID();
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
	void getTweetLikesCounts_Empty_ReturnsEmptyMap() throws Exception {
		assertThat(this.likeService.getTweetLikesCounts(List.of()).get()).isEmpty();
		assertThat(this.likeService.getTweetLikesCounts(null).get()).isEmpty();
		verify(this.likeRepository, never()).countByLikeableIdInAndLikeableType(any(), any());
	}

	@Test
	void getTweetLikesCounts_Populated_GroupsByTweet() throws Exception {
		given(this.likeRepository.countByLikeableIdInAndLikeableType(List.of(this.tweetId),
				LikeEntity.LikeableType.TWEET))
			.willReturn(List.of(new TweetCount(this.tweetId, 8L)));

		Map<UUID, Long> counts = this.likeService.getTweetLikesCounts(List.of(this.tweetId)).get();

		assertThat(counts).containsEntry(this.tweetId, 8L);
	}

	@Test
	void unlikeTweet_NoExistingRow_SkipsDeleteAndDecrement() throws Exception {
		// A repeated unlike finds no row: nothing to delete and no count delta.
		given(this.likeRepository.findByUserIdAndLikeableIdAndLikeableType(this.userId, this.tweetId,
				LikeEntity.LikeableType.TWEET))
			.willReturn(Optional.empty());

		this.likeService.unlikeTweet(this.userId, this.tweetId).get();

		verify(this.likeRepository, never()).deleteByUserIdAndLikeableIdAndLikeableType(any(), any(), any());
		verify(this.countCache, never()).decrement(any());
	}

	@Test
	void likeTweet_DuplicateInsert_ReturnsExistingRow() throws Exception {
		// A repeat like trips the UNIQUE constraint; the service swallows it and returns
		// the existing row so the like stays idempotent.
		LikeEntity existing = new LikeEntity();
		given(this.tweetService.getTweetSummary(this.tweetId))
			.willReturn(CompletableFuture.completedFuture(new TweetDto()));
		given(this.likeMapper.mapRequestToEntity(this.userId, this.tweetId, LikeEntity.LikeableType.TWEET))
			.willReturn(new LikeEntity());
		given(this.likeRepository.save(any(LikeEntity.class)))
			.willThrow(new DataIntegrityViolationException("duplicate"));
		given(this.likeRepository.findByUserIdAndLikeableIdAndLikeableType(this.userId, this.tweetId,
				LikeEntity.LikeableType.TWEET))
			.willReturn(Optional.of(existing));
		given(this.likeMapper.mapEntityToDto(existing)).willReturn(new LikeDto());

		LikeDto result = this.likeService.likeTweet(this.userId, this.tweetId).get();

		assertThat(result).isNotNull();
		verify(this.likeRepository).findByUserIdAndLikeableIdAndLikeableType(this.userId, this.tweetId,
				LikeEntity.LikeableType.TWEET);
		verify(this.likeMapper).mapEntityToDto(existing);
	}

	@Test
	void likeTweet_DuplicateInsertButRowGone_RethrowsOriginal() {
		// The duplicate fired but the row could not be re-read (race): the original
		// violation is rethrown via orElseThrow.
		DataIntegrityViolationException violation = new DataIntegrityViolationException("duplicate");
		given(this.tweetService.getTweetSummary(this.tweetId))
			.willReturn(CompletableFuture.completedFuture(new TweetDto()));
		given(this.likeMapper.mapRequestToEntity(this.userId, this.tweetId, LikeEntity.LikeableType.TWEET))
			.willReturn(new LikeEntity());
		given(this.likeRepository.save(any(LikeEntity.class))).willThrow(violation);
		given(this.likeRepository.findByUserIdAndLikeableIdAndLikeableType(this.userId, this.tweetId,
				LikeEntity.LikeableType.TWEET))
			.willReturn(Optional.empty());

		assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> this.likeService.likeTweet(this.userId, this.tweetId).get()))
			.hasCauseInstanceOf(DataIntegrityViolationException.class);

		verify(this.likeMapper, never()).mapEntityToDto(any());
	}

}
