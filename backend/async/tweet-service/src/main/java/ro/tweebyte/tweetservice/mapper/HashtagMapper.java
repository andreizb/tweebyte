/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.mapper;

import org.mapstruct.Mapper;

import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.model.HashtagDto;

@Mapper(componentModel = "spring")
public abstract class HashtagMapper {

	public abstract HashtagEntity mapTextToEntity(String text);

	public abstract HashtagDto mapEntityToDto(HashtagEntity hashtagEntity);

}
