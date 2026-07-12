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

	@Test
	void setId() {
		UUID id = UUID.randomUUID();
		UserDto userDto = new UserDto();
		assertThat(userDto.getId()).isNull(); // Initially null
		userDto.setId(id);
		assertThat(userDto.getId()).isEqualTo(id);
	}

	@Test
	void setUserName() {
		String userName = "testUser";
		UserDto userDto = new UserDto();
		assertThat(userDto.getUserName()).isNull(); // Initially null
		userDto.setUserName(userName);
		assertThat(userDto.getUserName()).isEqualTo(userName);
	}

	@Test
	void setEmail() {
		String email = "test@example.com";
		UserDto userDto = new UserDto();
		assertThat(userDto.getEmail()).isNull(); // Initially null
		userDto.setEmail(email);
		assertThat(userDto.getEmail()).isEqualTo(email);
	}

	@Test
	void setBiography() {
		String biography = "Test biography";
		UserDto userDto = new UserDto();
		assertThat(userDto.getBiography()).isNull(); // Initially null
		userDto.setBiography(biography);
		assertThat(userDto.getBiography()).isEqualTo(biography);
	}

	@Test
	void setIsPrivate() {
		Boolean isPrivate = true;
		UserDto userDto = new UserDto();
		assertThat(userDto.getIsPrivate()).isNull(); // Initially null
		userDto.setIsPrivate(isPrivate);
		assertThat(userDto.getIsPrivate()).isEqualTo(isPrivate);
	}

	@Test
	void setBirthDate() {
		LocalDate birthDate = LocalDate.of(1990, 1, 1);
		UserDto userDto = new UserDto();
		assertThat(userDto.getBirthDate()).isNull(); // Initially null
		userDto.setBirthDate(birthDate);
		assertThat(userDto.getBirthDate()).isEqualTo(birthDate);
	}

	@Test
	void setCreatedAt() {
		LocalDateTime createdAt = LocalDateTime.now();
		UserDto userDto = new UserDto();
		assertThat(userDto.getCreatedAt()).isNull(); // Initially null
		userDto.setCreatedAt(createdAt);
		assertThat(userDto.getCreatedAt()).isEqualTo(createdAt);
	}

	@Test
	void setFollowing() {
		Long following = 100L;
		UserDto userDto = new UserDto();
		assertThat(userDto.getFollowing()).isNull(); // Initially null
		userDto.setFollowing(following);
		assertThat(userDto.getFollowing()).isEqualTo(following);
	}

	@Test
	void setFollowers() {
		Long followers = 200L;
		UserDto userDto = new UserDto();
		assertThat(userDto.getFollowers()).isNull(); // Initially null
		userDto.setFollowers(followers);
		assertThat(userDto.getFollowers()).isEqualTo(followers);
	}

	@Test
	void setTweets() {
		List<TweetDto> tweets = List.of();
		UserDto userDto = new UserDto();
		assertThat(userDto.getTweets()).isNull(); // Initially null
		userDto.setTweets(tweets);
		assertThat(userDto.getTweets()).isEqualTo(tweets);
	}

}
