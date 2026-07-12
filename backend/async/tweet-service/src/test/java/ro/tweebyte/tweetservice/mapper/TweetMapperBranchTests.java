/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.mapper;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.model.ReplyDto;
import ro.tweebyte.tweetservice.model.TweetDto;
import ro.tweebyte.tweetservice.model.UserDto;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the hand-written {@link TweetMapper} overloads (user attach + the two feed
 * enrichment overloads), including the null/non-null hashtag and mention branches that
 * {@link TweetMapperTests} leaves uncovered. The element collaborators (Mention/Hashtag
 * mappers) are wired into the generated impl by hand, since the non-Spring test harness
 * does not field-autowire them.
 */
class TweetMapperBranchTests {

	private TweetMapper tweetMapper;

	@BeforeEach
	void setUp() {
		TweetMapperImpl impl = new TweetMapperImpl();
		ReflectionTestUtils.setField(impl, "hashtagMapper", new HashtagMapperImpl());
		ReflectionTestUtils.setField(impl, "mentionMapper", new MentionMapperImpl());
		this.tweetMapper = impl;
	}

	private TweetEntity sampleEntity() {
		TweetEntity entity = new TweetEntity();
		entity.setId(UUID.randomUUID());
		entity.setUserId(UUID.randomUUID());
		entity.setContent("Tweet Content");
		return entity;
	}

	private HashtagEntity hashtag(String text) {
		return HashtagEntity.builder().id(UUID.randomUUID()).text(text).build();
	}

	private MentionEntity mention(String text) {
		return MentionEntity.builder().id(UUID.randomUUID()).userId(UUID.randomUUID()).text(text).build();
	}

	@Test
	void mapEntityToDtoWithUser_attachesUser() {
		TweetEntity entity = sampleEntity();
		UserDto user = new UserDto();
		user.setUserName("alice");

		TweetDto dto = this.tweetMapper.mapEntityToDto(entity, user);

		assertThat(dto.getId()).isEqualTo(entity.getId());
		assertThat(dto.getContent()).isEqualTo("Tweet Content");
		assertThat(dto.getUser()).isSameAs(user);
	}

	@Test
	void mapFeedEnrichment_withHashtagsAndMentions() {
		TweetEntity entity = sampleEntity();
		ReplyDto topReply = new ReplyDto();

		TweetDto dto = this.tweetMapper.mapEntityToDto(entity, 10L, 5L, 7L, topReply, List.of(hashtag("spring")),
				List.of(mention("alice")));

		assertThat(dto.getId()).isEqualTo(entity.getId());
		assertThat(dto.getLikesCount()).isEqualTo(10L);
		assertThat(dto.getRepliesCount()).isEqualTo(5L);
		assertThat(dto.getRetweetsCount()).isEqualTo(7L);
		assertThat(dto.getTopReply()).isSameAs(topReply);
		assertThat(dto.getHashtags()).extracting("text").containsExactly("spring");
		assertThat(dto.getMentions()).extracting("text").containsExactly("alice");
	}

	@Test
	void mapFeedEnrichment_withNullHashtagsAndMentions() {
		TweetEntity entity = sampleEntity();

		TweetDto dto = this.tweetMapper.mapEntityToDto(entity, 1L, 2L, 3L, new ReplyDto(), null, null);

		assertThat(dto.getLikesCount()).isEqualTo(1L);
		assertThat(dto.getHashtags()).isNull();
		assertThat(dto.getMentions()).isNull();
	}

	@Test
	void mapSingleTweetEnrichment_withRepliesHashtagsMentions() {
		TweetEntity entity = sampleEntity();
		ReplyDto reply = new ReplyDto();
		reply.setContent("nice");
		List<ReplyDto> replies = List.of(reply);

		TweetDto dto = this.tweetMapper.mapEntityToDto(entity, 4L, 8L, 12L, replies, List.of(mention("bob")),
				List.of(hashtag("java")));

		assertThat(dto.getId()).isEqualTo(entity.getId());
		assertThat(dto.getLikesCount()).isEqualTo(4L);
		assertThat(dto.getRepliesCount()).isEqualTo(8L);
		assertThat(dto.getRetweetsCount()).isEqualTo(12L);
		assertThat(dto.getReplies()).isEqualTo(replies);
		assertThat(dto.getHashtags()).extracting("text").containsExactly("java");
		assertThat(dto.getMentions()).extracting("text").containsExactly("bob");
	}

	@Test
	void mapSingleTweetEnrichment_withNullHashtagsAndMentions() {
		TweetEntity entity = sampleEntity();
		List<ReplyDto> replies = List.of();

		TweetDto dto = this.tweetMapper.mapEntityToDto(entity, 0L, 0L, 0L, replies, null, null);

		assertThat(dto.getReplies()).isEqualTo(replies);
		assertThat(dto.getHashtags()).isNull();
		assertThat(dto.getMentions()).isNull();
	}

}
