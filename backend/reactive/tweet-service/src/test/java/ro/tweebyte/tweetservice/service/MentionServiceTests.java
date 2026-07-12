/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.service;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.exception.TweetNotFoundException;
import ro.tweebyte.tweetservice.exception.UserNotFoundException;
import ro.tweebyte.tweetservice.mapper.MentionMapper;
import ro.tweebyte.tweetservice.model.TweetCreationRequest;
import ro.tweebyte.tweetservice.repository.MentionRepository;
import ro.tweebyte.tweetservice.repository.TweetRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MentionServiceTests {

	@InjectMocks
	private MentionService mentionService;

	@Mock
	private MentionRepository mentionRepository;

	@Mock
	private TweetRepository tweetRepository;

	@Mock
	private MentionMapper mentionMapper;

	@Mock
	private UserService userService;

	private TweetCreationRequest tweetRequest;

	private TweetEntity tweetEntity;

	@BeforeEach
	void setup() {
		this.tweetRequest = new TweetCreationRequest();
		this.tweetRequest.setId(UUID.randomUUID());
		this.tweetRequest.setContent("Hello @user1 @user2");

		this.tweetEntity = new TweetEntity();
		this.tweetEntity.setId(this.tweetRequest.getId());
		this.tweetEntity.setContent(this.tweetRequest.getContent());
	}

	@Test
	void handleTweetCreationMentions_Success() {
		given(this.tweetRepository.findById(this.tweetRequest.getId())).willReturn(Mono.just(this.tweetEntity));
		given(this.userService.getUserId(any())).willReturn(Mono.just(UUID.randomUUID()));
		given(this.mentionMapper.mapFieldsToEntity(any(), anyString(), any())).willReturn(new MentionEntity());
		given(this.mentionRepository.save(any(MentionEntity.class))).willReturn(Mono.just(new MentionEntity()));

		StepVerifier.create(this.mentionService.handleTweetCreationMentions(this.tweetRequest)).verifyComplete();

		verify(this.tweetRepository).findById(this.tweetRequest.getId());
		verify(this.userService, times(2)).getUserId(any());
		verify(this.mentionRepository, times(2)).save(any(MentionEntity.class));
	}

	@Test
	void handleTweetCreationMentions_TweetNotFound() {
		given(this.tweetRepository.findById(this.tweetRequest.getId())).willReturn(Mono.empty());

		StepVerifier.create(this.mentionService.handleTweetCreationMentions(this.tweetRequest))
			.expectError(TweetNotFoundException.class)
			.verify();

		verify(this.tweetRepository).findById(this.tweetRequest.getId());
		verifyNoInteractions(this.userService);
	}

	@Test
	void handleTweetCreationMentions_UserNotFoundIsSwallowed() {
		given(this.tweetRepository.findById(this.tweetRequest.getId())).willReturn(Mono.just(this.tweetEntity));
		given(this.userService.getUserId(any())).willReturn(Mono.error(new UserNotFoundException("nope")));

		StepVerifier.create(this.mentionService.handleTweetCreationMentions(this.tweetRequest)).verifyComplete();

		verify(this.userService, times(2)).getUserId(any());
		verify(this.mentionRepository, never()).save(any(MentionEntity.class));
	}

	@Test
	void handleTweetCreationMentions_GenericErrorPropagates() {
		given(this.tweetRepository.findById(this.tweetRequest.getId())).willReturn(Mono.just(this.tweetEntity));
		given(this.userService.getUserId(any())).willReturn(Mono.error(new IllegalStateException("boom")));

		StepVerifier.create(this.mentionService.handleTweetCreationMentions(this.tweetRequest))
			.expectError(IllegalStateException.class)
			.verify();
	}

}
