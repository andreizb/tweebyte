/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.model;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserUpdateRequestTests {

	@Test
	void getUserName() {
		String userName = "testUser";
		UserUpdateRequest userEntity = new UserUpdateRequest();
		userEntity.setUserName(userName);
		assertThat(userEntity.getUserName()).isEqualTo(userName);
	}

	@Test
	void getEmail() {
		String email = "test@example.com";
		UserUpdateRequest userEntity = new UserUpdateRequest();
		userEntity.setEmail(email);
		assertThat(userEntity.getEmail()).isEqualTo(email);
	}

	@Test
	void getPassword() {
		String password = "testPassword";
		UserUpdateRequest userEntity = new UserUpdateRequest();
		userEntity.setPassword(password);
		assertThat(userEntity.getPassword()).isEqualTo(password);
	}

	@Test
	void getIsPrivate() {
		Boolean isPrivate = true;
		UserUpdateRequest userEntity = new UserUpdateRequest();
		userEntity.setIsPrivate(isPrivate);
		assertThat(userEntity.getIsPrivate()).isEqualTo(isPrivate);
	}

	@Test
	void getBirthDate() {
		LocalDate birthDate = LocalDate.of(1990, 1, 1);
		UserUpdateRequest userEntity = new UserUpdateRequest();
		userEntity.setBirthDate(birthDate);
		assertThat(userEntity.getBirthDate()).isEqualTo(birthDate);
	}

	@Test
	void setUserName() {
		String userName = "testUser";
		UserUpdateRequest userEntity = new UserUpdateRequest();
		assertThat(userEntity.getUserName()).isNull(); // Initially null
		userEntity.setUserName(userName);
		assertThat(userEntity.getUserName()).isEqualTo(userName);
	}

	@Test
	void setEmail() {
		String email = "test@example.com";
		UserUpdateRequest userEntity = new UserUpdateRequest();
		assertThat(userEntity.getEmail()).isNull(); // Initially null
		userEntity.setEmail(email);
		assertThat(userEntity.getEmail()).isEqualTo(email);
	}

	@Test
	void setPassword() {
		String password = "testPassword";
		UserUpdateRequest userEntity = new UserUpdateRequest();
		assertThat(userEntity.getPassword()).isNull(); // Initially null
		userEntity.setPassword(password);
		assertThat(userEntity.getPassword()).isEqualTo(password);
	}

	@Test
	void setIsPrivate() {
		Boolean isPrivate = true;
		UserUpdateRequest userEntity = new UserUpdateRequest();
		assertThat(userEntity.getIsPrivate()).isNull(); // Initially null
		userEntity.setIsPrivate(isPrivate);
		assertThat(userEntity.getIsPrivate()).isEqualTo(isPrivate);
	}

	@Test
	void setBirthDate() {
		LocalDate birthDate = LocalDate.of(1990, 1, 1);
		UserUpdateRequest userEntity = new UserUpdateRequest();
		assertThat(userEntity.getBirthDate()).isNull(); // Initially null
		userEntity.setBirthDate(birthDate);
		assertThat(userEntity.getBirthDate()).isEqualTo(birthDate);
	}

}
