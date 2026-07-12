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
	void gettersAndSettersRoundTrip() {
		UUID id = UUID.randomUUID();
		String userName = "testUser";
		String email = "test@example.com";
		String biography = "Test biography";
		String password = "testPassword";
		Boolean isPrivate = true;
		LocalDate birthDate = LocalDate.of(1990, 1, 1);
		LocalDateTime createdAt = LocalDateTime.now();

		UserEntity userEntity = new UserEntity();
		userEntity.setId(id);
		userEntity.setUserName(userName);
		userEntity.setEmail(email);
		userEntity.setBiography(biography);
		userEntity.setPassword(password);
		userEntity.setIsPrivate(isPrivate);
		userEntity.setBirthDate(birthDate);
		userEntity.setCreatedAt(createdAt);

		assertThat(userEntity.getId()).isEqualTo(id);
		assertThat(userEntity.getUserName()).isEqualTo(userName);
		assertThat(userEntity.getEmail()).isEqualTo(email);
		assertThat(userEntity.getBiography()).isEqualTo(biography);
		assertThat(userEntity.getPassword()).isEqualTo(password);
		assertThat(userEntity.getIsPrivate()).isTrue();
		assertThat(userEntity.getBirthDate()).isEqualTo(birthDate);
		assertThat(userEntity.getCreatedAt()).isEqualTo(createdAt);
	}

	@Test
	void insertableFlagControlsIsNew() {
		UserEntity userEntity = new UserEntity();
		userEntity.setInsertable(true);

		assertThat(userEntity.isInsertable()).isTrue();
		assertThat(userEntity.isNew()).isTrue();
	}

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

}
