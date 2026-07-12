/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
		Set<MentionDto> mentions = new HashSet<>();
		TweetDto tweetDto = new TweetDto();
		tweetDto.setMentions(mentions);
		assertThat(tweetDto.getMentions()).isEqualTo(mentions);
	}

	@Test
	void getHashtags() {
		Set<HashtagDto> hashtags = new HashSet<>();
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
	void getReplies() {
		List<ReplyDto> replies = new ArrayList<>();
		TweetDto tweetDto = new TweetDto();
		tweetDto.setReplies(replies);
		assertThat(tweetDto.getReplies()).isEqualTo(replies);
	}

	@Test
	void getUser() {
		UserDto user = new UserDto();
		TweetDto tweetDto = new TweetDto();
		tweetDto.setUser(user);
		assertThat(tweetDto.getUser()).isEqualTo(user);
	}

	@Test
	void setId() {
		UUID id = UUID.randomUUID();
		TweetDto tweetDto = new TweetDto();
		assertThat(tweetDto.getId()).isNull();
		tweetDto.setId(id);
		assertThat(tweetDto.getId()).isEqualTo(id);
	}

	@Test
	void setUserId() {
		UUID userId = UUID.randomUUID();
		TweetDto tweetDto = new TweetDto();
		assertThat(tweetDto.getUserId()).isNull();
		tweetDto.setUserId(userId);
		assertThat(tweetDto.getUserId()).isEqualTo(userId);
	}

	@Test
	void setContent() {
		String content = "Test tweet content";
		TweetDto tweetDto = new TweetDto();
		assertThat(tweetDto.getContent()).isNull();
		tweetDto.setContent(content);
		assertThat(tweetDto.getContent()).isEqualTo(content);
	}

	@Test
	void setCreatedAt() {
		LocalDateTime createdAt = LocalDateTime.now();
		TweetDto tweetDto = new TweetDto();
		assertThat(tweetDto.getCreatedAt()).isNull();
		tweetDto.setCreatedAt(createdAt);
		assertThat(tweetDto.getCreatedAt()).isEqualTo(createdAt);
	}

	@Test
	void setMentions() {
		Set<MentionDto> mentions = new HashSet<>();
		TweetDto tweetDto = new TweetDto();
		assertThat(tweetDto.getMentions()).isNull();
		tweetDto.setMentions(mentions);
		assertThat(tweetDto.getMentions()).isEqualTo(mentions);
	}

	@Test
	void setHashtags() {
		Set<HashtagDto> hashtags = new HashSet<>();
		TweetDto tweetDto = new TweetDto();
		assertThat(tweetDto.getHashtags()).isNull();
		tweetDto.setHashtags(hashtags);
		assertThat(tweetDto.getHashtags()).isEqualTo(hashtags);
	}

	@Test
	void setLikesCount() {
		Long likesCount = 42L;
		TweetDto tweetDto = new TweetDto();
		assertThat(tweetDto.getLikesCount()).isNull();
		tweetDto.setLikesCount(likesCount);
		assertThat(tweetDto.getLikesCount()).isEqualTo(likesCount);
	}

	@Test
	void setRepliesCount() {
		Long repliesCount = 24L;
		TweetDto tweetDto = new TweetDto();
		assertThat(tweetDto.getRepliesCount()).isNull();
		tweetDto.setRepliesCount(repliesCount);
		assertThat(tweetDto.getRepliesCount()).isEqualTo(repliesCount);
	}

	@Test
	void setRetweetsCount() {
		Long retweetsCount = 12L;
		TweetDto tweetDto = new TweetDto();
		assertThat(tweetDto.getRetweetsCount()).isNull();
		tweetDto.setRetweetsCount(retweetsCount);
		assertThat(tweetDto.getRetweetsCount()).isEqualTo(retweetsCount);
	}

	@Test
	void setTopReply() {
		ReplyDto topReply = new ReplyDto();
		TweetDto tweetDto = new TweetDto();
		assertThat(tweetDto.getTopReply()).isNull();
		tweetDto.setTopReply(topReply);
		assertThat(tweetDto.getTopReply()).isEqualTo(topReply);
	}

	@Test
	void setReplies() {
		List<ReplyDto> replies = new ArrayList<>();
		TweetDto tweetDto = new TweetDto();
		assertThat(tweetDto.getReplies()).isNull();
		tweetDto.setReplies(replies);
		assertThat(tweetDto.getReplies()).isEqualTo(replies);
	}

	@Test
	void setUser() {
		UserDto user = new UserDto();
		TweetDto tweetDto = new TweetDto();
		assertThat(tweetDto.getUser()).isNull();
		tweetDto.setUser(user);
		assertThat(tweetDto.getUser()).isEqualTo(user);
	}

}
