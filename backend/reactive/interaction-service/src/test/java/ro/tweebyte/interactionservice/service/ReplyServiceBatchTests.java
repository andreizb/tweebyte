/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Covers ReplyService paths the success/branch suites leave open: media validation on
 * createReply (valid, missing, and empty), and the batched count/top-reply reads delegating
 * to the caches.
 */
@ExtendWith(MockitoExtension.class)
class ReplyServiceBatchTests {

	@InjectMocks
	private ReplyService replyService;

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

	private UUID tweetId;

	@BeforeEach
	void setUp() {
		this.tweetId = UUID.randomUUID();
	}

	@Test
	void createReply_WithValidMedia_ValidatesThenSaves() {
		UUID mediaId = UUID.randomUUID();
		ReplyCreateRequest request = new ReplyCreateRequest();
		request.setTweetId(this.tweetId);
		request.setMediaIds(new UUID[] { mediaId });
		ReplyEntity entity = ReplyEntity.builder().id(UUID.randomUUID()).build();
		ReplyDto dto = new ReplyDto();

		given(this.tweetService.getTweetSummary(this.tweetId)).willReturn(Mono.just(new TweetDto()));
		given(this.userService.mediaExists(mediaId)).willReturn(Mono.just(true));
		given(this.replyMapper.mapRequestToEntity(request)).willReturn(entity);
		given(this.replyRepository.save(entity)).willReturn(Mono.just(entity));
		given(this.countCache.increment(anyString())).willReturn(Mono.empty());
		given(this.replyMapper.mapEntityToCreationDto(entity)).willReturn(dto);

		StepVerifier.create(this.replyService.createReply(request)).expectNext(dto).verifyComplete();

		verify(this.userService).mediaExists(mediaId);
		verify(this.replyRepository).save(entity);
	}

	@Test
	void createReply_WithMissingMedia_ErrorsBadRequestAndDoesNotSave() {
		UUID mediaId = UUID.randomUUID();
		ReplyCreateRequest request = new ReplyCreateRequest();
		request.setTweetId(this.tweetId);
		request.setMediaIds(new UUID[] { mediaId });
		ReplyEntity entity = ReplyEntity.builder().id(UUID.randomUUID()).build();

		given(this.tweetService.getTweetSummary(this.tweetId)).willReturn(Mono.just(new TweetDto()));
		given(this.userService.mediaExists(mediaId)).willReturn(Mono.just(false));
		// validateMediaIds(...).then(save(...)) assembles the save publisher eagerly, so
		// the mapper/repo must return non-null even though the error short-circuits before
		// the save is ever subscribed.
		given(this.replyMapper.mapRequestToEntity(request)).willReturn(entity);
		given(this.replyRepository.save(entity)).willReturn(Mono.just(entity));

		StepVerifier.create(this.replyService.createReply(request))
			.expectErrorMatches(e -> e instanceof ResponseStatusException rse
					&& rse.getStatusCode() == HttpStatus.BAD_REQUEST)
			.verify();

		// The save publisher was assembled but never subscribed: the count cache is the
		// next stage and must not run.
		verify(this.countCache, never()).increment(any());
	}

	@Test
	void createReply_NoMedia_SkipsMediaRoundTrip() {
		ReplyCreateRequest request = new ReplyCreateRequest();
		request.setTweetId(this.tweetId);
		request.setMediaIds(new UUID[0]);
		ReplyEntity entity = ReplyEntity.builder().id(UUID.randomUUID()).build();
		ReplyDto dto = new ReplyDto();

		given(this.tweetService.getTweetSummary(this.tweetId)).willReturn(Mono.just(new TweetDto()));
		given(this.replyMapper.mapRequestToEntity(request)).willReturn(entity);
		given(this.replyRepository.save(entity)).willReturn(Mono.just(entity));
		given(this.countCache.increment(anyString())).willReturn(Mono.empty());
		given(this.replyMapper.mapEntityToCreationDto(entity)).willReturn(dto);

		StepVerifier.create(this.replyService.createReply(request)).expectNext(dto).verifyComplete();

		verify(this.userService, never()).mediaExists(any());
	}

	@Test
	void getReplyCountsForTweets_DelegatesToCacheGetAll() {
		List<UUID> ids = List.of(this.tweetId);
		Map<UUID, Long> expected = Map.of(this.tweetId, 4L);
		given(this.countCache.getAll(any(), any(), any())).willReturn(Mono.just(expected));

		StepVerifier.create(this.replyService.getReplyCountsForTweets(ids)).expectNext(expected).verifyComplete();

		verify(this.countCache).getAll(any(), any(), any());
	}

	@Test
	void getReplyCountsForTweets_CacheMiss_LoaderQueriesGroupByAndCollectsTotals() {
		// Drive the real GROUP BY loader the service hands to getAll: countByTweetIdIn →
		// collectMap(tweetId, total), exercising the loader lambda itself, not just the delegation.
		UUID otherId = UUID.randomUUID();
		List<UUID> ids = List.of(this.tweetId, otherId);
		given(this.replyRepository.countByTweetIdIn(ids))
			.willReturn(Flux.just(new TweetCount(this.tweetId, 4L), new TweetCount(otherId, 9L)));
		given(this.countCache.getAll(any(), any(), any())).willAnswer(invocation -> {
			Function<List<UUID>, Mono<Map<UUID, Long>>> loader = invocation.getArgument(2);
			return loader.apply(ids);
		});

		StepVerifier.create(this.replyService.getReplyCountsForTweets(ids))
			.assertNext(counts -> assertThat(counts).containsEntry(this.tweetId, 4L).containsEntry(otherId, 9L))
			.verifyComplete();
	}

	@Test
	void getTopRepliesForTweets_Empty_ShortCircuitsToEmptyMap() {
		StepVerifier.create(this.replyService.getTopRepliesForTweets(List.of())).expectNext(Map.of()).verifyComplete();

		verify(this.topReplyCache, never()).getAll(any(), any(), any());
	}

	@Test
	void getTopRepliesForTweets_Null_ShortCircuitsToEmptyMap() {
		StepVerifier.create(this.replyService.getTopRepliesForTweets(null)).expectNext(Map.of()).verifyComplete();
	}

	@Test
	@SuppressWarnings("unchecked")
	void getTopRepliesForTweets_LoadsMissesAndResolvesUserNames() {
		// The cache's getAll is stubbed to invoke the supplied loader so the
		// loadTopRepliesForTweets mapping (per-row ReplyDto + userName) is exercised.
		UUID replyId = UUID.randomUUID();
		UUID authorId = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 0, 0);
		TopReply row = new TopReply(this.tweetId, replyId, authorId, "hi", createdAt, 9L);

		given(this.topReplyCache.getAll(any(), any(), any())).willAnswer(invocation -> {
			Function<List<UUID>, Mono<Map<UUID, ReplyDto>>> loader = invocation.getArgument(2);
			return loader.apply(List.of(this.tweetId));
		});
		given(this.replyRepository.findTopRepliesByLikesForTweetIds(anyList())).willReturn(Flux.just(row));
		given(this.userService.getUserSummary(authorId))
			.willReturn(Mono.just(new UserDto(authorId, "bob", false, createdAt)));

		StepVerifier.create(this.replyService.getTopRepliesForTweets(List.of(this.tweetId))).assertNext(map -> {
			assertThat(map).containsKey(this.tweetId);
			assertThat(map.get(this.tweetId).getUserName()).isEqualTo("bob");
			assertThat(map.get(this.tweetId).getId()).isEqualTo(replyId);
		}).verifyComplete();
	}

}
