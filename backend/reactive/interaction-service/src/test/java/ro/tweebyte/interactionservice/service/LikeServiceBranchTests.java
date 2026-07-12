/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.interactionservice.cache.CountCache;
import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.mapper.LikeMapper;
import ro.tweebyte.interactionservice.model.LikeDto;
import ro.tweebyte.interactionservice.model.LikeableType;
import ro.tweebyte.interactionservice.model.TweetCount;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Branch coverage — exercises the negative branches of LikeService that the existing
 * happy-path tests skip. Specifically the "likeReply when reply does not exist" branch
 * which reaches the IllegalArgumentException error signal, and the "likeTweet" path with
 * the tweet-summary error (covers the .flatMap propagation).
 */
@ExtendWith(MockitoExtension.class)
class LikeServiceBranchTests {

	@InjectMocks
	private LikeService likeService;

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

	@Mock
	private TransactionalOperator txOperator;

	private UUID userId;

	private UUID tweetId;

	private UUID replyId;

	@BeforeEach
	void setUp() {
		this.userId = UUID.randomUUID();
		this.tweetId = UUID.randomUUID();
		this.replyId = UUID.randomUUID();
	}

	@Test
	void likeReply_NotFound_RaisesIllegalArgument() {
		// An empty findById means the reply is missing, so likeReply takes the
		// switchIfEmpty branch and raises the "Reply does not exist." error.
		given(this.replyRepository.findById(this.replyId)).willReturn(Mono.empty());

		StepVerifier.create(this.likeService.likeReply(this.userId, this.replyId))
			.expectErrorMatches(
					e -> e instanceof IllegalArgumentException && e.getMessage().equals("Reply does not exist."))
			.verify();

		verify(this.replyRepository).findById(this.replyId);
		verify(this.likeRepository, never()).save(any());
	}

	@Test
	void likeTweet_TweetServiceErrors_PropagatesError() {
		// Covers the likeTweet error pass-through when the
		// upstream tweet-summary fails (e.g. tweet not found in the cache /
		// tweet-service). The save() must not be invoked.
		given(this.tweetService.getTweetSummary(this.tweetId))
			.willReturn(Mono.error(new RuntimeException("tweet lookup failed")));

		StepVerifier.create(this.likeService.likeTweet(this.userId, this.tweetId))
			.expectError(RuntimeException.class)
			.verify();

		verify(this.tweetService).getTweetSummary(this.tweetId);
		verify(this.likeRepository, never()).save(any());
	}

	@Test
	void getTweetLikesCounts_DelegatesBatchedCacheReadWithGroupByLoader() {
		// Drives the batched count read: the cache resolves both ids (loader not subscribed), so
		// the returned map carries each tweet's like count keyed by tweet id.
		UUID tweetTwo = UUID.randomUUID();
		given(this.countCache.getAll(eq(List.of(this.tweetId, tweetTwo)), any(), any()))
			.willReturn(Mono.just(Map.of(this.tweetId, 4L, tweetTwo, 7L)));

		StepVerifier.create(this.likeService.getTweetLikesCounts(List.of(this.tweetId, tweetTwo)))
			.assertNext(counts -> assertThat(counts).containsEntry(this.tweetId, 4L).containsEntry(tweetTwo, 7L))
			.verifyComplete();
	}

	@Test
	void getTweetLikesCounts_CacheMiss_LoaderQueriesGroupByAndMapsTotals() {
		// Drive the real loader the service hands to CountCache.getAll so the GROUP BY query +
		// collectMap(tweetId, total) lambda is exercised, not just the delegation.
		UUID tweetTwo = UUID.randomUUID();
		given(this.likeRepository.countByLikeableIdInAndLikeableType(List.of(this.tweetId, tweetTwo),
				LikeableType.TWEET.name()))
			.willReturn(Flux.just(new TweetCount(this.tweetId, 4L), new TweetCount(tweetTwo, 7L)));
		given(this.countCache.getAll(eq(List.of(this.tweetId, tweetTwo)), any(), any())).willAnswer(invocation -> {
			java.util.function.Function<List<UUID>, Mono<Map<UUID, Long>>> loader = invocation.getArgument(2);
			return loader.apply(List.of(this.tweetId, tweetTwo));
		});

		StepVerifier.create(this.likeService.getTweetLikesCounts(List.of(this.tweetId, tweetTwo)))
			.assertNext(counts -> assertThat(counts).containsEntry(this.tweetId, 4L).containsEntry(tweetTwo, 7L))
			.verifyComplete();
	}

	@Test
	void likeTweet_DuplicateInsert_SwallowsConstraintAndReturnsExistingRow() {
		// A repeated like trips the UNIQUE constraint (DataIntegrityViolationException); the
		// onErrorResume branch swallows it, returns the already-persisted row, and — crucially —
		// the count delta does NOT fire (the increment runs only on a genuine insert).
		LikeEntity request = new LikeEntity();
		LikeEntity existing = new LikeEntity();
		LikeDto mapped = new LikeDto();
		given(this.tweetService.getTweetSummary(this.tweetId)).willReturn(Mono.just(new TweetDto()));
		given(this.likeMapper.mapRequestToEntity(this.userId, this.tweetId, LikeableType.TWEET.name()))
			.willReturn(request);
		given(this.likeRepository.save(request))
			.willReturn(Mono.error(new DataIntegrityViolationException("duplicate key")));
		given(this.likeRepository.findByUserIdAndLikeableIdAndLikeableType(this.userId, this.tweetId,
				LikeableType.TWEET.name()))
			.willReturn(Mono.just(existing));
		given(this.likeMapper.mapEntityToDto(existing)).willReturn(mapped);

		StepVerifier.create(this.likeService.likeTweet(this.userId, this.tweetId)).expectNext(mapped).verifyComplete();

		// Idempotent: the cached like count is not inflated by the duplicate.
		verify(this.countCache, never()).increment(anyString());
	}

}
