/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.service;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.exception.TweetNotFoundException;
import ro.tweebyte.tweetservice.mapper.HashtagMapper;
import ro.tweebyte.tweetservice.mapper.MentionMapper;
import ro.tweebyte.tweetservice.model.TweetUpdateRequest;
import ro.tweebyte.tweetservice.repository.HashtagRepository;
import ro.tweebyte.tweetservice.repository.TweetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Covers the owner-scoped {@code deleteOwnedTweet} path and the mention-reconcile
 * branch where an already-present mention username is left untouched — both untouched by
 * {@link TweetUpdateServiceTests}. Mention-username resolution itself moved out of this
 * service (to {@code TweetService}), so its error-propagation branch is covered by
 * {@code TweetServiceTests} instead.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TweetUpdateServiceBranchTests {

	@Mock
	private TweetRepository tweetRepository;

	@Mock
	private HashtagRepository hashtagRepository;

	@Mock
	private HashtagMapper hashtagMapper;

	@Mock
	private MentionMapper mentionMapper;

	@InjectMocks
	private TweetUpdateService tweetUpdateService;

	private UUID tweetId;

	private UUID userId;

	@BeforeEach
	void setUp() {
		this.tweetId = UUID.randomUUID();
		this.userId = UUID.randomUUID();
	}

	@Test
	void deleteOwnedTweet_deletesWhenOwnerMatches() {
		TweetEntity tweet = TweetEntity.builder().id(this.tweetId).userId(this.userId).build();
		given(this.tweetRepository.findByIdAndUserId(this.tweetId, this.userId)).willReturn(Optional.of(tweet));

		this.tweetUpdateService.deleteOwnedTweet(this.userId, this.tweetId);

		verify(this.tweetRepository).delete(tweet);
	}

	@Test
	void deleteOwnedTweet_throwsWhenMissingOrNotOwner() {
		given(this.tweetRepository.findByIdAndUserId(this.tweetId, this.userId)).willReturn(Optional.empty());

		assertThatThrownBy(() -> this.tweetUpdateService.deleteOwnedTweet(this.userId, this.tweetId))
			.isInstanceOf(TweetNotFoundException.class);
	}

	@Test
	void updateTweet_skipsAlreadyPresentMentionUsername() {
		// existingTexts already contains "alice" → the `continue` branch is taken
		// and the resolved map is never consulted for it (no duplicate row).
		TweetEntity tweet = TweetEntity.builder()
			.id(this.tweetId)
			.userId(this.userId)
			.content("old")
			.mentions(new HashSet<>())
			.hashtags(new HashSet<>())
			.build();
		tweet.getMentions()
			.add(ro.tweebyte.tweetservice.entity.MentionEntity.builder()
				.id(UUID.randomUUID())
				.userId(UUID.randomUUID())
				.text("alice")
				.build());
		given(this.tweetRepository.findByIdAndUserId(this.tweetId, this.userId)).willReturn(Optional.of(tweet));

		TweetUpdateRequest request = new TweetUpdateRequest();
		request.setId(this.tweetId);
		request.setUserId(this.userId);
		request.setContent("hi @alice");

		assertThatCode(() -> this.tweetUpdateService.updateTweetWithRelations(request, Map.of("alice", UUID.randomUUID())))
			.doesNotThrowAnyException();

		// "alice" was already present, so no second row is added.
		assertThat(tweet.getMentions()).hasSize(1);
	}

}
