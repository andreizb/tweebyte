/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
	void getContent() {
		String content = "Test content";
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
		mentions.add(new TweetDto.MentionDto());
		TweetDto tweetDto = new TweetDto();
		tweetDto.setMentions(mentions);
		assertThat(tweetDto.getMentions()).isEqualTo(mentions);
	}

	@Test
	void getHashtags() {
		Set<TweetDto.HashtagDto> hashtags = new HashSet<>();
		hashtags.add(new TweetDto.HashtagDto());
		TweetDto tweetDto = new TweetDto();
		tweetDto.setHashtags(hashtags);
		assertThat(tweetDto.getHashtags()).isEqualTo(hashtags);
	}

	@Test
	void getLikesCount() {
		Long likesCount = 10L;
		TweetDto tweetDto = new TweetDto();
		tweetDto.setLikesCount(likesCount);
		assertThat(tweetDto.getLikesCount()).isEqualTo(likesCount);
	}

	@Test
	void getRepliesCount() {
		Long repliesCount = 5L;
		TweetDto tweetDto = new TweetDto();
		tweetDto.setRepliesCount(repliesCount);
		assertThat(tweetDto.getRepliesCount()).isEqualTo(repliesCount);
	}

	@Test
	void getRetweetsCount() {
		Long retweetsCount = 15L;
		TweetDto tweetDto = new TweetDto();
		tweetDto.setRetweetsCount(retweetsCount);
		assertThat(tweetDto.getRetweetsCount()).isEqualTo(retweetsCount);
	}

	@Test
	void getTopReply() {
		TweetDto.ReplyDto topReply = new TweetDto.ReplyDto();
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
	void setContent() {
		String content = "Test content";
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
		mentions.add(new TweetDto.MentionDto());
		TweetDto tweetDto = new TweetDto();
		assertThat(tweetDto.getMentions()).isNull(); // Initially null
		tweetDto.setMentions(mentions);
		assertThat(tweetDto.getMentions()).isEqualTo(mentions);
	}

	@Test
	void setHashtags() {
		Set<TweetDto.HashtagDto> hashtags = new HashSet<>();
		hashtags.add(new TweetDto.HashtagDto());
		TweetDto tweetDto = new TweetDto();
		assertThat(tweetDto.getHashtags()).isNull(); // Initially null
		tweetDto.setHashtags(hashtags);
		assertThat(tweetDto.getHashtags()).isEqualTo(hashtags);
	}

	@Test
	void setLikesCount() {
		Long likesCount = 10L;
		TweetDto tweetDto = new TweetDto();
		assertThat(tweetDto.getLikesCount()).isNull(); // Initially null
		tweetDto.setLikesCount(likesCount);
		assertThat(tweetDto.getLikesCount()).isEqualTo(likesCount);
	}

	@Test
	void setRepliesCount() {
		Long repliesCount = 5L;
		TweetDto tweetDto = new TweetDto();
		assertThat(tweetDto.getRepliesCount()).isNull(); // Initially null
		tweetDto.setRepliesCount(repliesCount);
		assertThat(tweetDto.getRepliesCount()).isEqualTo(repliesCount);
	}

	@Test
	void setRetweetsCount() {
		Long retweetsCount = 15L;
		TweetDto tweetDto = new TweetDto();
		assertThat(tweetDto.getRetweetsCount()).isNull(); // Initially null
		tweetDto.setRetweetsCount(retweetsCount);
		assertThat(tweetDto.getRetweetsCount()).isEqualTo(retweetsCount);
	}

	@Test
	void setTopReply() {
		TweetDto.ReplyDto topReply = new TweetDto.ReplyDto();
		TweetDto tweetDto = new TweetDto();
		assertThat(tweetDto.getTopReply()).isNull(); // Initially null
		tweetDto.setTopReply(topReply);
		assertThat(tweetDto.getTopReply()).isEqualTo(topReply);
	}

	@Test
	void MentionDto_getters() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String text = "Mention text";
		TweetDto.MentionDto mentionDto = new TweetDto.MentionDto(id, userId, text);
		assertThat(mentionDto.getId()).isEqualTo(id);
		assertThat(mentionDto.getUserId()).isEqualTo(userId);
		assertThat(mentionDto.getText()).isEqualTo(text);
	}

	@Test
	void HashtagDto_getters() {
		UUID id = UUID.randomUUID();
		String text = "Hashtag text";
		TweetDto.HashtagDto hashtagDto = new TweetDto.HashtagDto(id, text);
		assertThat(hashtagDto.getId()).isEqualTo(id);
		assertThat(hashtagDto.getText()).isEqualTo(text);
	}

	@Test
	void ReplyDto_getters() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String userName = "reply_author";
		String content = "Reply content";
		LocalDateTime createdAt = LocalDateTime.now();
		Long likesCount = 5L;
		List<TweetDto.LikeDto> likes = Collections.singletonList(new TweetDto.LikeDto(id));
		TweetDto.ReplyDto replyDto = new TweetDto.ReplyDto(id, userId, userName, content, createdAt, likesCount, likes);
		assertThat(replyDto.getId()).isEqualTo(id);
		assertThat(replyDto.getUserId()).isEqualTo(userId);
		assertThat(replyDto.getUserName()).isEqualTo(userName);
		assertThat(replyDto.getContent()).isEqualTo(content);
		assertThat(replyDto.getCreatedAt()).isEqualTo(createdAt);
		assertThat(replyDto.getLikesCount()).isEqualTo(likesCount);
		assertThat(replyDto.getLikes()).isEqualTo(likes);
	}

	@Test
	void LikeDto_getters() {
		UUID id = UUID.randomUUID();
		TweetDto.LikeDto likeDto = new TweetDto.LikeDto(id);
		assertThat(likeDto.getId()).isEqualTo(id);
	}

	@Test
	void testAllArgsConstructor() {
		UUID id = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();
		Set<TweetDto.HashtagDto> hashtags = Set.of(new TweetDto.HashtagDto(UUID.randomUUID(), "hashtag"));

		TweetDto tweet = new TweetDto(id, "content", createdAt, null, hashtags, 10L, 5L, 2L, null);

		assertThat(tweet).isNotNull();
		assertThat(tweet.getId()).isEqualTo(id);
		assertThat(tweet.getContent()).isEqualTo("content");
		assertThat(tweet.getCreatedAt()).isEqualTo(createdAt);
		assertThat(tweet.getHashtags()).isEqualTo(hashtags);
		assertThat(tweet.getLikesCount()).isEqualTo(10L);
		assertThat(tweet.getRepliesCount()).isEqualTo(5L);
		assertThat(tweet.getRetweetsCount()).isEqualTo(2L);
	}

	@Test
	void testBuilder() {
		UUID id = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();
		Set<TweetDto.HashtagDto> hashtags = Set.of(new TweetDto.HashtagDto(UUID.randomUUID(), "hashtag"));

		TweetDto tweet = TweetDto.builder()
			.id(id)
			.content("content")
			.createdAt(createdAt)
			.hashtags(hashtags)
			.likesCount(10L)
			.repliesCount(5L)
			.retweetsCount(2L)
			.build();

		assertThat(tweet).isNotNull();
		assertThat(tweet.getId()).isEqualTo(id);
		assertThat(tweet.getContent()).isEqualTo("content");
		assertThat(tweet.getCreatedAt()).isEqualTo(createdAt);
		assertThat(tweet.getHashtags()).isEqualTo(hashtags);
		assertThat(tweet.getLikesCount()).isEqualTo(10L);
		assertThat(tweet.getRepliesCount()).isEqualTo(5L);
		assertThat(tweet.getRetweetsCount()).isEqualTo(2L);
	}

}
