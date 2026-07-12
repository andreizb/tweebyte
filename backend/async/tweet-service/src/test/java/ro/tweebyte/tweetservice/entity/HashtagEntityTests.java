/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashtagEntityTests {

	@Test
	void getId() {
		UUID id = UUID.randomUUID();
		HashtagEntity hashtagEntity = new HashtagEntity();
		hashtagEntity.setId(id);
		assertThat(hashtagEntity.getId()).isEqualTo(id);
	}

	@Test
	void getText() {
		String text = "exampleHashtag";
		HashtagEntity hashtagEntity = new HashtagEntity();
		hashtagEntity.setText(text);
		assertThat(hashtagEntity.getText()).isEqualTo(text);
	}

	@Test
	void getTweets() {
		Set<TweetEntity> tweets = new HashSet<>();
		HashtagEntity hashtagEntity = new HashtagEntity();
		hashtagEntity.setTweets(tweets);
		assertThat(hashtagEntity.getTweets()).isEqualTo(tweets);
	}

	@Test
	void setId() {
		UUID id = UUID.randomUUID();
		HashtagEntity hashtagEntity = new HashtagEntity();
		assertThat(hashtagEntity.getId()).isNull(); // Initially null
		hashtagEntity.setId(id);
		assertThat(hashtagEntity.getId()).isEqualTo(id);
	}

	@Test
	void setText() {
		String text = "exampleHashtag";
		HashtagEntity hashtagEntity = new HashtagEntity();
		assertThat(hashtagEntity.getText()).isNull(); // Initially null
		hashtagEntity.setText(text);
		assertThat(hashtagEntity.getText()).isEqualTo(text);
	}

	@Test
	void setTweets() {
		Set<TweetEntity> tweets = new HashSet<>();
		HashtagEntity hashtagEntity = new HashtagEntity();
		assertThat(hashtagEntity.getTweets()).isNull(); // Initially null
		hashtagEntity.setTweets(tweets);
		assertThat(hashtagEntity.getTweets()).isEqualTo(tweets);
	}

	@Test
	void builderTest() {
		UUID id = UUID.randomUUID();
		String text = "TestHashtag";
		Set<TweetEntity> tweets = new HashSet<>();

		HashtagEntity hashtagEntity = HashtagEntity.builder().id(id).text(text).tweets(tweets).build();

		assertThat(hashtagEntity).isNotNull();
		assertThat(hashtagEntity.getId()).isEqualTo(id);
		assertThat(hashtagEntity.getText()).isEqualTo(text);
		assertThat(hashtagEntity.getTweets()).isEqualTo(tweets);
	}

	@Test
	void allArgsConstructorTest() {
		UUID id = UUID.randomUUID();
		String text = "TestHashtag";
		Set<TweetEntity> tweets = new HashSet<>();

		HashtagEntity hashtagEntity = new HashtagEntity(id, text, tweets);

		assertThat(hashtagEntity).isNotNull();
		assertThat(hashtagEntity.getId()).isEqualTo(id);
		assertThat(hashtagEntity.getText()).isEqualTo(text);
		assertThat(hashtagEntity.getTweets()).isEqualTo(tweets);
	}

}
