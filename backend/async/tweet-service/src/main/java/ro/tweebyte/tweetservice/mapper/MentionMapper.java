/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.mapper;

import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.model.MentionDto;

@Mapper(componentModel = "spring")
public abstract class MentionMapper {

	@Mapping(source = "userId", target = "userId")
	@Mapping(source = "text", target = "text")
	@Mapping(source = "tweetEntity", target = "tweetEntity")
	@Mapping(target = "id", ignore = true)
	public abstract MentionEntity mapFieldsToEntity(UUID userId, String text, TweetEntity tweetEntity);

	@Mapping(source = "userId", target = "userId")
	@Mapping(source = "text", target = "text")
	@Mapping(target = "id", ignore = true)
	public abstract MentionEntity mapFieldsToEntity(UUID userId, String text);

	public abstract MentionDto mapEntityToDto(MentionEntity mentionEntity);

}
