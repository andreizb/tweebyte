/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.interactionservice.cache.CountCache;
import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.mapper.LikeMapper;
import ro.tweebyte.interactionservice.model.LikeDto;
import ro.tweebyte.interactionservice.model.LikeableType;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LikeServiceTests {

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

	private LikeDto likeDto;

	private TweetDto tweetDto;

	private UserDto userDto;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		this.userId = UUID.randomUUID();
		this.tweetId = UUID.randomUUID();
		this.replyId = UUID.randomUUID();

		this.likeDto = new LikeDto();
		this.tweetDto = new TweetDto();
		this.userDto = new UserDto();

		// unlikeTweet wraps find+delete inside a reactive tx; stub as identity so tests
		// observe the unwrapped reactive behaviour.
		given(this.txOperator.transactional(any(Mono.class)))
			.willAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void getUserLikes_Success() {
		given(this.likeRepository.findByUserIdAndLikeableType(eq(this.userId), eq(LikeableType.TWEET.name()), anyInt(),
				anyInt()))
			.willReturn(Flux.just(
					new LikeEntity(UUID.randomUUID(), LocalDateTime.now(), true, this.userId, this.tweetId, "TWEET")));
		given(this.tweetService.getTweetSummary(any(UUID.class))).willReturn(Mono.just(this.tweetDto));
		given(this.likeMapper.mapToDto(any(), eq(this.tweetDto))).willReturn(this.likeDto);

		StepVerifier.create(this.likeService.getUserLikes(this.userId, 0, 10)).expectNext(this.likeDto).verifyComplete();

		verify(this.likeRepository, times(1)).findByUserIdAndLikeableType(this.userId, LikeableType.TWEET.name(), 10, 0);
		verify(this.tweetService, atLeastOnce()).getTweetSummary(any(UUID.class));
		verify(this.likeMapper, atLeastOnce()).mapToDto(any(), eq(this.tweetDto));
	}

	@Test
	void getTweetLikes_Success() {
		given(this.likeRepository.findByLikeableIdAndLikeableType(eq(this.tweetId), eq(LikeableType.TWEET.name()),
				anyInt(), anyInt()))
			.willReturn(Flux.just(
					new LikeEntity(UUID.randomUUID(), LocalDateTime.now(), true, this.userId, this.tweetId, "TWEET")));
		given(this.userService.getUserSummary(any(UUID.class))).willReturn(Mono.just(this.userDto));
		given(this.likeMapper.mapToDto(any(), eq(this.userDto))).willReturn(this.likeDto);

		StepVerifier.create(this.likeService.getTweetLikes(this.tweetId, 0, 10)).expectNext(this.likeDto).verifyComplete();

		verify(this.likeRepository, times(1)).findByLikeableIdAndLikeableType(this.tweetId, LikeableType.TWEET.name(), 10,
				0);
		verify(this.userService, atLeastOnce()).getUserSummary(any(UUID.class));
		verify(this.likeMapper, atLeastOnce()).mapToDto(any(), eq(this.userDto));
	}

	@Test
	void getTweetLikesCount_Success() {
		given(this.countCache.get(anyString(), any())).willAnswer(invocation -> invocation.getArgument(1));
		given(this.likeRepository.countByLikeableIdAndLikeableType(this.tweetId, LikeableType.TWEET.name()))
			.willReturn(Mono.just(5L));

		StepVerifier.create(this.likeService.getTweetLikesCount(this.tweetId)).expectNext(5L).verifyComplete();

		verify(this.likeRepository, times(1)).countByLikeableIdAndLikeableType(this.tweetId, LikeableType.TWEET.name());
	}

	@Test
	void likeTweet_Success() {
		given(this.tweetService.getTweetSummary(this.tweetId)).willReturn(Mono.just(this.tweetDto));
		given(this.likeRepository.save(any())).willReturn(Mono.just(new LikeEntity()));
		given(this.likeMapper.mapEntityToDto(any())).willReturn(this.likeDto);
		given(this.countCache.increment(anyString())).willReturn(Mono.empty());

		StepVerifier.create(this.likeService.likeTweet(this.userId, this.tweetId))
			.expectNext(this.likeDto)
			.verifyComplete();

		verify(this.tweetService, times(1)).getTweetSummary(this.tweetId);
		verify(this.likeRepository, times(1)).save(any());
		verify(this.likeMapper, times(1)).mapEntityToDto(any());
	}

	@Test
	void unlikeTweet_Success() {
		// A row exists, so the delete fires and the cached count is decremented.
		given(this.likeRepository.findByUserIdAndLikeableIdAndLikeableType(this.userId, this.tweetId,
				LikeableType.TWEET.name()))
			.willReturn(Mono.just(new LikeEntity()));
		given(this.likeRepository.deleteByUserIdAndLikeableIdAndLikeableType(this.userId, this.tweetId,
				LikeableType.TWEET.name()))
			.willReturn(Mono.empty());
		given(this.countCache.decrement(anyString())).willReturn(Mono.empty());

		StepVerifier.create(this.likeService.unlikeTweet(this.userId, this.tweetId)).verifyComplete();

		verify(this.likeRepository, times(1)).deleteByUserIdAndLikeableIdAndLikeableType(this.userId, this.tweetId,
				LikeableType.TWEET.name());
		verify(this.countCache, times(1)).decrement(anyString());
	}

	@Test
	void unlikeTweet_NoExistingRow_SkipsDeleteAndDecrement() {
		// A phantom unlike finds no row: nothing is deleted and the count is left alone.
		given(this.likeRepository.findByUserIdAndLikeableIdAndLikeableType(this.userId, this.tweetId,
				LikeableType.TWEET.name()))
			.willReturn(Mono.empty());

		StepVerifier.create(this.likeService.unlikeTweet(this.userId, this.tweetId)).verifyComplete();

		verify(this.likeRepository, never()).deleteByUserIdAndLikeableIdAndLikeableType(this.userId, this.tweetId,
				LikeableType.TWEET.name());
		verify(this.countCache, never()).decrement(anyString());
	}

	@Test
	void likeReply_Success() {
		given(this.replyRepository.findById(this.replyId)).willReturn(Mono.just(new ReplyEntity()));
		given(this.likeRepository.save(any())).willReturn(Mono.just(new LikeEntity()));
		given(this.likeMapper.mapEntityToDto(any())).willReturn(this.likeDto);

		StepVerifier.create(this.likeService.likeReply(this.userId, this.replyId))
			.expectNext(this.likeDto)
			.verifyComplete();

		verify(this.replyRepository, times(1)).findById(this.replyId);
		verify(this.likeRepository, times(1)).save(any());
		verify(this.likeMapper, times(1)).mapEntityToDto(any());
	}

	@Test
	void unlikeReply_Success() {
		given(this.likeRepository.deleteByUserIdAndLikeableIdAndLikeableType(this.userId, this.replyId,
				LikeableType.REPLY.name()))
			.willReturn(Mono.empty());

		StepVerifier.create(this.likeService.unlikeReply(this.userId, this.replyId)).verifyComplete();

		verify(this.likeRepository, times(1)).deleteByUserIdAndLikeableIdAndLikeableType(this.userId, this.replyId,
				LikeableType.REPLY.name());
	}

}
