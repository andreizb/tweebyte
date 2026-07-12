/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserDtoTests {

	@Test
	void testGetterAndSetter() {
		UUID id = UUID.randomUUID();
		String userName = "TestUser";
		Boolean isPrivate = true;
		LocalDateTime createdAt = LocalDateTime.now();

		UserDto userDto = new UserDto();
		userDto.setUserName(userName);
		userDto.setId(id);
		userDto.setIsPrivate(isPrivate);
		userDto.setCreatedAt(createdAt);

		assertThat(userDto.getId()).isEqualTo(id);
		assertThat(userDto.getUserName()).isEqualTo(userName);
		assertThat(userDto.getIsPrivate()).isEqualTo(isPrivate);
		assertThat(userDto.getCreatedAt()).isEqualTo(createdAt);

		// Test setters
		UUID newId = UUID.randomUUID();
		String newUserName = "NewUser";
		Boolean newIsPrivate = false;
		LocalDateTime newCreatedAt = LocalDateTime.now().minusDays(1);

		userDto.setId(newId);
		userDto.setUserName(newUserName);
		userDto.setIsPrivate(newIsPrivate);
		userDto.setCreatedAt(newCreatedAt);

		assertThat(userDto.getId()).isEqualTo(newId);
		assertThat(userDto.getUserName()).isEqualTo(newUserName);
		assertThat(userDto.getIsPrivate()).isEqualTo(newIsPrivate);
		assertThat(userDto.getCreatedAt()).isEqualTo(newCreatedAt);
	}

	@Test
	void testAllArgsConstructor() {
		UUID id = UUID.randomUUID();
		String userName = "TestUser";
		Boolean isPrivate = true;
		LocalDateTime createdAt = LocalDateTime.now();

		UserDto userDto = new UserDto(id, userName, isPrivate, createdAt);

		assertThat(userDto.getId()).isEqualTo(id);
		assertThat(userDto.getUserName()).isEqualTo(userName);
		assertThat(userDto.getIsPrivate()).isEqualTo(isPrivate);
		assertThat(userDto.getCreatedAt()).isEqualTo(createdAt);
	}

	@Test
	void testBuilder() {
		UUID id = UUID.randomUUID();
		String userName = "TestUser";
		Boolean isPrivate = true;
		LocalDateTime createdAt = LocalDateTime.now();

		UserDto userDto = UserDto.builder().id(id).userName(userName).isPrivate(isPrivate).createdAt(createdAt).build();

		assertThat(userDto.getId()).isEqualTo(id);
		assertThat(userDto.getUserName()).isEqualTo(userName);
		assertThat(userDto.getIsPrivate()).isEqualTo(isPrivate);
		assertThat(userDto.getCreatedAt()).isEqualTo(createdAt);
	}

	@Test
	void testNoArgsConstructor() {
		UserDto userDto = new UserDto();

		assertThat(userDto).isNotNull();
	}

}
