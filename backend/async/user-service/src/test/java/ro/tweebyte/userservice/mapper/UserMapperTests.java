/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.model.TweetDto;
import ro.tweebyte.userservice.model.UserDto;
import ro.tweebyte.userservice.model.UserRegisterRequest;
import ro.tweebyte.userservice.model.UserUpdateRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the MapStruct-generated UserMapperImpl: the abstract profile/summary
 * field-copies plus the concrete update and register paths. The mapper is a pure
 * transformation; bcrypt hashing is the caller's responsibility (UserService and
 * AuthenticationService own the encoder), so no encoder is wired in here.
 */
class UserMapperTests {

	private UserMapper mapper;

	@BeforeEach
	void setUp() {
		this.mapper = Mappers.getMapper(UserMapper.class);
	}

	@Test
	void mapToProfileDtoAllArgsNullReturnsNull() {
		assertThat(this.mapper.mapToProfileDto(null, null, null, null)).isNull();
	}

	@Test
	void mapToProfileDtoCopiesEntityAndCounters() {
		UserEntity entity = new UserEntity();
		entity.setId(UUID.randomUUID());
		entity.setUserName("alice");
		entity.setEmail("a@b");
		entity.setBiography("bio");
		entity.setIsPrivate(true);
		entity.setBirthDate(LocalDate.of(1990, 1, 1));
		entity.setCreatedAt(LocalDateTime.of(2024, 1, 1, 0, 0));

		UserDto dto = this.mapper.mapToProfileDto(entity, 5L, 7L, List.of(new TweetDto()));
		assertThat(dto).isNotNull();
		assertThat(dto.getId()).isEqualTo(entity.getId());
		assertThat(dto.getUserName()).isEqualTo("alice");
		assertThat(dto.getEmail()).isEqualTo("a@b");
		assertThat(dto.getBiography()).isEqualTo("bio");
		assertThat(dto.getIsPrivate()).isTrue();
		assertThat(dto.getFollowing()).isEqualTo(5L);
		assertThat(dto.getFollowers()).isEqualTo(7L);
		assertThat(dto.getTweets()).hasSize(1);
	}

	@Test
	void mapToProfileDtoNullEntityWithCountersStillBuildsDto() {
		UserDto dto = this.mapper.mapToProfileDto(null, 1L, 2L, Collections.emptyList());
		assertThat(dto).isNotNull();
		assertThat(dto.getFollowing()).isEqualTo(1L);
		assertThat(dto.getFollowers()).isEqualTo(2L);
		assertThat(dto.getTweets()).isNotNull();
	}

	@Test
	void mapToProfileDtoNullTweetsDefaultsToEmptyList() {
		UserEntity entity = new UserEntity();
		entity.setId(UUID.randomUUID());
		UserDto dto = this.mapper.mapToProfileDto(entity, 0L, 0L, null);
		assertThat(dto).isNotNull();
		assertThat(dto.getTweets()).isNotNull();
		assertThat(dto.getTweets()).isEmpty();
	}

	@Test
	void mapToSummaryDtoNullEntityReturnsNull() {
		assertThat(this.mapper.mapToSummaryDto(null)).isNull();
	}

	@Test
	void mapToSummaryDtoCopiesSummaryFields() {
		UserEntity entity = new UserEntity();
		entity.setId(UUID.randomUUID());
		entity.setUserName("bob");
		entity.setIsPrivate(false);
		entity.setCreatedAt(LocalDateTime.of(2024, 5, 5, 5, 5));
		UserDto dto = this.mapper.mapToSummaryDto(entity);
		assertThat(dto).isNotNull();
		assertThat(dto.getUserName()).isEqualTo("bob");
		assertThat(dto.getIsPrivate()).isFalse();
		assertThat(dto.getCreatedAt()).isEqualTo(entity.getCreatedAt());
		// summary should not include profile-only fields
		assertThat(dto.getEmail()).isNull();
		assertThat(dto.getBiography()).isNull();
	}

	@Test
	void mapRequestToEntityUpdateNullRequestNoOp() {
		UserEntity entity = new UserEntity();
		entity.setUserName("alice");
		this.mapper.mapRequestToEntity((UserUpdateRequest) null, entity);
		assertThat(entity.getUserName()).isEqualTo("alice");
	}

	@Test
	void mapRequestToEntityUpdateAllNullsLeavesEntity() {
		UserEntity entity = new UserEntity();
		entity.setUserName("alice");
		entity.setEmail("a@b");
		UserUpdateRequest req = new UserUpdateRequest(); // all nulls
		this.mapper.mapRequestToEntity(req, entity);
		assertThat(entity.getUserName()).isEqualTo("alice");
		assertThat(entity.getEmail()).isEqualTo("a@b");
	}

	@Test
	void mapRequestToEntityUpdateAllPresentOverwrites() {
		UserEntity entity = new UserEntity();
		entity.setUserName("old");
		entity.setEmail("old@x");
		entity.setBiography("oldbio");
		entity.setPassword("oldhash");
		entity.setBirthDate(LocalDate.of(1990, 1, 1));
		entity.setIsPrivate(false);

		UserUpdateRequest req = new UserUpdateRequest();
		req.setUserName("new");
		req.setEmail("new@x");
		req.setBiography("newbio");
		req.setPassword("newpw");
		req.setBirthDate(LocalDate.of(2000, 2, 2));
		req.setIsPrivate(true);

		// The mapper copies the password through verbatim; bcrypt encoding is
		// performed by UserService before this call, so a non-null request
		// password lands on the entity unchanged.
		this.mapper.mapRequestToEntity(req, entity);
		assertThat(entity.getUserName()).isEqualTo("new");
		assertThat(entity.getEmail()).isEqualTo("new@x");
		assertThat(entity.getBiography()).isEqualTo("newbio");
		assertThat(entity.getPassword()).isEqualTo("newpw");
		assertThat(entity.getBirthDate()).isEqualTo(LocalDate.of(2000, 2, 2));
		assertThat(entity.getIsPrivate()).isTrue();
	}

	@Test
	void mapRequestToEntityUpdateOnlyUserName() {
		UserEntity entity = new UserEntity();
		entity.setUserName("old");
		entity.setEmail("keep@x");
		UserUpdateRequest req = new UserUpdateRequest();
		req.setUserName("new");
		this.mapper.mapRequestToEntity(req, entity);
		assertThat(entity.getUserName()).isEqualTo("new");
		assertThat(entity.getEmail()).isEqualTo("keep@x");
	}

	@Test
	void mapRegisterRequestToUserEntityNullReturnsNull() {
		// mapRegisterRequestToUserEntity is the protected MapStruct hook; on null input
		// the generated code short-circuits to null. The public wrapper does NOT guard
		// against null, so test the protected variant directly via reflection.
		java.lang.reflect.Method m;
		try {
			m = this.mapper.getClass().getDeclaredMethod("mapRegisterRequestToUserEntity", UserRegisterRequest.class);
			m.setAccessible(true);
			Object result = m.invoke(this.mapper, (Object) null);
			assertThat(result).isNull();
		}
		catch (Exception ex) {
			throw new AssertionError(ex);
		}
	}

	@Test
	void mapRequestToEntityRegisterPopulatesAllFields() {
		UserRegisterRequest req = UserRegisterRequest.builder()
			.userName("alice")
			.email("a@b")
			.biography("bio")
			.password("pw")
			.birthDate(LocalDate.of(1990, 1, 2))
			.isPrivate(true)
			.build();
		UserEntity entity = this.mapper.mapRequestToEntity(req);
		assertThat(entity).isNotNull();
		assertThat(entity.getUserName()).isEqualTo("alice");
		assertThat(entity.getEmail()).isEqualTo("a@b");
		assertThat(entity.getBiography()).isEqualTo("bio");
		// The register mapping ignores password; AuthenticationService bcrypt-encodes
		// and sets it after this call, so the mapped entity carries no password yet.
		assertThat(entity.getPassword()).isNull();
		assertThat(entity.getBirthDate()).isEqualTo(LocalDate.of(1990, 1, 2));
		assertThat(entity.getIsPrivate()).isTrue();
		// Register flags the entity insertable so save() does a direct INSERT (no merge probe).
		assertThat(entity.isInsertable()).isTrue();
		assertThat(entity.isNew()).isTrue();
		assertThat(entity.getId()).isNotNull();
		assertThat(entity.getCreatedAt()).isNotNull();
	}

	@Test
	void mapRequestToEntityRegisterNullPrivateUsesProvidedPicture() {
		// Exercises the complementary ternary branches to the all-present case above:
		// a null isPrivate defaults to FALSE, while an explicit profilePictureId is kept
		// verbatim (rather than falling back to the seeded default-avatar sentinel).
		UUID picture = UUID.randomUUID();
		UserRegisterRequest req = UserRegisterRequest.builder()
			.userName("bob")
			.email("b@c")
			.password("pw")
			.birthDate(LocalDate.of(1991, 3, 4))
			.profilePictureId(picture)
			.build();
		UserEntity entity = this.mapper.mapRequestToEntity(req);
		assertThat(entity).isNotNull();
		assertThat(entity.getIsPrivate()).isFalse();
		assertThat(entity.getProfilePictureId()).isEqualTo(picture);
	}

}
