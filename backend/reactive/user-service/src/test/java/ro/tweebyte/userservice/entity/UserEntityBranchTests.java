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

/**
 * Branch-coverage extras for UserEntity: builder/all-args/no-args, the Persistable
 * {@code isNew()} contract, mirroring the per-class file naming convention used elsewhere
 * (e.g. *BranchTest.java).
 */
class UserEntityBranchTests {

	@Test
	void allArgsConstructorPopulatesAllFields() {
		// Reactive UserEntity field order (Lombok @AllArgsConstructor): id, userName, email,
		// biography, password, birthDate, createdAt, isPrivate, profilePictureId, isInsertable.
		UUID id = UUID.randomUUID();
		UUID profilePictureId = UUID.randomUUID();
		LocalDate birth = LocalDate.of(1990, 5, 5);
		LocalDateTime createdAt = LocalDateTime.of(2024, 1, 1, 0, 0);
		UserEntity entity = new UserEntity(id, "alice", "a@b", "bio", "pw", birth, createdAt, true, profilePictureId,
				true);
		assertThat(entity.getId()).isEqualTo(id);
		assertThat(entity.getUserName()).isEqualTo("alice");
		assertThat(entity.getEmail()).isEqualTo("a@b");
		assertThat(entity.getBiography()).isEqualTo("bio");
		assertThat(entity.getPassword()).isEqualTo("pw");
		assertThat(entity.getIsPrivate()).isTrue();
		assertThat(entity.getBirthDate()).isEqualTo(birth);
		assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
		assertThat(entity.getProfilePictureId()).isEqualTo(profilePictureId);
		assertThat(entity.isInsertable()).isTrue();
	}

	@Test
	void noArgsConstructorYieldsNullFields() {
		UserEntity entity = new UserEntity();
		assertThat(entity.getId()).isNull();
		assertThat(entity.getUserName()).isNull();
		assertThat(entity.getEmail()).isNull();
		assertThat(entity.getBiography()).isNull();
		assertThat(entity.getPassword()).isNull();
		assertThat(entity.getIsPrivate()).isNull();
		assertThat(entity.getBirthDate()).isNull();
		assertThat(entity.getCreatedAt()).isNull();
	}

	@Test
	void builderPopulatesAllFields() {
		UUID id = UUID.randomUUID();
		LocalDate birth = LocalDate.of(2000, 2, 2);
		LocalDateTime createdAt = LocalDateTime.now();
		UserEntity entity = UserEntity.builder()
			.id(id)
			.userName("bob")
			.email("b@x")
			.biography("b")
			.password("h")
			.isPrivate(false)
			.birthDate(birth)
			.createdAt(createdAt)
			.build();
		assertThat(entity.getId()).isEqualTo(id);
		assertThat(entity.getUserName()).isEqualTo("bob");
		assertThat(entity.getEmail()).isEqualTo("b@x");
		assertThat(entity.getBiography()).isEqualTo("b");
		assertThat(entity.getPassword()).isEqualTo("h");
		assertThat(entity.getIsPrivate()).isFalse();
		assertThat(entity.getBirthDate()).isEqualTo(birth);
		assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
	}

	@Test
	void builderInstanceReturnsBuilderType() {
		// Cover the static builder() factory method
		assertThat(UserEntity.builder()).isNotNull();
	}

	@Test
	void isPrivateFalseRoundTrips() {
		UserEntity entity = UserEntity.builder().isPrivate(false).build();
		assertThat(entity.getIsPrivate()).isFalse();
	}

	@Test
	void settersChainOnSameInstance() {
		UserEntity entity = new UserEntity();
		entity.setUserName("x");
		entity.setEmail("y");
		assertThat(entity.getUserName()).isEqualTo("x");
		assertThat(entity.getEmail()).isEqualTo("y");
	}

	@Test
	void overwriteFieldKeepsLatestValue() {
		UserEntity entity = new UserEntity();
		entity.setUserName("first");
		entity.setUserName("second");
		assertThat(entity.getUserName()).isEqualTo("second");
	}

	@Test
	void nullIdMarksEntityAsNew() {
		// Persistable contract: a null id always reports new, even without the insertable flag.
		UserEntity entity = new UserEntity();
		entity.setInsertable(false);
		assertThat(entity.getId()).isNull();
		assertThat(entity.isNew()).isTrue();
	}

}
