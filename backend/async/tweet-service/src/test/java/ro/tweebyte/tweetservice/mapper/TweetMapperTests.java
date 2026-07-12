/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.mapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.model.ReplyDto;
import ro.tweebyte.tweetservice.model.TweetCreationRequest;
import ro.tweebyte.tweetservice.model.TweetDto;
import ro.tweebyte.tweetservice.model.TweetUpdateRequest;

import static org.assertj.core.api.Assertions.assertThat;

class TweetMapperTests {

	private final TweetMapper tweetMapper = Mappers.getMapper(TweetMapper.class);

	@Test
	void testMapEntityToDto() {
		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setId(UUID.randomUUID());
		tweetEntity.setContent("Tweet Content");
		tweetEntity.setCreatedAt(LocalDateTime.now());

		TweetDto tweetDto = this.tweetMapper.mapEntityToDto(tweetEntity);

		assertThat(tweetDto.getId()).isEqualTo(tweetEntity.getId());
		assertThat(tweetDto.getContent()).isEqualTo(tweetEntity.getContent());
		assertThat(tweetDto.getCreatedAt()).isEqualTo(tweetEntity.getCreatedAt());
	}

	@Test
	void testMapEntityToDtoWithLikesRepliesRetweets() {
		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setId(UUID.randomUUID());
		tweetEntity.setContent("Tweet Content");
		tweetEntity.setCreatedAt(LocalDateTime.now());
		Long likesCount = 10L;
		Long repliesCount = 5L;
		Long retweetsCount = 7L;
		ReplyDto topReply = new ReplyDto();

		TweetDto tweetDto = this.tweetMapper.mapEntityToDto(tweetEntity, likesCount, repliesCount, retweetsCount,
				topReply);

		assertThat(tweetDto.getId()).isEqualTo(tweetEntity.getId());
		assertThat(tweetDto.getContent()).isEqualTo(tweetEntity.getContent());
		assertThat(tweetDto.getCreatedAt()).isEqualTo(tweetEntity.getCreatedAt());
		assertThat(tweetDto.getLikesCount()).isEqualTo(likesCount);
		assertThat(tweetDto.getRepliesCount()).isEqualTo(repliesCount);
		assertThat(tweetDto.getRetweetsCount()).isEqualTo(retweetsCount);
		assertThat(tweetDto.getTopReply()).isEqualTo(topReply);
	}

	@Test
	void testMapEntityToDtoWithReplies() {
		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setId(UUID.randomUUID());
		tweetEntity.setContent("Tweet Content");
		tweetEntity.setCreatedAt(LocalDateTime.now());
		List<ReplyDto> replies = new ArrayList<>();

		TweetDto tweetDto = this.tweetMapper.mapEntityToDto(tweetEntity, null, null, null, replies);

		assertThat(tweetDto.getId()).isEqualTo(tweetEntity.getId());
		assertThat(tweetDto.getContent()).isEqualTo(tweetEntity.getContent());
		assertThat(tweetDto.getCreatedAt()).isEqualTo(tweetEntity.getCreatedAt());
		assertThat(tweetDto.getReplies()).isEqualTo(replies);
	}

	@Test
	void mapCreationRequestToEntity_shouldMapCorrectly() {
		TweetCreationRequest request = new TweetCreationRequest();
		request.setUserId(UUID.randomUUID());
		request.setContent("Test Content");

		TweetEntity result = this.tweetMapper.mapCreationRequestToEntity(request);

		assertThat(result).isNotNull();
		assertThat(result.getUserId()).isEqualTo(request.getUserId());
		assertThat(result.getContent()).isEqualTo(request.getContent());
	}

	@Test
	void mapEntityToCreationDto_shouldMapCorrectly() {
		UUID id = UUID.randomUUID();
		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setId(id);

		TweetDto result = this.tweetMapper.mapEntityToCreationDto(tweetEntity);

		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(id);
	}

	@Test
	void mapEntityToCreationDto_shouldReturnNullWhenEntityIsNull() {
		TweetDto result = this.tweetMapper.mapEntityToCreationDto(null);

		assertThat(result).isNull();
	}

	@Test
	void testMapUpdateRequestToEntity() {
		TweetUpdateRequest request = new TweetUpdateRequest();
		request.setContent("Updated Content");

		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setContent("Old Content");

		TweetEntity updatedEntity = this.tweetMapper.mapUpdateRequestToEntity(request, tweetEntity);

		assertThat(updatedEntity).isNotNull();
		assertThat(updatedEntity.getContent()).isEqualTo("Updated Content");
	}

	@Test
	void testMapMentions() {
		MentionEntity mentionEntity = new MentionEntity();
		mentionEntity.setId(UUID.randomUUID());
		mentionEntity.setUserId(UUID.randomUUID());
		mentionEntity.setText("@user");

		Set<MentionEntity> mentions = new HashSet<>();
		mentions.add(mentionEntity);

		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setMentions(mentions);

		TweetDto tweetDto = this.tweetMapper.mapEntityToDto(tweetEntity);

		assertThat(tweetDto.getMentions()).isNull();
	}

	@Test
	void testMapHashtags() {
		HashtagEntity hashtagEntity = new HashtagEntity();
		hashtagEntity.setId(UUID.randomUUID());
		hashtagEntity.setText("#hashtag");

		Set<HashtagEntity> hashtags = new HashSet<>();
		hashtags.add(hashtagEntity);

		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setHashtags(hashtags);

		TweetDto tweetDto = this.tweetMapper.mapEntityToDto(tweetEntity);

		assertThat(tweetDto.getHashtags()).isNull();
	}

	@Test
	void testMapNullMentionsAndHashtags() {
		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setMentions(null);
		tweetEntity.setHashtags(null);

		TweetDto tweetDto = this.tweetMapper.mapEntityToDto(tweetEntity);

		assertThat(tweetDto.getMentions()).isNull();
		assertThat(tweetDto.getHashtags()).isNull();
	}

}
