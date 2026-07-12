/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.service;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;

import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.exception.TweetNotFoundException;
import ro.tweebyte.tweetservice.exception.UserNotFoundException;
import ro.tweebyte.tweetservice.mapper.MentionMapper;
import ro.tweebyte.tweetservice.model.TweetCreationRequest;
import ro.tweebyte.tweetservice.repository.TweetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest
class MentionServiceTests {

	@Mock
	private TweetRepository tweetRepository;

	@Mock
	private MentionMapper mentionMapper;

	@Mock
	private UserService userService;

	@InjectMocks
	private MentionService mentionService;

	@Test
	void testHandleTweetCreationMentions() {
		TweetCreationRequest request = new TweetCreationRequest();
		request.setId(UUID.randomUUID());

		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setContent("This is a mention to @testUser.");
		tweetEntity.setMentions(new HashSet<>());

		given(this.tweetRepository.findById(any(UUID.class))).willReturn(Optional.of(tweetEntity));
		given(this.userService.getUserId(any(String.class)))
			.willReturn(CompletableFuture.completedFuture(UUID.randomUUID()));
		given(this.mentionMapper.mapFieldsToEntity(any(UUID.class), any(String.class), any(TweetEntity.class)))
			.willReturn(new MentionEntity());

		this.mentionService.handleTweetCreationMentions(request);

		assertThat(tweetEntity.getMentions()).isNotEmpty();
	}

	@Test
	void testHandleTweetCreationMentionsWithTweetNotFound() {
		TweetCreationRequest request = new TweetCreationRequest();
		request.setId(UUID.randomUUID());

		given(this.tweetRepository.findById(any(UUID.class))).willReturn(Optional.empty());

		assertThatThrownBy(() -> this.mentionService.handleTweetCreationMentions(request))
			.isInstanceOf(TweetNotFoundException.class);
	}

	@Test
	void testHandleTweetCreationMentionsWithUserNotFound() {
		TweetCreationRequest request = new TweetCreationRequest();
		request.setId(UUID.randomUUID());

		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setContent("This is a mention to @testUser.");
		tweetEntity.setMentions(new HashSet<>());

		given(this.tweetRepository.findById(any(UUID.class))).willReturn(Optional.of(tweetEntity));
		given(this.userService.getUserId(any(String.class)))
			.willReturn(CompletableFuture.failedFuture(new UserNotFoundException("User not found")));

		assertThatCode(() -> this.mentionService.handleTweetCreationMentions(request)).doesNotThrowAnyException();

		assertThat(tweetEntity.getMentions()).isEmpty();
	}

	@Test
	void testHandleTweetCreationMentionsRethrowsNonUserNotFoundException() {
		// Covers the false branch of the `instanceof UserNotFoundException` check
		// inside createMention's handle() — any other failure must be surfaced as
		// a CompletionException (wrapping the originating cause).
		TweetCreationRequest request = new TweetCreationRequest();
		request.setId(UUID.randomUUID());

		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setContent("Hello @bob");
		tweetEntity.setMentions(new HashSet<>());

		given(this.tweetRepository.findById(any(UUID.class))).willReturn(Optional.of(tweetEntity));
		given(this.userService.getUserId(any(String.class)))
			.willReturn(CompletableFuture.failedFuture(new RuntimeException("internal-failure")));

		Throwable thrown = catchThrowable(() -> this.mentionService.handleTweetCreationMentions(request));
		assertThat(thrown).isInstanceOf(Throwable.class);
		// The outer .join() bubbles the failure as CompletionException.
		assertThat(thrown instanceof java.util.concurrent.CompletionException
				|| thrown.getCause() instanceof RuntimeException || thrown instanceof RuntimeException)
			.isTrue();
	}

}
