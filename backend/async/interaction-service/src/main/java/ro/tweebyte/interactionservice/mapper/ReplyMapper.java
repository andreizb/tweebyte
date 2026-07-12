/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.mapper;

import java.time.LocalDateTime;
import java.util.UUID;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.model.ReplyCreateRequest;
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.ReplyUpdateRequest;

@Mapper(componentModel = "spring")
public abstract class ReplyMapper {

	public ReplyEntity mapRequestToEntity(ReplyCreateRequest request) {
		ReplyEntity replyEntity = mapCreationRequestToEntity(request);
		replyEntity.setId(UUID.randomUUID());
		replyEntity.setCreatedAt(LocalDateTime.now());
		return replyEntity;
	}

	@BeanMapping(ignoreByDefault = true)
	@Mapping(source = "id", target = "id")
	public abstract ReplyDto mapEntityToCreationDto(ReplyEntity entity);

	public abstract ReplyDto mapEntityToDto(ReplyEntity entity, String userName);

	public void mapRequestToEntity(ReplyUpdateRequest request, ReplyEntity entity) {
		if (request == null || entity == null) {
			return;
		}
		if (request.getContent() != null) {
			entity.setContent(request.getContent());
		}
		if (request.getUserId() != null) {
			entity.setUserId(request.getUserId());
		}
	}

	protected abstract ReplyEntity mapCreationRequestToEntity(ReplyCreateRequest request);

}
