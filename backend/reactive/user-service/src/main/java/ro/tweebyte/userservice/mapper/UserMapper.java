/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.model.TweetDto;
import ro.tweebyte.userservice.model.UserDto;
import ro.tweebyte.userservice.model.UserRegisterRequest;
import ro.tweebyte.userservice.model.UserUpdateRequest;
import ro.tweebyte.userservice.util.MediaConstants;

@Mapper(componentModel = "spring")
public abstract class UserMapper {

	@Mapping(target = "tweets", source = "tweets", defaultExpression = "java(java.util.Collections.emptyList())")
	public abstract UserDto mapToProfileDto(UserEntity entity, Long following, Long followers, List<TweetDto> tweets);

	@Mapping(target = "email", ignore = true)
	@Mapping(target = "biography", ignore = true)
	@Mapping(target = "birthDate", ignore = true)
	@Mapping(target = "profilePictureId", ignore = true)
	@Mapping(target = "following", ignore = true)
	@Mapping(target = "followers", ignore = true)
	@Mapping(target = "tweets", ignore = true)
	public abstract UserDto mapToSummaryDto(UserEntity entity);

	// Password hashing is the caller's responsibility: UserService and
	// AuthenticationService own the BCryptPasswordEncoder and encode before this copy, so
	// the mapper stays a pure transformation with no injected Spring beans.
	@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
	public abstract void mapRequestToEntity(UserUpdateRequest request, @MappingTarget UserEntity entity);

	public UserEntity mapRequestToEntity(UserRegisterRequest request) {
		if (request == null) {
			return null;
		}
		UserEntity userEntity = mapRegisterRequestToUserEntity(request);
		userEntity.setId(UUID.randomUUID());
		userEntity.setCreatedAt(LocalDateTime.now());
		// is_private is NOT NULL; default to false when the request omits it.
		userEntity.setIsPrivate((request.getIsPrivate() != null) ? request.getIsPrivate() : Boolean.FALSE);
		// biography is NOT NULL; default to empty when the request omits it. MVC binds an empty
		// multipart field to "" but WebFlux binds it to null — defaulting here keeps both stacks identical.
		userEntity.setBiography((request.getBiography() != null) ? request.getBiography() : "");
		// default to the seeded avatar sentinel so profile_picture_id is never
		// user-facing NULL.
		userEntity.setProfilePictureId((request.getProfilePictureId() != null) ? request.getProfilePictureId()
				: MediaConstants.DEFAULT_AVATAR_ID);
		// R2DBC Persistable: flag insertable so save() does INSERT despite the client-set
		// id.
		userEntity.setInsertable(true);
		return userEntity;
	}

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "password", ignore = true)
	@Mapping(target = "isInsertable", ignore = true)
	protected abstract UserEntity mapRegisterRequestToUserEntity(UserRegisterRequest request);

}
