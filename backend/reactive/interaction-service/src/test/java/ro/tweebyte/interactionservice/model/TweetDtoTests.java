/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TweetDtoTests {

	@Test
	void getId() {
		UUID id = UUID.randomUUID();
		TweetDto tweetDto = new TweetDto();
		tweetDto.setId(id);
		assertThat(tweetDto.getId()).isEqualTo(id);
	}

	@Test
	void getUserId() {
		UUID userId = UUID.randomUUID();
		TweetDto tweetDto = new TweetDto();
		tweetDto.setUserId(userId);
		assertThat(tweetDto.getUserId()).isEqualTo(userId);
	}

	@Test
	void getContent() {
		String content = "Test tweet content";
		TweetDto tweetDto = new TweetDto();
		tweetDto.setContent(content);
		assertThat(tweetDto.getContent()).isEqualTo(content);
	}

	@Test
	void getCreatedAt() {
		LocalDateTime createdAt = LocalDateTime.now();
		TweetDto tweetDto = new TweetDto();
		tweetDto.setCreatedAt(createdAt);
		assertThat(tweetDto.getCreatedAt()).isEqualTo(createdAt);
	}

	@Test
	void getMentions() {
		Set<TweetDto.MentionDto> mentions = new HashSet<>();
		TweetDto tweetDto = new TweetDto();
		tweetDto.setMentions(mentions);
		assertThat(tweetDto.getMentions()).isEqualTo(mentions);
	}

	@Test
	void getHashtags() {
		Set<TweetDto.HashtagDto> hashtags = new HashSet<>();
		TweetDto tweetDto = new TweetDto();
		tweetDto.setHashtags(hashtags);
		assertThat(tweetDto.getHashtags()).isEqualTo(hashtags);
	}

	@Test
	void getLikesCount() {
		Long likesCount = 42L;
		TweetDto tweetDto = new TweetDto();
		tweetDto.setLikesCount(likesCount);
		assertThat(tweetDto.getLikesCount()).isEqualTo(likesCount);
	}

	@Test
	void getRepliesCount() {
		Long repliesCount = 24L;
		TweetDto tweetDto = new TweetDto();
		tweetDto.setRepliesCount(repliesCount);
		assertThat(tweetDto.getRepliesCount()).isEqualTo(repliesCount);
	}

	@Test
	void getRetweetsCount() {
		Long retweetsCount = 12L;
		TweetDto tweetDto = new TweetDto();
		tweetDto.setRetweetsCount(retweetsCount);
		assertThat(tweetDto.getRetweetsCount()).isEqualTo(retweetsCount);
	}

	@Test
	void getTopReply() {
		ReplyDto topReply = new ReplyDto();
		TweetDto tweetDto = new TweetDto();
		tweetDto.setTopReply(topReply);
		assertThat(tweetDto.getTopReply()).isEqualTo(topReply);
	}

	@Test
	void setId() {
		UUID id = UUID.randomUUID();
		TweetDto tweetDto = new TweetDto();
		assertThat(tweetDto.getId()).isNull(); // Initially null
		tweetDto.setId(id);
		assertThat(tweetDto.getId()).isEqualTo(id);
	}

	@Test
	void setUserId() {
		UUID userId = UUID.randomUUID();
		TweetDto tweetDto = new TweetDto();
		assertThat(tweetDto.getUserId()).isNull(); // Initially null
		tweetDto.setUserId(userId);
		assertThat(tweetDto.getUserId()).isEqualTo(userId);
	}

	@Test
	void setContent() {
		String content = "Test tweet content";
		TweetDto tweetDto = new TweetDto();
		assertThat(tweetDto.getContent()).isNull(); // Initially null
		tweetDto.setContent(content);
		assertThat(tweetDto.getContent()).isEqualTo(content);
	}

	@Test
	void setCreatedAt() {
		LocalDateTime createdAt = LocalDateTime.now();
		TweetDto tweetDto = new TweetDto();
		assertThat(tweetDto.getCreatedAt()).isNull(); // Initially null
		tweetDto.setCreatedAt(createdAt);
		assertThat(tweetDto.getCreatedAt()).isEqualTo(createdAt);
	}

	@Test
	void setMentions() {
		Set<TweetDto.MentionDto> mentions = new HashSet<>();
		TweetDto tweetDto = new TweetDto();
		assertThat(tweetDto.getMentions()).isNull(); // Initially null
		tweetDto.setMentions(mentions);
		assertThat(tweetDto.getMentions()).isEqualTo(mentions);
	}

	@Test
	void setHashtags() {
		Set<TweetDto.HashtagDto> hashtags = new HashSet<>();
		TweetDto tweetDto = new TweetDto();
		assertThat(tweetDto.getHashtags()).isNull(); // Initially null
		tweetDto.setHashtags(hashtags);
		assertThat(tweetDto.getHashtags()).isEqualTo(hashtags);
	}

	@Test
	void setLikesCount() {
		Long likesCount = 42L;
		TweetDto tweetDto = new TweetDto();
		assertThat(tweetDto.getLikesCount()).isNull(); // Initially null
		tweetDto.setLikesCount(likesCount);
		assertThat(tweetDto.getLikesCount()).isEqualTo(likesCount);
	}

	@Test
	void setRepliesCount() {
		Long repliesCount = 24L;
		TweetDto tweetDto = new TweetDto();
		assertThat(tweetDto.getRepliesCount()).isNull(); // Initially null
		tweetDto.setRepliesCount(repliesCount);
		assertThat(tweetDto.getRepliesCount()).isEqualTo(repliesCount);
	}

	@Test
	void setRetweetsCount() {
		Long retweetsCount = 12L;
		TweetDto tweetDto = new TweetDto();
		assertThat(tweetDto.getRetweetsCount()).isNull(); // Initially null
		tweetDto.setRetweetsCount(retweetsCount);
		assertThat(tweetDto.getRetweetsCount()).isEqualTo(retweetsCount);
	}

	@Test
	void setTopReply() {
		ReplyDto topReply = new ReplyDto();
		TweetDto tweetDto = new TweetDto();
		assertThat(tweetDto.getTopReply()).isNull(); // Initially null
		tweetDto.setTopReply(topReply);
		assertThat(tweetDto.getTopReply()).isEqualTo(topReply);
	}

	@Test
	void testGetId() {
		UUID id = UUID.randomUUID();
		TweetDto.MentionDto mentionDto = new TweetDto.MentionDto();
		mentionDto.setId(id);
		assertThat(mentionDto.getId()).isEqualTo(id);
	}

	@Test
	void testGetUserId() {
		UUID userId = UUID.randomUUID();
		TweetDto.MentionDto mentionDto = new TweetDto.MentionDto();
		mentionDto.setUserId(userId);
		assertThat(mentionDto.getUserId()).isEqualTo(userId);
	}

	@Test
	void testGetText() {
		String text = "example";
		TweetDto.MentionDto mentionDto = new TweetDto.MentionDto();
		mentionDto.setText(text);
		assertThat(mentionDto.getText()).isEqualTo(text);
	}

	@Test
	void testSetId() {
		UUID id = UUID.randomUUID();
		TweetDto.MentionDto mentionDto = new TweetDto.MentionDto();
		assertThat(mentionDto.getId()).isNull(); // Initially null
		mentionDto.setId(id);
		assertThat(mentionDto.getId()).isEqualTo(id);
	}

	@Test
	void testSetUserId() {
		UUID userId = UUID.randomUUID();
		TweetDto.MentionDto mentionDto = new TweetDto.MentionDto();
		assertThat(mentionDto.getUserId()).isNull(); // Initially null
		mentionDto.setUserId(userId);
		assertThat(mentionDto.getUserId()).isEqualTo(userId);
	}

	@Test
	void testSetText() {
		String text = "example";
		TweetDto.MentionDto mentionDto = new TweetDto.MentionDto();
		assertThat(mentionDto.getText()).isNull(); // Initially null
		mentionDto.setText(text);
		assertThat(mentionDto.getText()).isEqualTo(text);
	}

	@Test
	void testGetHashtagId() {
		UUID id = UUID.randomUUID();
		TweetDto.HashtagDto hashtagDto = new TweetDto.HashtagDto();
		hashtagDto.setId(id);
		assertThat(hashtagDto.getId()).isEqualTo(id);
	}

	@Test
	void testGetHashtagText() {
		String text = "example";
		TweetDto.HashtagDto hashtagDto = new TweetDto.HashtagDto();
		hashtagDto.setText(text);
		assertThat(hashtagDto.getText()).isEqualTo(text);
	}

	@Test
	void testGetCount() {
		Long count = 5L;
		TweetDto.HashtagDto hashtagDto = new TweetDto.HashtagDto();
		hashtagDto.setCount(count);
		assertThat(hashtagDto.getCount()).isEqualTo(count);
	}

	@Test
	void testSetHashtagId() {
		UUID id = UUID.randomUUID();
		TweetDto.HashtagDto hashtagDto = new TweetDto.HashtagDto();
		assertThat(hashtagDto.getId()).isNull(); // Initially null
		hashtagDto.setId(id);
		assertThat(hashtagDto.getId()).isEqualTo(id);
	}

	@Test
	void testSetHashtagText() {
		String text = "example";
		TweetDto.HashtagDto hashtagDto = new TweetDto.HashtagDto();
		assertThat(hashtagDto.getText()).isNull(); // Initially null
		hashtagDto.setText(text);
		assertThat(hashtagDto.getText()).isEqualTo(text);
	}

	@Test
	void testSetCount() {
		Long count = 5L;
		TweetDto.HashtagDto hashtagDto = new TweetDto.HashtagDto();
		assertThat(hashtagDto.getCount()).isNull(); // Initially null
		hashtagDto.setCount(count);
		assertThat(hashtagDto.getCount()).isEqualTo(count);
	}

	@Test
	void testMentionDtoAllArgsConstructor() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String text = "mention text";

		TweetDto.MentionDto mentionDto = new TweetDto.MentionDto(id, userId, text);

		assertThat(mentionDto.getId()).isEqualTo(id);
		assertThat(mentionDto.getUserId()).isEqualTo(userId);
		assertThat(mentionDto.getText()).isEqualTo(text);
	}

	@Test
	void testMentionDtoBuilder() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String text = "mention text";

		TweetDto.MentionDto mentionDto = TweetDto.MentionDto.builder().id(id).userId(userId).text(text).build();

		assertThat(mentionDto.getId()).isEqualTo(id);
		assertThat(mentionDto.getUserId()).isEqualTo(userId);
		assertThat(mentionDto.getText()).isEqualTo(text);
	}

	@Test
	void testHashtagDtoAllArgsConstructor() {
		UUID id = UUID.randomUUID();
		String text = "hashtag text";
		Long count = 10L;

		TweetDto.HashtagDto hashtagDto = new TweetDto.HashtagDto(id, text, count);

		assertThat(hashtagDto.getId()).isEqualTo(id);
		assertThat(hashtagDto.getText()).isEqualTo(text);
		assertThat(hashtagDto.getCount()).isEqualTo(count);
	}

	@Test
	void testHashtagDtoBuilder() {
		UUID id = UUID.randomUUID();
		String text = "hashtag text";
		Long count = 10L;

		TweetDto.HashtagDto hashtagDto = TweetDto.HashtagDto.builder().id(id).text(text).count(count).build();

		assertThat(hashtagDto.getId()).isEqualTo(id);
		assertThat(hashtagDto.getText()).isEqualTo(text);
		assertThat(hashtagDto.getCount()).isEqualTo(count);
	}

}
