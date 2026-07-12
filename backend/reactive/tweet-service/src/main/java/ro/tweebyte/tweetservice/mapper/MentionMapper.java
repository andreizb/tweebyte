/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.mapper;

import java.util.UUID;

import org.mapstruct.Mapper;

import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.model.MentionDto;

@Mapper(componentModel = "spring")
public abstract class MentionMapper {

	// R2DBC Persistable: client-generated id + insertable flag, plus the owning tweet id.
	public MentionEntity mapFieldsToEntity(UUID userId, String text, TweetEntity tweetEntity) {
		MentionEntity mentionEntity = mapFieldsToEntity(userId, text);
		mentionEntity.setTweetId(tweetEntity.getId());
		mentionEntity.setId(UUID.randomUUID());
		mentionEntity.setInsertable(true);
		return mentionEntity;
	}

	public MentionEntity mapFieldsToEntity(UUID userId, String text) {
		return MentionEntity.builder().userId(userId).text(text).build();
	}

	public abstract MentionDto mapEntityToDto(MentionEntity mentionEntity);

}
