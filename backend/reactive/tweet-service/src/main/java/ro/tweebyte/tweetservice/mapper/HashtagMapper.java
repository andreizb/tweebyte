/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.mapper;

import java.util.UUID;

import org.mapstruct.Mapper;

import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.model.HashtagDto;

@Mapper(componentModel = "spring")
public abstract class HashtagMapper {

	// R2DBC Persistable: client-generated id + insertable flag so save() does INSERT.
	public HashtagEntity mapTextToEntity(String text) {
		HashtagEntity hashtagEntity = new HashtagEntity();
		hashtagEntity.setText(text);
		hashtagEntity.setId(UUID.randomUUID());
		hashtagEntity.setInsertable(true);
		return hashtagEntity;
	}

	public abstract HashtagDto mapEntityToDto(HashtagEntity hashtagEntity);

}
