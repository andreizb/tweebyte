/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.entity.MentionEntity;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Covers the rich tweet-update path used by the {@code tweet-update} workload.
 * Validates:
 * <ul>
 * <li>hashtag tokens in updated content replace existing hashtag links</li>
 * <li>content with no hashtags clears the existing collection</li>
 * <li>mention tokens replace existing mention rows correctly</li>
 * <li>no-token update still updates content</li>
 * <li>missing tweet → 404</li>
 * <li>unresolvable mention username → skipped silently (matches reactive)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TweetUpdateServiceTests {

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

	private TweetEntity tweet;

	private UUID tweetId;

	private UUID userId;

	@BeforeEach
	void setUp() {
		this.tweetId = UUID.randomUUID();
		this.userId = UUID.randomUUID();
		this.tweet = TweetEntity.builder()
			.id(this.tweetId)
			.userId(this.userId)
			.content("benchmark old tweet 1 #old_1 #common_1")
			.mentions(new HashSet<>())
			.hashtags(new HashSet<>())
			.build();

		// Pre-populate existing hashtags collection so the "replace" tests have
		// something to clear.
		this.tweet.getHashtags().add(HashtagEntity.builder().id(UUID.randomUUID()).text("old_1").build());
		this.tweet.getHashtags().add(HashtagEntity.builder().id(UUID.randomUUID()).text("common_1").build());
	}

	private TweetUpdateRequest request(String content) {
		TweetUpdateRequest r = new TweetUpdateRequest();
		r.setId(this.tweetId);
		r.setUserId(this.userId);
		r.setContent(content);
		return r;
	}

	@Test
	void hashtagReplacement_replacesLinks() {
		given(this.tweetRepository.findByIdAndUserId(this.tweetId, this.userId)).willReturn(Optional.of(this.tweet));
		HashtagEntity newTag = HashtagEntity.builder().id(UUID.randomUUID()).text("new_1").build();
		HashtagEntity commonTag = HashtagEntity.builder().id(UUID.randomUUID()).text("common_1").build();
		given(this.hashtagRepository.findByTextIn(any())).willReturn(List.of(newTag, commonTag));

		this.tweetUpdateService.updateTweetWithRelations(request("benchmark new tweet 1 #new_1 #common_1"), Map.of());

		assertThat(this.tweet.getContent()).isEqualTo("benchmark new tweet 1 #new_1 #common_1");
		Set<String> hashtagTexts = new HashSet<>();
		this.tweet.getHashtags().forEach(h -> hashtagTexts.add(h.getText()));
		assertThat(hashtagTexts).isEqualTo(Set.of("new_1", "common_1"));
	}

	@Test
	void hashtagRebuild_createsMissingHashtags() {
		given(this.tweetRepository.findByIdAndUserId(this.tweetId, this.userId)).willReturn(Optional.of(this.tweet));
		// Only one of the two requested tags exists in DB
		HashtagEntity existing = HashtagEntity.builder().id(UUID.randomUUID()).text("common_1").build();
		given(this.hashtagRepository.findByTextIn(any())).willReturn(List.of(existing));
		// hashtagMapper should be called for the missing "new_1"
		HashtagEntity created = HashtagEntity.builder().text("new_1").build();
		given(this.hashtagMapper.mapTextToEntity("new_1")).willReturn(created);
		given(this.hashtagRepository.save(created))
			.willReturn(HashtagEntity.builder().id(UUID.randomUUID()).text("new_1").build());

		this.tweetUpdateService.updateTweetWithRelations(request("benchmark new tweet 1 #new_1 #common_1"), Map.of());

		verify(this.hashtagMapper).mapTextToEntity("new_1");
		verify(this.hashtagRepository).save(created);
	}

	@Test
	void noHashtagTokens_clearsCollection() {
		given(this.tweetRepository.findByIdAndUserId(this.tweetId, this.userId)).willReturn(Optional.of(this.tweet));

		this.tweetUpdateService.updateTweetWithRelations(request("updated content with no tokens"), Map.of());

		assertThat(this.tweet.getContent()).isEqualTo("updated content with no tokens");
		assertThat(this.tweet.getHashtags()).as("hashtag collection should be cleared").isEmpty();
		// No lookups needed when token set is empty
		verify(this.hashtagRepository, never()).findByTextIn(any());
	}

	@Test
	void mentionRebuild_addsResolvedMentionRows() {
		given(this.tweetRepository.findByIdAndUserId(this.tweetId, this.userId)).willReturn(Optional.of(this.tweet));
		UUID aliceId = UUID.randomUUID();
		UUID bobId = UUID.randomUUID();
		given(this.mentionMapper.mapFieldsToEntity(eq(aliceId), eq("alice"), any(TweetEntity.class)))
			.willReturn(MentionEntity.builder().userId(aliceId).text("alice").build());
		given(this.mentionMapper.mapFieldsToEntity(eq(bobId), eq("bob"), any(TweetEntity.class)))
			.willReturn(MentionEntity.builder().userId(bobId).text("bob").build());

		this.tweetUpdateService.updateTweetWithRelations(request("hello @alice and @bob"),
				Map.of("alice", aliceId, "bob", bobId));

		Set<String> mentionTexts = new HashSet<>();
		this.tweet.getMentions().forEach(m -> mentionTexts.add(m.getText()));
		assertThat(mentionTexts).isEqualTo(Set.of("alice", "bob"));
	}

	@Test
	void mentionRebuild_skipsUnresolvableUsernames() {
		// Mentions support is exercised by the functional-equivalence tests
		// while the perf payload contains no at-tokens. Here we just verify an
		// unresolved username (absent from the pre-resolved map) is dropped,
		// leaving no mention row, without blowing up the request.
		given(this.tweetRepository.findByIdAndUserId(this.tweetId, this.userId)).willReturn(Optional.of(this.tweet));

		assertThatCode(() -> this.tweetUpdateService.updateTweetWithRelations(request("hi @ghost #common_1"), Map.of()))
			.doesNotThrowAnyException();

		assertThat(this.tweet.getMentions()).as("ghost mention should be silently skipped").isEmpty();
	}

	@Test
	void missingTweet_throwsTweetNotFoundException() {
		given(this.tweetRepository.findByIdAndUserId(this.tweetId, this.userId)).willReturn(Optional.empty());
		TweetUpdateRequest req = request("anything #new_1");

		assertThatThrownBy(() -> this.tweetUpdateService.updateTweetWithRelations(req, Map.of()))
			.isInstanceOf(TweetNotFoundException.class);
	}

}
