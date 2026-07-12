/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.mapper;

import java.time.LocalDateTime;
import java.util.UUID;

import org.mapstruct.Mapper;

import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.model.RetweetCreateRequest;
import ro.tweebyte.interactionservice.model.RetweetDto;
import ro.tweebyte.interactionservice.model.RetweetUpdateRequest;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.UserDto;

@Mapper(componentModel = "spring")
public abstract class RetweetMapper {

	public RetweetEntity mapRequestToEntity(RetweetCreateRequest request) {
		RetweetEntity retweetEntity = mapCreationRequestToEntity(request);
		retweetEntity.setId(UUID.randomUUID());
		retweetEntity.setCreatedAt(LocalDateTime.now());
		return retweetEntity;
	}

	public abstract RetweetDto mapEntityToDto(RetweetEntity entity);

	public RetweetDto mapEntityToDto(RetweetEntity entity, UserDto user) {
		RetweetDto dto = mapEntityToDto(entity);
		if (dto != null) {
			dto.setUser(user);
		}
		return dto;
	}

	public RetweetDto mapEntityToDto(RetweetEntity entity, UserDto user, TweetDto tweet) {
		RetweetDto dto = mapEntityToDto(entity, user);
		if (dto != null) {
			dto.setTweet(tweet);
		}
		return dto;
	}

	public void mapRequestToEntity(RetweetUpdateRequest request, RetweetEntity entity) {
		if (request == null || entity == null) {
			return;
		}
		if (request.getContent() != null) {
			entity.setContent(request.getContent());
		}
		if (request.getRetweeterId() != null) {
			entity.setRetweeterId(request.getRetweeterId());
		}
	}

	protected abstract RetweetEntity mapCreationRequestToEntity(RetweetCreateRequest request);

}
