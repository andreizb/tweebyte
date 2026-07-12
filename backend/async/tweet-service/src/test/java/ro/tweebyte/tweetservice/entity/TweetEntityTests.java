/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.entity;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TweetEntityTests {

	@Test
	void getId() {
		UUID id = UUID.randomUUID();
		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setId(id);
		assertThat(tweetEntity.getId()).isEqualTo(id);
	}

	@Test
	void getUserId() {
		UUID userId = UUID.randomUUID();
		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setUserId(userId);
		assertThat(tweetEntity.getUserId()).isEqualTo(userId);
	}

	@Test
	void getVersion() {
		Long version = 1L;
		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setVersion(version);
		assertThat(tweetEntity.getVersion()).isEqualTo(version);
	}

	@Test
	void getContent() {
		String content = "Tweet content";
		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setContent(content);
		assertThat(tweetEntity.getContent()).isEqualTo(content);
	}

	@Test
	void getCreatedAt() {
		LocalDateTime createdAt = LocalDateTime.now();
		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setCreatedAt(createdAt);
		assertThat(tweetEntity.getCreatedAt()).isEqualTo(createdAt);
	}

	@Test
	void getMentions() {
		MentionEntity mention1 = new MentionEntity();
		MentionEntity mention2 = new MentionEntity();
		Set<MentionEntity> mentions = new HashSet<>();
		mentions.add(mention1);
		mentions.add(mention2);

		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setMentions(mentions);

		assertThat(tweetEntity.getMentions()).isEqualTo(mentions);
	}

	@Test
	void getHashtags() {
		HashtagEntity hashtag1 = new HashtagEntity();
		HashtagEntity hashtag2 = new HashtagEntity();
		Set<HashtagEntity> hashtags = new HashSet<>();
		hashtags.add(hashtag1);
		hashtags.add(hashtag2);

		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setHashtags(hashtags);

		assertThat(tweetEntity.getHashtags()).isEqualTo(hashtags);
	}

	@Test
	void setId() {
		UUID id = UUID.randomUUID();
		TweetEntity tweetEntity = new TweetEntity();
		assertThat(tweetEntity.getId()).isNull(); // Initially null
		tweetEntity.setId(id);
		assertThat(tweetEntity.getId()).isEqualTo(id);
	}

	@Test
	void setUserId() {
		UUID userId = UUID.randomUUID();
		TweetEntity tweetEntity = new TweetEntity();
		assertThat(tweetEntity.getUserId()).isNull(); // Initially null
		tweetEntity.setUserId(userId);
		assertThat(tweetEntity.getUserId()).isEqualTo(userId);
	}

	@Test
	void setVersion() {
		Long version = 1L;
		TweetEntity tweetEntity = new TweetEntity();
		assertThat(tweetEntity.getVersion()).isNull(); // Initially null
		tweetEntity.setVersion(version);
		assertThat(tweetEntity.getVersion()).isEqualTo(version);
	}

	@Test
	void setContent() {
		String content = "Tweet content";
		TweetEntity tweetEntity = new TweetEntity();
		assertThat(tweetEntity.getContent()).isNull(); // Initially null
		tweetEntity.setContent(content);
		assertThat(tweetEntity.getContent()).isEqualTo(content);
	}

	@Test
	void setCreatedAt() {
		LocalDateTime createdAt = LocalDateTime.now();
		TweetEntity tweetEntity = new TweetEntity();
		assertThat(tweetEntity.getCreatedAt()).isNull(); // Initially null
		tweetEntity.setCreatedAt(createdAt);
		assertThat(tweetEntity.getCreatedAt()).isEqualTo(createdAt);
	}

	@Test
	void setMentions() {
		MentionEntity mention1 = new MentionEntity();
		MentionEntity mention2 = new MentionEntity();
		Set<MentionEntity> mentions = new HashSet<>();
		mentions.add(mention1);
		mentions.add(mention2);

		TweetEntity tweetEntity = new TweetEntity();
		assertThat(tweetEntity.getMentions()).isNull(); // Initially null
		tweetEntity.setMentions(mentions);
		assertThat(tweetEntity.getMentions()).isEqualTo(mentions);
	}

	@Test
	void setHashtags() {
		HashtagEntity hashtag1 = new HashtagEntity();
		HashtagEntity hashtag2 = new HashtagEntity();
		Set<HashtagEntity> hashtags = new HashSet<>();
		hashtags.add(hashtag1);
		hashtags.add(hashtag2);

		TweetEntity tweetEntity = new TweetEntity();
		assertThat(tweetEntity.getHashtags()).isNull(); // Initially null
		tweetEntity.setHashtags(hashtags);
		assertThat(tweetEntity.getHashtags()).isEqualTo(hashtags);
	}

	@Test
	void builderTest() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String content = "Test tweet content";
		LocalDateTime createdAt = LocalDateTime.now();
		Set<MentionEntity> mentions = new HashSet<>();
		Set<HashtagEntity> hashtags = new HashSet<>();

		TweetEntity tweetEntity = TweetEntity.builder()
			.id(id)
			.userId(userId)
			.content(content)
			.createdAt(createdAt)
			.mentions(mentions)
			.hashtags(hashtags)
			.build();

		assertThat(tweetEntity).isNotNull();
		assertThat(tweetEntity.getId()).isEqualTo(id);
		assertThat(tweetEntity.getUserId()).isEqualTo(userId);
		assertThat(tweetEntity.getContent()).isEqualTo(content);
		assertThat(tweetEntity.getCreatedAt()).isEqualTo(createdAt);
		assertThat(tweetEntity.getMentions()).isEqualTo(mentions);
		assertThat(tweetEntity.getHashtags()).isEqualTo(hashtags);
	}

	@Test
	void allArgsConstructorTest() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		String content = "Test tweet content";
		LocalDateTime createdAt = LocalDateTime.now();
		Set<MentionEntity> mentions = new HashSet<>();
		Set<HashtagEntity> hashtags = new HashSet<>();
		UUID[] mediaIds = { UUID.randomUUID() };

		TweetEntity tweetEntity = new TweetEntity(id, userId, 0L, content, createdAt, mediaIds, mentions, hashtags);

		assertThat(tweetEntity).isNotNull();
		assertThat(tweetEntity.getId()).isEqualTo(id);
		assertThat(tweetEntity.getUserId()).isEqualTo(userId);
		assertThat(tweetEntity.getContent()).isEqualTo(content);
		assertThat(tweetEntity.getCreatedAt()).isEqualTo(createdAt);
		assertThat(tweetEntity.getMediaIds()).isEqualTo(mediaIds);
		assertThat(tweetEntity.getMentions()).isEqualTo(mentions);
		assertThat(tweetEntity.getHashtags()).isEqualTo(hashtags);
	}

}
