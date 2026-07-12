/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.mapper;

import java.time.LocalDateTime;
import java.util.UUID;

import org.mapstruct.Mapper;

import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.model.FollowDto;
import ro.tweebyte.interactionservice.model.FollowingEntryDto;

@Mapper(componentModel = "spring")
public abstract class FollowMapper {

	public FollowEntity mapRequestToEntity(UUID followerId, UUID followedId, FollowEntity.Status status) {
		FollowEntity followEntity = mapCreationRequestToEntity(followerId, followedId, status);
		followEntity.setId(UUID.randomUUID());
		followEntity.setCreatedAt(LocalDateTime.now());
		followEntity.setInsertable(true);
		return followEntity;
	}

	public abstract FollowDto mapEntityToDto(FollowEntity followEntity);

	public abstract FollowDto mapEntityToDto(FollowEntity followEntity, String userName);

	public abstract FollowingEntryDto mapEntityToEntry(FollowEntity followEntity, String userName);

	protected abstract FollowEntity mapCreationRequestToEntity(UUID followerId, UUID followedId,
			FollowEntity.Status status);

}
