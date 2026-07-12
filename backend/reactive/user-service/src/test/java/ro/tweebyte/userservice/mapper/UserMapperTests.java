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
 * field-copies plus the register defaults. Password hashing now lives in the service
 * layer, so the mapper copies the password verbatim (update) or leaves it for the caller
 * to set (register).
 */
class UserMapperTests {

	private UserMapper mapper;

	@BeforeEach
	void setUp() {
		this.mapper = Mappers.getMapper(UserMapper.class);
	}

	// --- mapToProfileDto ----------------------------------------------

	@Test
	void mapToProfileDtoAllArgsNullReturnsNull() {
		assertThat(this.mapper.mapToProfileDto(null, null, null, null)).isNull();
	}

	@Test
	void mapToProfileDtoNullEntityWithCountersStillBuildsDto() {
		// MapStruct's multi-source guard only short-circuits to null when EVERY
		// source arg is null; a non-null counter still yields a (sparse) DTO.
		// The service never maps a null entity (it orElseThrows first), so this
		// corner is not a production path.
		UserDto dto = this.mapper.mapToProfileDto(null, 1L, 2L, Collections.emptyList());
		assertThat(dto).isNotNull();
		assertThat(dto.getFollowing()).isEqualTo(1L);
		assertThat(dto.getFollowers()).isEqualTo(2L);
		assertThat(dto.getTweets()).isNotNull();
	}

	@Test
	void mapToProfileDtoCopiesAllFieldsAndTweets() {
		UserEntity entity = new UserEntity();
		entity.setId(UUID.randomUUID());
		entity.setUserName("alice");
		entity.setEmail("a@b");
		entity.setBiography("bio");
		entity.setIsPrivate(true);
		entity.setBirthDate(LocalDate.of(1990, 1, 2));
		entity.setCreatedAt(LocalDateTime.of(2024, 1, 1, 0, 0));

		TweetDto tweet = new TweetDto();
		tweet.setId(UUID.randomUUID());
		UserDto dto = this.mapper.mapToProfileDto(entity, 5L, 7L, List.of(tweet));

		assertThat(dto.getId()).isEqualTo(entity.getId());
		assertThat(dto.getUserName()).isEqualTo("alice");
		assertThat(dto.getEmail()).isEqualTo("a@b");
		assertThat(dto.getBiography()).isEqualTo("bio");
		assertThat(dto.getIsPrivate()).isTrue();
		assertThat(dto.getBirthDate()).isEqualTo(LocalDate.of(1990, 1, 2));
		assertThat(dto.getCreatedAt()).isEqualTo(LocalDateTime.of(2024, 1, 1, 0, 0));
		assertThat(dto.getFollowing()).isEqualTo(5L);
		assertThat(dto.getFollowers()).isEqualTo(7L);
		assertThat(dto.getTweets()).hasSize(1);
	}

	@Test
	void mapToProfileDtoNullTweetsYieldsEmptyList() {
		UserEntity entity = new UserEntity();
		entity.setId(UUID.randomUUID());

		UserDto dto = this.mapper.mapToProfileDto(entity, 0L, 0L, null);

		assertThat(dto.getTweets()).isNotNull();
		assertThat(dto.getTweets()).isEmpty();
	}

	// --- mapToSummaryDto ----------------------------------------------

	@Test
	void mapToSummaryDtoReturnsNullWhenEntityNull() {
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

		assertThat(dto.getId()).isEqualTo(entity.getId());
		assertThat(dto.getUserName()).isEqualTo("bob");
		assertThat(dto.getIsPrivate()).isFalse();
		assertThat(dto.getCreatedAt()).isEqualTo(entity.getCreatedAt());
		// summary should not include profile-only fields
		assertThat(dto.getEmail()).isNull();
		assertThat(dto.getBiography()).isNull();
	}

	// --- mapRequestToEntity (UserUpdateRequest variant) ---------------

	@Test
	void mapRequestToEntityUpdateNoOpWhenRequestNull() {
		UserEntity entity = new UserEntity();
		entity.setUserName("alice");
		this.mapper.mapRequestToEntity((UserUpdateRequest) null, entity);
		assertThat(entity.getUserName()).isEqualTo("alice");
	}

	@Test
	void mapRequestToEntityUpdateAllNullsLeavesEntityIntact() {
		UserEntity entity = new UserEntity();
		entity.setUserName("alice");
		entity.setEmail("a@b");
		entity.setBiography("bio");
		entity.setPassword("oldhash");
		entity.setBirthDate(LocalDate.of(1990, 1, 1));
		entity.setIsPrivate(false);

		UserUpdateRequest req = new UserUpdateRequest(); // all nulls

		this.mapper.mapRequestToEntity(req, entity);

		assertThat(entity.getUserName()).isEqualTo("alice");
		assertThat(entity.getEmail()).isEqualTo("a@b");
		assertThat(entity.getBiography()).isEqualTo("bio");
		assertThat(entity.getPassword()).isEqualTo("oldhash");
		assertThat(entity.getBirthDate()).isEqualTo(LocalDate.of(1990, 1, 1));
		assertThat(entity.getIsPrivate()).isFalse();
	}

	@Test
	void mapRequestToEntityUpdateAllPresentOverwritesFields() {
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
		// the caller hashes before mapping; the mapper copies the value verbatim
		req.setPassword("alreadyHashed");
		req.setBirthDate(LocalDate.of(2000, 2, 2));
		req.setIsPrivate(true);

		this.mapper.mapRequestToEntity(req, entity);

		assertThat(entity.getUserName()).isEqualTo("new");
		assertThat(entity.getEmail()).isEqualTo("new@x");
		assertThat(entity.getBiography()).isEqualTo("newbio");
		assertThat(entity.getPassword()).isEqualTo("alreadyHashed");
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
	void mapRequestToEntityUpdateOnlyEmail() {
		UserEntity entity = new UserEntity();
		entity.setEmail("old@x");
		UserUpdateRequest req = new UserUpdateRequest();
		req.setEmail("new@x");
		this.mapper.mapRequestToEntity(req, entity);
		assertThat(entity.getEmail()).isEqualTo("new@x");
	}

	@Test
	void mapRequestToEntityUpdateOnlyBiography() {
		UserEntity entity = new UserEntity();
		entity.setBiography("old");
		UserUpdateRequest req = new UserUpdateRequest();
		req.setBiography("new");
		this.mapper.mapRequestToEntity(req, entity);
		assertThat(entity.getBiography()).isEqualTo("new");
	}

	@Test
	void mapRequestToEntityUpdateOnlyPasswordCopiedVerbatim() {
		UserEntity entity = new UserEntity();
		entity.setPassword("oldhash");
		UserUpdateRequest req = new UserUpdateRequest();
		req.setPassword("preHashed");
		this.mapper.mapRequestToEntity(req, entity);
		assertThat(entity.getPassword()).isEqualTo("preHashed");
	}

	@Test
	void mapRequestToEntityUpdateOnlyBirthDate() {
		UserEntity entity = new UserEntity();
		entity.setBirthDate(LocalDate.of(1990, 1, 1));
		UserUpdateRequest req = new UserUpdateRequest();
		req.setBirthDate(LocalDate.of(2000, 1, 1));
		this.mapper.mapRequestToEntity(req, entity);
		assertThat(entity.getBirthDate()).isEqualTo(LocalDate.of(2000, 1, 1));
	}

	@Test
	void mapRequestToEntityUpdateOnlyIsPrivate() {
		UserEntity entity = new UserEntity();
		entity.setIsPrivate(false);
		UserUpdateRequest req = new UserUpdateRequest();
		req.setIsPrivate(true);
		this.mapper.mapRequestToEntity(req, entity);
		assertThat(entity.getIsPrivate()).isTrue();
	}

	// --- mapRequestToEntity (UserRegisterRequest variant) -------------

	@Test
	void mapRequestToEntityRegisterReturnsNullWhenRequestNull() {
		assertThat(this.mapper.mapRequestToEntity((UserRegisterRequest) null)).isNull();
	}

	@Test
	void mapRequestToEntityRegisterPopulatesAllFieldsExceptPassword() {
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
		// the mapper leaves the password unset; the service hashes and sets it
		assertThat(entity.getPassword()).isNull();
		assertThat(entity.getBirthDate()).isEqualTo(LocalDate.of(1990, 1, 2));
		assertThat(entity.getIsPrivate()).isTrue();
		assertThat(entity.isInsertable()).isTrue();
		assertThat(entity.isNew()).isTrue();
		assertThat(entity.getId()).isNotNull();
		assertThat(entity.getCreatedAt()).isNotNull();
	}

	@Test
	void mapRequestToEntityRegisterDefaultsIsPrivateToFalseWhenNull() {
		// when request.getIsPrivate() == null we use Boolean.FALSE
		UserRegisterRequest req = UserRegisterRequest.builder()
			.userName("bob")
			.email("b@b")
			.password("pw")
			.birthDate(LocalDate.of(1991, 1, 1))
			.isPrivate(null)
			.build();

		UserEntity entity = this.mapper.mapRequestToEntity(req);

		assertThat(entity).isNotNull();
		assertThat(entity.getIsPrivate()).isFalse();
	}

	@Test
	void mapRequestToEntityRegisterIsPrivateFalseExplicit() {
		UserRegisterRequest req = UserRegisterRequest.builder()
			.userName("bob")
			.email("b@b")
			.password("pw")
			.birthDate(LocalDate.of(1991, 1, 1))
			.isPrivate(false)
			.build();

		UserEntity entity = this.mapper.mapRequestToEntity(req);
		assertThat(entity.getIsPrivate()).isFalse();
	}

}
