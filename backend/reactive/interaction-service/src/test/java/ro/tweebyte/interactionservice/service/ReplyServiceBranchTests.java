/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.UUID;

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
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.ReplyUpdateRequest;
import ro.tweebyte.interactionservice.repository.ReplyRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Branch-coverage suite for ReplyService — covers the unauthorized / not-found branches
 * (lines 43, 54) and the empty-top-reply switchIfEmpty branch.
 */
@ExtendWith(MockitoExtension.class)
class ReplyServiceBranchTests {

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

	private UUID otherUserId;

	private UUID replyId;

	private ReplyEntity replyEntity;

	@BeforeEach
	void setUp() {
		this.userId = UUID.randomUUID();
		this.otherUserId = UUID.randomUUID();
		this.replyId = UUID.randomUUID();
		this.replyEntity = ReplyEntity.builder().id(this.replyId).userId(this.userId).build();
	}

	@Test
	void updateReply_DifferentUser_ErrorsForbidden() {
		// Covers the false-branch of "reply.userId.equals(request.userId)" in
		// ReplyService.updateReply: an existing reply owned by a different user now
		// raises ResponseStatusException(FORBIDDEN), not IllegalArgumentException.
		ReplyUpdateRequest req = new ReplyUpdateRequest();
		req.setId(this.replyId);
		req.setUserId(this.otherUserId);
		req.setContent("hi");

		given(this.replyRepository.findById(this.replyId)).willReturn(Mono.just(this.replyEntity));

		StepVerifier.create(this.replyService.updateReply(req))
			.expectErrorMatches(e -> e instanceof ResponseStatusException
					&& ((ResponseStatusException) e).getStatusCode() == HttpStatus.FORBIDDEN)
			.verify();

		verify(this.replyRepository, never()).save(any());
	}

	@Test
	void deleteReply_DifferentUser_ErrorsForbidden() {
		// Covers the false-branch of "reply.userId.equals(userId)" in
		// ReplyService.deleteReply: an existing reply owned by a different user now
		// raises ResponseStatusException(FORBIDDEN), not IllegalArgumentException.
		given(this.replyRepository.findById(this.replyId)).willReturn(Mono.just(this.replyEntity));

		StepVerifier.create(this.replyService.deleteReply(this.otherUserId, this.replyId))
			.expectErrorMatches(e -> e instanceof ResponseStatusException
					&& ((ResponseStatusException) e).getStatusCode() == HttpStatus.FORBIDDEN)
			.verify();

		verify(this.replyRepository, never()).deleteById(any(UUID.class));
	}

	@Test
	void getTopReplyForTweet_NoReplies_ReturnsEmptyDto() {
		// Covers the switchIfEmpty branch in getTopReplyForTweet — the source
		// flux is empty so the .next() yields empty and the switchIfEmpty
		// emits a default ReplyDto.
		UUID tweetId = UUID.randomUUID();
		given(this.topReplyCache.get(anyString(), any())).willAnswer(invocation -> {
			Mono<ReplyDto> loader = invocation.getArgument(1);
			return loader.switchIfEmpty(Mono.just(new ReplyDto()));
		});
		given(this.replyRepository.findTopReplyByLikesForTweetId(tweetId)).willReturn(Flux.empty());

		StepVerifier.create(this.replyService.getTopReplyForTweet(tweetId))
			.expectNextMatches(dto -> dto != null && dto.getContent() == null && dto.getId() == null)
			.verifyComplete();

		verify(this.userService, never()).getUserSummary(any());
	}

	@Test
	void updateReply_NotFound_RaisesError() {
		// When findById emits empty, switchIfEmpty raises
		// IllegalArgumentException("Unauthorized or reply not found") so the
		// controller surfaces a non-2xx — same observable behaviour as the
		// async stack on the same code path.
		ReplyUpdateRequest req = new ReplyUpdateRequest();
		req.setId(this.replyId);
		req.setUserId(this.userId);
		given(this.replyRepository.findById(this.replyId)).willReturn(Mono.empty());

		StepVerifier.create(this.replyService.updateReply(req))
			.expectErrorMatches(t -> t instanceof IllegalArgumentException && t.getMessage().contains("not found"))
			.verify();

		verify(this.replyRepository, never()).save(any());
	}

}
