/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserDtoTests {

	@Test
	void gettersAndSettersRoundTrip() {
		UUID id = UUID.randomUUID();
		String userName = "testUser";
		String email = "test@example.com";
		String biography = "Test biography";
		Boolean isPrivate = true;
		LocalDate birthDate = LocalDate.of(1990, 1, 1);
		LocalDateTime createdAt = LocalDateTime.now();
		Long following = 100L;
		Long followers = 200L;
		List<TweetDto> tweets = List.of();

		UserDto userDto = new UserDto();
		userDto.setId(id);
		userDto.setUserName(userName);
		userDto.setEmail(email);
		userDto.setBiography(biography);
		userDto.setIsPrivate(isPrivate);
		userDto.setBirthDate(birthDate);
		userDto.setCreatedAt(createdAt);
		userDto.setFollowing(following);
		userDto.setFollowers(followers);
		userDto.setTweets(tweets);

		assertThat(userDto.getId()).isEqualTo(id);
		assertThat(userDto.getUserName()).isEqualTo(userName);
		assertThat(userDto.getEmail()).isEqualTo(email);
		assertThat(userDto.getBiography()).isEqualTo(biography);
		assertThat(userDto.getIsPrivate()).isTrue();
		assertThat(userDto.getBirthDate()).isEqualTo(birthDate);
		assertThat(userDto.getCreatedAt()).isEqualTo(createdAt);
		assertThat(userDto.getFollowing()).isEqualTo(following);
		assertThat(userDto.getFollowers()).isEqualTo(followers);
		assertThat(userDto.getTweets()).isEqualTo(tweets);
	}

	@Test
	void getId() {
		UUID id = UUID.randomUUID();
		UserDto userDto = new UserDto();
		userDto.setId(id);
		assertThat(userDto.getId()).isEqualTo(id);
	}

	@Test
	void getUserName() {
		String userName = "testUser";
		UserDto userDto = new UserDto();
		userDto.setUserName(userName);
		assertThat(userDto.getUserName()).isEqualTo(userName);
	}

	@Test
	void getEmail() {
		String email = "test@example.com";
		UserDto userDto = new UserDto();
		userDto.setEmail(email);
		assertThat(userDto.getEmail()).isEqualTo(email);
	}

	@Test
	void getBiography() {
		String biography = "Test biography";
		UserDto userDto = new UserDto();
		userDto.setBiography(biography);
		assertThat(userDto.getBiography()).isEqualTo(biography);
	}

	@Test
	void getIsPrivate() {
		Boolean isPrivate = true;
		UserDto userDto = new UserDto();
		userDto.setIsPrivate(isPrivate);
		assertThat(userDto.getIsPrivate()).isEqualTo(isPrivate);
	}

	@Test
	void getBirthDate() {
		LocalDate birthDate = LocalDate.of(1990, 1, 1);
		UserDto userDto = new UserDto();
		userDto.setBirthDate(birthDate);
		assertThat(userDto.getBirthDate()).isEqualTo(birthDate);
	}

	@Test
	void getCreatedAt() {
		LocalDateTime createdAt = LocalDateTime.now();
		UserDto userDto = new UserDto();
		userDto.setCreatedAt(createdAt);
		assertThat(userDto.getCreatedAt()).isEqualTo(createdAt);
	}

	@Test
	void getFollowing() {
		Long following = 100L;
		UserDto userDto = new UserDto();
		userDto.setFollowing(following);
		assertThat(userDto.getFollowing()).isEqualTo(following);
	}

	@Test
	void getFollowers() {
		Long followers = 200L;
		UserDto userDto = new UserDto();
		userDto.setFollowers(followers);
		assertThat(userDto.getFollowers()).isEqualTo(followers);
	}

	@Test
	void getTweets() {
		List<TweetDto> tweets = List.of();
		UserDto userDto = new UserDto();
		userDto.setTweets(tweets);
		assertThat(userDto.getTweets()).isEqualTo(tweets);
	}

}
