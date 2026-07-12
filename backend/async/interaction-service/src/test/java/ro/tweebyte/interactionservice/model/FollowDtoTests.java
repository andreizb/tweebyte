/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FollowDtoTests {

	@Test
	void testAllArgsConstructor() {
		UUID id = UUID.randomUUID();
		String userName = "testUser";
		UUID followerId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();
		FollowDto.Status status = FollowDto.Status.ACCEPTED;

		FollowDto followDto = new FollowDto(id, userName, followerId, followedId, createdAt, status);

		assertThat(followDto.getId()).isEqualTo(id);
		assertThat(followDto.getUserName()).isEqualTo(userName);
		assertThat(followDto.getFollowerId()).isEqualTo(followerId);
		assertThat(followDto.getFollowedId()).isEqualTo(followedId);
		assertThat(followDto.getCreatedAt()).isEqualTo(createdAt);
		assertThat(followDto.getStatus()).isEqualTo(status);
	}

	@Test
	void testBuilder() {
		UUID id = UUID.randomUUID();
		String userName = "testUser";
		UUID followerId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();
		FollowDto.Status status = FollowDto.Status.ACCEPTED;

		FollowDto followDto = new FollowDto().setId(id)
			.setUserName(userName)
			.setFollowerId(followerId)
			.setFollowedId(followedId)
			.setCreatedAt(createdAt)
			.setStatus(status);

		assertThat(followDto.getId()).isEqualTo(id);
		assertThat(followDto.getUserName()).isEqualTo(userName);
		assertThat(followDto.getFollowerId()).isEqualTo(followerId);
		assertThat(followDto.getFollowedId()).isEqualTo(followedId);
		assertThat(followDto.getCreatedAt()).isEqualTo(createdAt);
		assertThat(followDto.getStatus()).isEqualTo(status);
	}

	@Test
	void testNoArgsConstructor() {
		FollowDto followDto = new FollowDto();
		assertThat(followDto).isNotNull();
	}

	@Test
	void testGetterAndSetter() {
		UUID id = UUID.randomUUID();
		String userName = "testUser";
		UUID followerId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();
		FollowDto.Status status = FollowDto.Status.ACCEPTED;

		FollowDto followDto = new FollowDto().setId(id)
			.setUserName(userName)
			.setFollowerId(followerId)
			.setFollowedId(followedId)
			.setCreatedAt(createdAt)
			.setStatus(status);

		assertThat(followDto.getId()).isEqualTo(id);
		assertThat(followDto.getUserName()).isEqualTo(userName);
		assertThat(followDto.getFollowerId()).isEqualTo(followerId);
		assertThat(followDto.getFollowedId()).isEqualTo(followedId);
		assertThat(followDto.getCreatedAt()).isEqualTo(createdAt);
		assertThat(followDto.getStatus()).isEqualTo(status);

		// Test setters
		UUID newId = UUID.randomUUID();
		String newUserName = "newUser";
		UUID newFollowerId = UUID.randomUUID();
		UUID newFollowedId = UUID.randomUUID();
		LocalDateTime newCreatedAt = LocalDateTime.now().minusDays(1);
		FollowDto.Status newStatus = FollowDto.Status.REJECTED;

		followDto.setId(newId);
		followDto.setUserName(newUserName);
		followDto.setFollowerId(newFollowerId);
		followDto.setFollowedId(newFollowedId);
		followDto.setCreatedAt(newCreatedAt);
		followDto.setStatus(newStatus);

		assertThat(followDto.getId()).isEqualTo(newId);
		assertThat(followDto.getUserName()).isEqualTo(newUserName);
		assertThat(followDto.getFollowerId()).isEqualTo(newFollowerId);
		assertThat(followDto.getFollowedId()).isEqualTo(newFollowedId);
		assertThat(followDto.getCreatedAt()).isEqualTo(newCreatedAt);
		assertThat(followDto.getStatus()).isEqualTo(newStatus);
	}

}
