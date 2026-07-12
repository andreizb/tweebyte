/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.mapper;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.model.UserRegisterRequest;
import ro.tweebyte.userservice.util.MediaConstants;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Branch-coverage complement for UserMapper — exercises the ternary guards in
 * {@code mapRequestToEntity(UserRegisterRequest)}:
 * <ul>
 *   <li>biography null → defaults to empty string</li>
 *   <li>biography non-null → uses supplied value</li>
 *   <li>profilePictureId null → defaults to DEFAULT_AVATAR_ID sentinel</li>
 *   <li>profilePictureId non-null → uses supplied value</li>
 * </ul>
 */
class UserMapperBranchTests {

	private UserMapper mapper;

	@BeforeEach
	void setUp() {
		this.mapper = Mappers.getMapper(UserMapper.class);
	}

	@Test
	void mapRequestToEntity_nullBiography_defaultsToEmptyString() {
		UserRegisterRequest req = UserRegisterRequest.builder()
			.userName("alice")
			.email("a@b")
			.password("pw")
			.birthDate(LocalDate.of(1990, 1, 1))
			.biography(null) // null → default ""
			.build();

		UserEntity entity = this.mapper.mapRequestToEntity(req);

		assertThat(entity.getBiography()).isEqualTo("");
	}

	@Test
	void mapRequestToEntity_nonNullBiography_usesSuppliedValue() {
		UserRegisterRequest req = UserRegisterRequest.builder()
			.userName("alice")
			.email("a@b")
			.password("pw")
			.birthDate(LocalDate.of(1990, 1, 1))
			.biography("My bio")
			.build();

		UserEntity entity = this.mapper.mapRequestToEntity(req);

		assertThat(entity.getBiography()).isEqualTo("My bio");
	}

	@Test
	void mapRequestToEntity_nullProfilePictureId_defaultsToAvatarSentinel() {
		UserRegisterRequest req = UserRegisterRequest.builder()
			.userName("alice")
			.email("a@b")
			.password("pw")
			.birthDate(LocalDate.of(1990, 1, 1))
			.profilePictureId(null) // null → DEFAULT_AVATAR_ID
			.build();

		UserEntity entity = this.mapper.mapRequestToEntity(req);

		assertThat(entity.getProfilePictureId()).isEqualTo(MediaConstants.DEFAULT_AVATAR_ID);
	}

	@Test
	void mapRequestToEntity_nonNullProfilePictureId_usesSuppliedValue() {
		java.util.UUID pictureId = java.util.UUID.randomUUID();
		UserRegisterRequest req = UserRegisterRequest.builder()
			.userName("alice")
			.email("a@b")
			.password("pw")
			.birthDate(LocalDate.of(1990, 1, 1))
			.profilePictureId(pictureId)
			.build();

		UserEntity entity = this.mapper.mapRequestToEntity(req);

		assertThat(entity.getProfilePictureId()).isEqualTo(pictureId);
	}

}
