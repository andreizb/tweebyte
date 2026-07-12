/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.mapper;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.model.LikeDto;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.UserDto;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Branch-coverage tests for LikeMapper — exercises: - mapCreationRequestToEntity each
 * clause of the &amp;&amp; null-guard going both ways - mapToDto(LikeEntity, UserDto)
 * with null entity (likeDto == null branch) - mapToDto(LikeEntity, TweetDto) with null
 * entity (likeDto == null branch)
 */
class LikeMapperBranchTests {

	private final LikeMapper likeMapper = org.mapstruct.factory.Mappers.getMapper(LikeMapper.class);

	@Test
	void mapCreationRequestToEntity_userIdNonNull_returnsEntity() {
		// userId != null short-circuits the first &&, exercising the false arm.
		LikeEntity entity = this.likeMapper.mapCreationRequestToEntity(UUID.randomUUID(), null, null);
		assertThat(entity).isNotNull();
	}

	@Test
	void mapCreationRequestToEntity_likeableIdNonNull_returnsEntity() {
		// userId null, likeableId != null → second && false arm.
		LikeEntity entity = this.likeMapper.mapCreationRequestToEntity(null, UUID.randomUUID(), null);
		assertThat(entity).isNotNull();
	}

	@Test
	void mapCreationRequestToEntity_likeableTypeNonNull_returnsEntity() {
		// userId null, likeableId null, likeableType != null → third && false arm.
		LikeEntity entity = this.likeMapper.mapCreationRequestToEntity(null, null, LikeEntity.LikeableType.TWEET);
		assertThat(entity).isNotNull();
	}

	@Test
	void mapToDtoUserOverload_nullEntity_returnsNull() {
		// mapEntityToDto returns null → guard skips setUser and returns null.
		UserDto userDto = new UserDto();
		LikeDto result = this.likeMapper.mapToDto((LikeEntity) null, userDto);
		assertThat(result).isNull();
	}

	@Test
	void mapToDtoTweetOverload_nullEntity_returnsNull() {
		// mapEntityToDto returns null → guard skips setTweet and returns null.
		TweetDto tweetDto = new TweetDto();
		LikeDto result = this.likeMapper.mapToDto((LikeEntity) null, tweetDto);
		assertThat(result).isNull();
	}

}
