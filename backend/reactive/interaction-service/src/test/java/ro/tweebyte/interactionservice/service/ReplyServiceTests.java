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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.interactionservice.cache.CountCache;
import ro.tweebyte.interactionservice.cache.TopReplyCache;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.mapper.ReplyMapper;
import ro.tweebyte.interactionservice.model.ReplyCreateRequest;
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.ReplyUpdateRequest;
import ro.tweebyte.interactionservice.model.TopReply;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.ReplyRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReplyServiceTests {

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

	private UUID userId;

	private UUID tweetId;

	private UUID replyId;

	private ReplyEntity replyEntity;

	private ReplyDto replyDto;

	private ReplyCreateRequest createRequest;

	private ReplyUpdateRequest updateRequest;

	@BeforeEach
	void setUp() {
		this.userId = UUID.randomUUID();
		this.tweetId = UUID.randomUUID();
		this.replyId = UUID.randomUUID();

		this.replyEntity = ReplyEntity.builder()
			.id(this.replyId)
			.userId(this.userId)
			.tweetId(this.tweetId)
			.content("Test Reply")
			.build();

		this.replyDto = new ReplyDto();
		this.replyDto.setId(this.replyId);
		this.replyDto.setContent("Test Reply");

		this.createRequest = new ReplyCreateRequest();
		this.createRequest.setTweetId(this.tweetId);
		this.createRequest.setUserId(this.userId);
		this.createRequest.setContent("Test Reply");

		this.updateRequest = new ReplyUpdateRequest();
		this.updateRequest.setId(this.replyId);
		this.updateRequest.setUserId(this.userId);
		this.updateRequest.setContent("Updated Reply");
	}

	@Test
	void createReply_Success() {
		given(this.tweetService.getTweetSummary(this.tweetId)).willReturn(Mono.just(new TweetDto()));
		given(this.replyMapper.mapRequestToEntity(this.createRequest)).willReturn(this.replyEntity);
		given(this.replyRepository.save(any(ReplyEntity.class))).willReturn(Mono.just(this.replyEntity));
		given(this.replyMapper.mapEntityToCreationDto(any(ReplyEntity.class))).willReturn(this.replyDto);
		given(this.countCache.increment(anyString())).willReturn(Mono.empty());

		StepVerifier.create(this.replyService.createReply(this.createRequest))
			.expectNext(this.replyDto)
			.verifyComplete();

		verify(this.tweetService, times(1)).getTweetSummary(this.tweetId);
		verify(this.replyRepository, times(1)).save(this.replyEntity);
		verify(this.replyMapper, times(1)).mapEntityToCreationDto(this.replyEntity);
	}

	@Test
	void updateReply_Success() {
		given(this.replyRepository.findById(this.replyId)).willReturn(Mono.just(this.replyEntity));
		doNothing().when(this.replyMapper).mapRequestToEntity(this.updateRequest, this.replyEntity);
		given(this.replyRepository.save(any(ReplyEntity.class))).willReturn(Mono.just(this.replyEntity));

		StepVerifier.create(this.replyService.updateReply(this.updateRequest)).verifyComplete();

		verify(this.replyRepository, times(1)).findById(this.replyId);
		verify(this.replyMapper, times(1)).mapRequestToEntity(this.updateRequest, this.replyEntity);
		verify(this.replyRepository, times(1)).save(this.replyEntity);
	}

	@Test
	void deleteReply_Success() {
		given(this.replyRepository.findById(this.replyId)).willReturn(Mono.just(this.replyEntity));
		given(this.replyRepository.deleteById(this.replyId)).willReturn(Mono.empty());
		given(this.countCache.decrement(anyString())).willReturn(Mono.empty());

		StepVerifier.create(this.replyService.deleteReply(this.userId, this.replyId)).verifyComplete();

		verify(this.replyRepository, times(1)).findById(this.replyId);
		verify(this.replyRepository, times(1)).deleteById(this.replyId);
	}

	@Test
	void getRepliesForTweet_Success() {
		given(this.replyRepository.findByTweetIdOrderByCreatedAtDescIdDesc(eq(this.tweetId), anyInt(), anyInt()))
			.willReturn(Flux.just(this.replyEntity));
		given(this.userService.getUserSummary(this.userId))
			.willReturn(Mono.just(new UserDto(this.userId, "Test User", true, LocalDateTime.now())));
		given(this.replyMapper.mapEntityToDto(this.replyEntity, "Test User")).willReturn(this.replyDto);

		StepVerifier.create(this.replyService.getRepliesForTweet(this.tweetId, 0, 10))
			.expectNext(this.replyDto)
			.verifyComplete();

		verify(this.replyRepository, times(1)).findByTweetIdOrderByCreatedAtDescIdDesc(this.tweetId, 10, 0);
		verify(this.userService, times(1)).getUserSummary(this.userId);
		verify(this.replyMapper, times(1)).mapEntityToDto(this.replyEntity, "Test User");
	}

	@Test
	void getReplyCountForTweet_Success() {
		given(this.countCache.get(anyString(), any())).willAnswer(invocation -> invocation.getArgument(1));
		given(this.replyRepository.countByTweetId(this.tweetId)).willReturn(Mono.just(10L));

		StepVerifier.create(this.replyService.getReplyCountForTweet(this.tweetId)).expectNext(10L).verifyComplete();

		verify(this.replyRepository, times(1)).countByTweetId(this.tweetId);
	}

	@Test
	void getTopReplyForTweet_Success() {
		// Repository now returns the TopReply projection (carrying the aggregated
		// like_count); the service builds the ReplyDto directly and sets userName,
		// so ReplyMapper is no longer on this path.
		LocalDateTime createdAt = LocalDateTime.now();
		TopReply topReply = new TopReply(this.tweetId, this.replyId, this.userId, "Test Reply", createdAt, 7L);

		given(this.topReplyCache.get(anyString(), any())).willAnswer(invocation -> {
			Mono<ReplyDto> loader = invocation.getArgument(1);
			return loader.switchIfEmpty(Mono.just(new ReplyDto()));
		});
		given(this.replyRepository.findTopReplyByLikesForTweetId(this.tweetId)).willReturn(Flux.just(topReply));
		given(this.userService.getUserSummary(this.userId))
			.willReturn(Mono.just(new UserDto(this.userId, "Test User", true, LocalDateTime.now())));

		ReplyDto expected = new ReplyDto(this.replyId, this.userId, "Test Reply", createdAt, 7L)
			.setUserName("Test User");

		StepVerifier.create(this.replyService.getTopReplyForTweet(this.tweetId)).expectNext(expected).verifyComplete();

		verify(this.replyRepository, times(1)).findTopReplyByLikesForTweetId(this.tweetId);
		verify(this.userService, times(1)).getUserSummary(this.userId);
	}

}
