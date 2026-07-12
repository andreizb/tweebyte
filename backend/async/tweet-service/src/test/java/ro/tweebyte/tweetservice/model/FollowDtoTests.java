/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.model;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FollowDtoTests {

	@Test
	void getId() {
		UUID id = UUID.randomUUID();
		FollowDto followDto = new FollowDto();
		followDto.setId(id);
		assertThat(followDto.getId()).isEqualTo(id);
	}

	@Test
	void getUserName() {
		String userName = "testUser";
		FollowDto followDto = new FollowDto();
		followDto.setUserName(userName);
		assertThat(followDto.getUserName()).isEqualTo(userName);
	}

	@Test
	void getFollowerId() {
		UUID followerId = UUID.randomUUID();
		FollowDto followDto = new FollowDto();
		followDto.setFollowerId(followerId);
		assertThat(followDto.getFollowerId()).isEqualTo(followerId);
	}

	@Test
	void getFollowedId() {
		UUID followedId = UUID.randomUUID();
		FollowDto followDto = new FollowDto();
		followDto.setFollowedId(followedId);
		assertThat(followDto.getFollowedId()).isEqualTo(followedId);
	}

	@Test
	void getCreatedAt() {
		LocalDateTime createdAt = LocalDateTime.now();
		FollowDto followDto = new FollowDto();
		followDto.setCreatedAt(createdAt);
		assertThat(followDto.getCreatedAt()).isEqualTo(createdAt);
	}

	@Test
	void getStatus() {
		FollowDto.Status status = FollowDto.Status.PENDING;
		FollowDto followDto = new FollowDto();
		followDto.setStatus(status);
		assertThat(followDto.getStatus()).isEqualTo(status);
	}

	@Test
	void setId() {
		UUID id = UUID.randomUUID();
		FollowDto followDto = new FollowDto();
		assertThat(followDto.getId()).isNull(); // Initially null
		followDto.setId(id);
		assertThat(followDto.getId()).isEqualTo(id);
	}

	@Test
	void setUserName() {
		String userName = "testUser";
		FollowDto followDto = new FollowDto();
		assertThat(followDto.getUserName()).isNull(); // Initially null
		followDto.setUserName(userName);
		assertThat(followDto.getUserName()).isEqualTo(userName);
	}

	@Test
	void setFollowerId() {
		UUID followerId = UUID.randomUUID();
		FollowDto followDto = new FollowDto();
		assertThat(followDto.getFollowerId()).isNull(); // Initially null
		followDto.setFollowerId(followerId);
		assertThat(followDto.getFollowerId()).isEqualTo(followerId);
	}

	@Test
	void setFollowedId() {
		UUID followedId = UUID.randomUUID();
		FollowDto followDto = new FollowDto();
		assertThat(followDto.getFollowedId()).isNull(); // Initially null
		followDto.setFollowedId(followedId);
		assertThat(followDto.getFollowedId()).isEqualTo(followedId);
	}

	@Test
	void setCreatedAt() {
		LocalDateTime createdAt = LocalDateTime.now();
		FollowDto followDto = new FollowDto();
		assertThat(followDto.getCreatedAt()).isNull(); // Initially null
		followDto.setCreatedAt(createdAt);
		assertThat(followDto.getCreatedAt()).isEqualTo(createdAt);
	}

	@Test
	void setStatus() {
		FollowDto.Status status = FollowDto.Status.PENDING;
		FollowDto followDto = new FollowDto();
		assertThat(followDto.getStatus()).isNull(); // Initially null
		followDto.setStatus(status);
		assertThat(followDto.getStatus()).isEqualTo(status);
	}

}
