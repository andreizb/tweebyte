/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.model;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserRegisterRequestTests {

	@Test
	void getUserName() {
		String userName = "testUser";
		UserRegisterRequest userRegisterRequest = new UserRegisterRequest();
		userRegisterRequest.setUserName(userName);
		assertThat(userRegisterRequest.getUserName()).isEqualTo(userName);
	}

	@Test
	void getEmail() {
		String email = "test@example.com";
		UserRegisterRequest userRegisterRequest = new UserRegisterRequest();
		userRegisterRequest.setEmail(email);
		assertThat(userRegisterRequest.getEmail()).isEqualTo(email);
	}

	@Test
	void getBiography() {
		String biography = "Test biography";
		UserRegisterRequest userRegisterRequest = new UserRegisterRequest();
		userRegisterRequest.setBiography(biography);
		assertThat(userRegisterRequest.getBiography()).isEqualTo(biography);
	}

	@Test
	void getIsPrivate() {
		Boolean isPrivate = true;
		UserRegisterRequest userRegisterRequest = new UserRegisterRequest();
		userRegisterRequest.setIsPrivate(isPrivate);
		assertThat(userRegisterRequest.getIsPrivate()).isEqualTo(isPrivate);
	}

	@Test
	void getPassword() {
		String password = "testPassword";
		UserRegisterRequest userRegisterRequest = new UserRegisterRequest();
		userRegisterRequest.setPassword(password);
		assertThat(userRegisterRequest.getPassword()).isEqualTo(password);
	}

	@Test
	void getBirthDate() {
		LocalDate birthDate = LocalDate.of(1990, 1, 1);
		UserRegisterRequest userRegisterRequest = new UserRegisterRequest();
		userRegisterRequest.setBirthDate(birthDate);
		assertThat(userRegisterRequest.getBirthDate()).isEqualTo(birthDate);
	}

	@Test
	void setUserName() {
		String userName = "testUser";
		UserRegisterRequest userRegisterRequest = new UserRegisterRequest();
		assertThat(userRegisterRequest.getUserName()).isNull(); // Initially null
		userRegisterRequest.setUserName(userName);
		assertThat(userRegisterRequest.getUserName()).isEqualTo(userName);
	}

	@Test
	void setEmail() {
		String email = "test@example.com";
		UserRegisterRequest userRegisterRequest = new UserRegisterRequest();
		assertThat(userRegisterRequest.getEmail()).isNull(); // Initially null
		userRegisterRequest.setEmail(email);
		assertThat(userRegisterRequest.getEmail()).isEqualTo(email);
	}

	@Test
	void setBiography() {
		String biography = "Test biography";
		UserRegisterRequest userRegisterRequest = new UserRegisterRequest();
		assertThat(userRegisterRequest.getBiography()).isNull(); // Initially null
		userRegisterRequest.setBiography(biography);
		assertThat(userRegisterRequest.getBiography()).isEqualTo(biography);
	}

	@Test
	void setIsPrivate() {
		Boolean isPrivate = true;
		UserRegisterRequest userRegisterRequest = new UserRegisterRequest();
		assertThat(userRegisterRequest.getIsPrivate()).isNull(); // Initially null
		userRegisterRequest.setIsPrivate(isPrivate);
		assertThat(userRegisterRequest.getIsPrivate()).isEqualTo(isPrivate);
	}

	@Test
	void setPassword() {
		String password = "testPassword";
		UserRegisterRequest userRegisterRequest = new UserRegisterRequest();
		assertThat(userRegisterRequest.getPassword()).isNull(); // Initially null
		userRegisterRequest.setPassword(password);
		assertThat(userRegisterRequest.getPassword()).isEqualTo(password);
	}

	@Test
	void setBirthDate() {
		LocalDate birthDate = LocalDate.of(1990, 1, 1);
		UserRegisterRequest userRegisterRequest = new UserRegisterRequest();
		assertThat(userRegisterRequest.getBirthDate()).isNull(); // Initially null
		userRegisterRequest.setBirthDate(birthDate);
		assertThat(userRegisterRequest.getBirthDate()).isEqualTo(birthDate);
	}

	@Test
	void testAllArgsConstructor() {
		// UserRegisterRequest field order: userName, email, biography, password,
		// birthDate, isPrivate, profilePictureId.
		LocalDate birthDate = LocalDate.of(1990, 1, 1);
		UUID profilePictureId = UUID.randomUUID();
		UserRegisterRequest request = new UserRegisterRequest("testUser", "test@example.com", "This is a biography",
				"securePassword123", birthDate, true, profilePictureId);

		assertThat(request.getUserName()).isEqualTo("testUser");
		assertThat(request.getEmail()).isEqualTo("test@example.com");
		assertThat(request.getBiography()).isEqualTo("This is a biography");
		assertThat(request.getIsPrivate()).isTrue();
		assertThat(request.getPassword()).isEqualTo("securePassword123");
		assertThat(request.getBirthDate()).isEqualTo(birthDate);
		assertThat(request.getProfilePictureId()).isEqualTo(profilePictureId);
	}

	@Test
	void testBuilder() {
		LocalDate birthDate = LocalDate.of(1990, 1, 1);
		UserRegisterRequest request = UserRegisterRequest.builder()
			.userName("testUser")
			.email("test@example.com")
			.biography("This is a biography")
			.isPrivate(true)
			.password("securePassword123")
			.birthDate(birthDate)
			.build();

		assertThat(request.getUserName()).isEqualTo("testUser");
		assertThat(request.getEmail()).isEqualTo("test@example.com");
		assertThat(request.getBiography()).isEqualTo("This is a biography");
		assertThat(request.getIsPrivate()).isTrue();
		assertThat(request.getPassword()).isEqualTo("securePassword123");
		assertThat(request.getBirthDate()).isEqualTo(birthDate);
	}

}
