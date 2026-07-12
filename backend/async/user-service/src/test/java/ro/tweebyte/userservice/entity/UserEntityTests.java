/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserEntityTests {

	@Test
	void getId() {
		UserEntity userEntity = new UserEntity();
		UUID id = UUID.randomUUID();
		userEntity.setId(id);

		assertThat(userEntity.getId()).isEqualTo(id);
	}

	@Test
	void getUserName() {
		UserEntity userEntity = new UserEntity();
		String userName = "testUser";
		userEntity.setUserName(userName);

		assertThat(userEntity.getUserName()).isEqualTo(userName);
	}

	@Test
	void getEmail() {
		UserEntity userEntity = new UserEntity();
		String email = "test@example.com";
		userEntity.setEmail(email);

		assertThat(userEntity.getEmail()).isEqualTo(email);
	}

	@Test
	void getBiography() {
		UserEntity userEntity = new UserEntity();
		String biography = "Test biography";
		userEntity.setBiography(biography);

		assertThat(userEntity.getBiography()).isEqualTo(biography);
	}

	@Test
	void getPassword() {
		UserEntity userEntity = new UserEntity();
		String password = "testPassword";
		userEntity.setPassword(password);

		assertThat(userEntity.getPassword()).isEqualTo(password);
	}

	@Test
	void getIsPrivate() {
		UserEntity userEntity = new UserEntity();
		Boolean isPrivate = true;
		userEntity.setIsPrivate(isPrivate);

		assertThat(userEntity.getIsPrivate()).isTrue();
	}

	@Test
	void getBirthDate() {
		UserEntity userEntity = new UserEntity();
		LocalDate birthDate = LocalDate.of(1990, 1, 1);
		userEntity.setBirthDate(birthDate);

		assertThat(userEntity.getBirthDate()).isEqualTo(birthDate);
	}

	@Test
	void getCreatedAt() {
		UserEntity userEntity = new UserEntity();
		LocalDateTime createdAt = LocalDateTime.now();
		userEntity.setCreatedAt(createdAt);

		assertThat(userEntity.getCreatedAt()).isEqualTo(createdAt);
	}

	@Test
	void setId() {
		UserEntity userEntity = new UserEntity();
		UUID id = UUID.randomUUID();
		assertThat(userEntity.getId()).isNull(); // Initially null
		userEntity.setId(id);

		assertThat(userEntity.getId()).isEqualTo(id);
	}

	@Test
	void setUserName() {
		UserEntity userEntity = new UserEntity();
		String userName = "testUser";
		assertThat(userEntity.getUserName()).isNull(); // Initially null
		userEntity.setUserName(userName);

		assertThat(userEntity.getUserName()).isEqualTo(userName);
	}

	@Test
	void setEmail() {
		UserEntity userEntity = new UserEntity();
		String email = "test@example.com";
		assertThat(userEntity.getEmail()).isNull(); // Initially null
		userEntity.setEmail(email);

		assertThat(userEntity.getEmail()).isEqualTo(email);
	}

	@Test
	void setBiography() {
		UserEntity userEntity = new UserEntity();
		String biography = "Test biography";
		assertThat(userEntity.getBiography()).isNull(); // Initially null
		userEntity.setBiography(biography);

		assertThat(userEntity.getBiography()).isEqualTo(biography);
	}

	@Test
	void setPassword() {
		UserEntity userEntity = new UserEntity();
		String password = "testPassword";
		assertThat(userEntity.getPassword()).isNull(); // Initially null
		userEntity.setPassword(password);

		assertThat(userEntity.getPassword()).isEqualTo(password);
	}

	@Test
	void setIsPrivate() {
		UserEntity userEntity = new UserEntity();
		Boolean isPrivate = true;
		assertThat(userEntity.getIsPrivate()).isNull(); // Initially null
		userEntity.setIsPrivate(isPrivate);

		assertThat(userEntity.getIsPrivate()).isTrue();
	}

	@Test
	void setBirthDate() {
		UserEntity userEntity = new UserEntity();
		LocalDate birthDate = LocalDate.of(1990, 1, 1);
		assertThat(userEntity.getBirthDate()).isNull(); // Initially null
		userEntity.setBirthDate(birthDate);

		assertThat(userEntity.getBirthDate()).isEqualTo(birthDate);
	}

	@Test
	void setCreatedAt() {
		UserEntity userEntity = new UserEntity();
		LocalDateTime createdAt = LocalDateTime.now();
		assertThat(userEntity.getCreatedAt()).isNull(); // Initially null
		userEntity.setCreatedAt(createdAt);

		assertThat(userEntity.getCreatedAt()).isEqualTo(createdAt);
	}

}
