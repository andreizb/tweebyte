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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.exception.TweetNotFoundException;
import ro.tweebyte.interactionservice.model.Status;
import ro.tweebyte.interactionservice.repository.FollowRepository;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CleanupServiceTests {

	@Mock
	private TweetService tweetService;

	@Mock
	private FollowRepository followRepository;

	@Mock
	private LikeRepository likeRepository;

	@Mock
	private ReplyRepository replyRepository;

	@Mock
	private RetweetRepository retweetRepository;

	private CleanupService cleanupService;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		this.cleanupService = new CleanupService(this.tweetService, this.followRepository, this.likeRepository,
				this.replyRepository, this.retweetRepository);
	}

	@Test
	void cleanupRejectedFollowRequests_Success() {
		given(this.followRepository.findByStatus(Status.REJECTED.name()))
			.willReturn(Flux.just(new FollowEntity(UUID.randomUUID(), LocalDateTime.now(), true, UUID.randomUUID(),
					UUID.randomUUID(), "REJECTED")));
		given(this.followRepository.deleteAll(anyList())).willReturn(Mono.empty());

		StepVerifier.create(this.cleanupService.cleanupRejectedFollowRequests()).verifyComplete();

		verify(this.followRepository, times(1)).findByStatus(Status.REJECTED.name());
		verify(this.followRepository, times(1)).deleteAll(anyList());
	}

	@Test
	void cleanupOrphanLikes_Success() {
		UUID likeableId = UUID.randomUUID();

		given(this.likeRepository.findAll()).willReturn(Flux.just(
				new LikeEntity(UUID.randomUUID(), LocalDateTime.now(), true, likeableId, UUID.randomUUID(), "TWEET")));
		given(this.tweetService.getTweetSummary(any(UUID.class))).willReturn(Mono.empty())
			.willReturn(Mono.error(new TweetNotFoundException("Tweet not found")));
		given(this.likeRepository.deleteAll(anyList())).willReturn(Mono.empty());

		StepVerifier.create(this.cleanupService.cleanupOrphanLikes()).verifyComplete();

		verify(this.likeRepository, times(1)).findAll();
		verify(this.tweetService, atLeastOnce()).getTweetSummary(any(UUID.class));
		verify(this.likeRepository, times(1)).deleteAll(anyList());
	}

	@Test
	void cleanupOrphanReplies_Success() {
		UUID tweetId = UUID.randomUUID();

		given(this.replyRepository.findAll()).willReturn(Flux.just(new ReplyEntity(UUID.randomUUID(),
				LocalDateTime.now(), tweetId, UUID.randomUUID(), "comment", null, true)));
		given(this.tweetService.getTweetSummary(any(UUID.class)))
			.willReturn(Mono.error(new TweetNotFoundException("Tweet not found")));
		given(this.replyRepository.deleteAll(anyList())).willReturn(Mono.empty());

		StepVerifier.create(this.cleanupService.cleanupOrphanReplies()).verifyComplete();

		verify(this.replyRepository, times(1)).findAll();
		verify(this.tweetService, atLeastOnce()).getTweetSummary(any(UUID.class));
		verify(this.replyRepository, times(1)).deleteAll(anyList());
	}

	@Test
	void cleanupOrphanRetweets_Success() {
		UUID tweetId = UUID.randomUUID();

		given(this.retweetRepository.findAll()).willReturn(Flux.just(new RetweetEntity(UUID.randomUUID(),
				LocalDateTime.now(), tweetId, UUID.randomUUID(), "comment", null, true)));
		given(this.tweetService.getTweetSummary(any(UUID.class)))
			.willReturn(Mono.error(new TweetNotFoundException("Tweet not found")));
		given(this.retweetRepository.deleteAll(anyList())).willReturn(Mono.empty());

		StepVerifier.create(this.cleanupService.cleanupOrphanRetweets()).verifyComplete();

		verify(this.retweetRepository, times(1)).findAll();
		verify(this.tweetService, atLeastOnce()).getTweetSummary(any(UUID.class));
		verify(this.retweetRepository, times(1)).deleteAll(anyList());
	}

}
