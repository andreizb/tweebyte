/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetweetDtoTests {

	@Test
	void testGetterAndSetter() {
		UUID id = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();
		String content = "Test content";
		UserDto user = new UserDto();
		TweetDto tweet = new TweetDto();

		RetweetDto retweetDto = new RetweetDto().setId(id)
			.setCreatedAt(createdAt)
			.setContent(content)
			.setUser(user)
			.setTweet(tweet);

		assertThat(retweetDto.getId()).isEqualTo(id);
		assertThat(retweetDto.getCreatedAt()).isEqualTo(createdAt);
		assertThat(retweetDto.getContent()).isEqualTo(content);
		assertThat(retweetDto.getUser()).isEqualTo(user);
		assertThat(retweetDto.getTweet()).isEqualTo(tweet);

		// Test setters
		UUID newId = UUID.randomUUID();
		LocalDateTime newCreatedAt = LocalDateTime.now().minusDays(1);
		String newContent = "New test content";
		UserDto newUser = new UserDto();
		TweetDto newTweet = new TweetDto();

		retweetDto.setId(newId);
		retweetDto.setCreatedAt(newCreatedAt);
		retweetDto.setContent(newContent);
		retweetDto.setUser(newUser);
		retweetDto.setTweet(newTweet);

		assertThat(retweetDto.getId()).isEqualTo(newId);
		assertThat(retweetDto.getCreatedAt()).isEqualTo(newCreatedAt);
		assertThat(retweetDto.getContent()).isEqualTo(newContent);
		assertThat(retweetDto.getUser()).isEqualTo(newUser);
		assertThat(retweetDto.getTweet()).isEqualTo(newTweet);
	}

	@Test
	void testAllArgsConstructor() {
		UUID id = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();
		String content = "Test content";
		UserDto user = new UserDto();
		TweetDto tweet = new TweetDto();
		UUID[] mediaIds = { UUID.randomUUID() };

		RetweetDto retweetDto = new RetweetDto(id, createdAt, content, user, tweet, mediaIds);

		assertThat(retweetDto.getId()).isEqualTo(id);
		assertThat(retweetDto.getCreatedAt()).isEqualTo(createdAt);
		assertThat(retweetDto.getContent()).isEqualTo(content);
		assertThat(retweetDto.getUser()).isEqualTo(user);
		assertThat(retweetDto.getTweet()).isEqualTo(tweet);
		assertThat(retweetDto.getMediaIds()).isEqualTo(mediaIds);
	}

	@Test
	void testNoArgsConstructor() {
		RetweetDto retweetDto = new RetweetDto();

		assertThat(retweetDto).isNotNull();
	}

}
