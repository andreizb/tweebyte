/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikeDtoTests {

	@Test
	void testGetterAndSetter() {
		UUID id = UUID.randomUUID();
		UserDto user = new UserDto();
		TweetDto tweet = new TweetDto();
		LocalDateTime createdAt = LocalDateTime.now();

		LikeDto likeDto = new LikeDto().setId(id).setUser(user).setTweet(tweet).setCreatedAt(createdAt);

		assertThat(likeDto.getId()).isEqualTo(id);
		assertThat(likeDto.getUser()).isEqualTo(user);
		assertThat(likeDto.getTweet()).isEqualTo(tweet);
		assertThat(likeDto.getCreatedAt()).isEqualTo(createdAt);

		UUID newId = UUID.randomUUID();
		UserDto newUser = new UserDto();
		TweetDto newTweet = new TweetDto();
		LocalDateTime newCreatedAt = LocalDateTime.now().minusDays(1);

		likeDto.setId(newId);
		likeDto.setUser(newUser);
		likeDto.setTweet(newTweet);
		likeDto.setCreatedAt(newCreatedAt);

		assertThat(likeDto.getId()).isEqualTo(newId);
		assertThat(likeDto.getUser()).isEqualTo(newUser);
		assertThat(likeDto.getTweet()).isEqualTo(newTweet);
		assertThat(likeDto.getCreatedAt()).isEqualTo(newCreatedAt);
	}

	@Test
	void testAllArgsConstructor() {
		UUID id = UUID.randomUUID();
		UserDto user = new UserDto();
		TweetDto tweet = new TweetDto();
		LocalDateTime createdAt = LocalDateTime.now();

		LikeDto likeDto = new LikeDto(id, user, tweet, createdAt);

		assertThat(likeDto.getId()).isEqualTo(id);
		assertThat(likeDto.getUser()).isEqualTo(user);
		assertThat(likeDto.getTweet()).isEqualTo(tweet);
		assertThat(likeDto.getCreatedAt()).isEqualTo(createdAt);
	}

	@Test
	void testNoArgsConstructor() {
		LikeDto likeDto = new LikeDto();

		assertThat(likeDto).isNotNull();
	}

}
