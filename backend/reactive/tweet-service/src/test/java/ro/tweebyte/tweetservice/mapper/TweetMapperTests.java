/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.mapper;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.model.ReplyDto;
import ro.tweebyte.tweetservice.model.TweetCreationRequest;
import ro.tweebyte.tweetservice.model.TweetDto;
import ro.tweebyte.tweetservice.model.TweetUpdateRequest;
import ro.tweebyte.tweetservice.model.UserDto;

import static org.assertj.core.api.Assertions.assertThat;

class TweetMapperTests {

	private final TweetMapper tweetMapper = Mappers.getMapper(TweetMapper.class);

	@BeforeEach
	void wireCollaborators() {
		// @Mapper(uses = ...) collaborators are Spring-injected; Mappers.getMapper bypasses
		// Spring, so wire the generated impl's sub-mapper fields with real leaf instances.
		ReflectionTestUtils.setField(this.tweetMapper, "mentionMapper", Mappers.getMapper(MentionMapper.class));
		ReflectionTestUtils.setField(this.tweetMapper, "hashtagMapper", Mappers.getMapper(HashtagMapper.class));
	}

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
	void testMapEntityToDtoWithUserDto() {
		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setId(UUID.randomUUID());
		tweetEntity.setContent("Tweet Content");
		tweetEntity.setCreatedAt(LocalDateTime.now());
		UserDto userDto = new UserDto(UUID.randomUUID(), "testuser", true, LocalDateTime.now());

		TweetDto tweetDto = this.tweetMapper.mapEntityToDto(tweetEntity, userDto);

		assertThat(tweetDto.getId()).isEqualTo(tweetEntity.getId());
		assertThat(tweetDto.getContent()).isEqualTo(tweetEntity.getContent());
		assertThat(tweetDto.getUser()).isEqualTo(userDto);
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
		assertThat(tweetDto.getLikesCount()).isEqualTo(likesCount);
		assertThat(tweetDto.getRepliesCount()).isEqualTo(repliesCount);
		assertThat(tweetDto.getRetweetsCount()).isEqualTo(retweetsCount);
		assertThat(tweetDto.getTopReply()).isEqualTo(topReply);
	}

	@Test
	void testMapCreationRequestToEntity() {
		TweetCreationRequest request = new TweetCreationRequest();
		request.setUserId(UUID.randomUUID());
		request.setContent("Test Content");

		TweetEntity tweetEntity = this.tweetMapper.mapCreationRequestToEntity(request);

		assertThat(tweetEntity).isNotNull();
		assertThat(tweetEntity.getUserId()).isEqualTo(request.getUserId());
		assertThat(tweetEntity.getContent()).isEqualTo(request.getContent());
		assertThat(tweetEntity.getId()).isNotNull();
		assertThat(tweetEntity.getCreatedAt()).isNotNull();
	}

	@Test
	void testMapEntityToCreationDto() {
		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setId(UUID.randomUUID());

		TweetDto tweetDto = this.tweetMapper.mapEntityToCreationDto(tweetEntity);

		assertThat(tweetDto).isNotNull();
		assertThat(tweetDto.getId()).isEqualTo(tweetEntity.getId());
	}

	@Test
	void testMapEntityToCreationDtoWithNullEntity() {
		TweetDto tweetDto = this.tweetMapper.mapEntityToCreationDto(null);

		assertThat(tweetDto).isNull();
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
		assertThat(updatedEntity.getId()).isEqualTo(tweetEntity.getId()); // Ensure ID
																			// remains
		// unchanged
	}

	@Test
	void testMapUpdateRequestToEntityWithNullContent() {
		TweetUpdateRequest request = new TweetUpdateRequest();
		request.setContent(null);

		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setContent("Old Content");

		TweetEntity updatedEntity = this.tweetMapper.mapUpdateRequestToEntity(request, tweetEntity);

		assertThat(updatedEntity).isNotNull();
		assertThat(updatedEntity.getContent()).isEqualTo("Old Content");
	}

	@Test
	void testMapEntityToDto_WithLikesRepliesRetweetsMentionsHashtags() {
		// Given
		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setId(UUID.randomUUID());
		tweetEntity.setContent("Sample Tweet");
		tweetEntity.setCreatedAt(LocalDateTime.now());

		Long likesCount = 10L;
		Long repliesCount = 5L;
		Long retweetsCount = 3L;

		ReplyDto topReply = new ReplyDto();
		topReply.setContent("Top Reply");

		HashtagEntity hashtag = new HashtagEntity(UUID.randomUUID(), "#example", true);
		MentionEntity mention = new MentionEntity(UUID.randomUUID(), UUID.randomUUID(), "@user", tweetEntity.getId(),
				true);

		List<HashtagEntity> hashtags = Collections.singletonList(hashtag);
		List<MentionEntity> mentions = Collections.singletonList(mention);

		// When
		TweetDto tweetDto = this.tweetMapper.mapEntityToDto(tweetEntity, likesCount, repliesCount, retweetsCount,
				topReply, hashtags, mentions);

		// Assert
		assertThat(tweetDto).isNotNull();
		assertThat(tweetDto.getId()).isEqualTo(tweetEntity.getId());
		assertThat(tweetDto.getContent()).isEqualTo(tweetEntity.getContent());
		assertThat(tweetDto.getLikesCount()).isEqualTo(likesCount);
		assertThat(tweetDto.getRepliesCount()).isEqualTo(repliesCount);
		assertThat(tweetDto.getRetweetsCount()).isEqualTo(retweetsCount);
		assertThat(tweetDto.getTopReply()).isEqualTo(topReply);
		assertThat(tweetDto.getHashtags()).isNotNull();
		assertThat(tweetDto.getHashtags()).hasSize(1);
		assertThat(tweetDto.getHashtags().iterator().next().getText()).isEqualTo("#example");
		assertThat(tweetDto.getMentions()).isNotNull();
		assertThat(tweetDto.getMentions()).hasSize(1);
		assertThat(tweetDto.getMentions().iterator().next().getText()).isEqualTo("@user");
	}

	@Test
	void testMapEntityToDto_NullEntityReturnsNull() {
		assertThat(this.tweetMapper.mapEntityToDto((TweetEntity) null)).isNull();
	}

	@Test
	void testMapCreationRequestToTweetEntity_NullRequestReturnsNull() throws Exception {
		java.lang.reflect.Method m = TweetMapper.class.getDeclaredMethod("mapCreationRequestToTweetEntity",
				TweetCreationRequest.class);
		m.setAccessible(true);
		Object result = m.invoke(this.tweetMapper, (TweetCreationRequest) null);
		assertThat(result).isNull();
	}

	@Test
	void testMapUpdateRequestToEntity_NullRequestReturnsEntityUnchanged() {
		TweetEntity entity = new TweetEntity();
		entity.setContent("kept");
		TweetEntity result = this.tweetMapper.mapUpdateRequestToEntity(null, entity);
		assertThat(result.getContent()).isEqualTo("kept");
	}

	@Test
	void testMapEntityToDtoSevenArgsTopReply_NullMentionsAndHashtags() {
		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setId(UUID.randomUUID());
		tweetEntity.setContent("c");
		tweetEntity.setCreatedAt(LocalDateTime.now());
		TweetDto dto = this.tweetMapper.mapEntityToDto(tweetEntity, 1L, 2L, 3L, new ReplyDto(), null, null);
		assertThat(dto).isNotNull();
		assertThat(dto.getMentions()).isNull();
		assertThat(dto.getHashtags()).isNull();
	}

	@Test
	void testMapEntityToDtoSevenArgsReplies_NullMentionsAndHashtags() {
		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setId(UUID.randomUUID());
		tweetEntity.setContent("c");
		tweetEntity.setCreatedAt(LocalDateTime.now());
		TweetDto dto = this.tweetMapper.mapEntityToDto(tweetEntity, 1L, 2L, 3L, Collections.<ReplyDto>emptyList(), null,
				null);
		assertThat(dto).isNotNull();
		assertThat(dto.getMentions()).isNull();
		assertThat(dto.getHashtags()).isNull();
	}

	@Test
	void testMapEntityToDto_WithLikesRepliesRetweetsMentionsHashtagsAndReplies() {
		// Given
		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setId(UUID.randomUUID());
		tweetEntity.setContent("Sample Tweet");
		tweetEntity.setCreatedAt(LocalDateTime.now());

		Long likesCount = 15L;
		Long repliesCount = 7L;
		Long retweetsCount = 4L;

		ReplyDto reply1 = new ReplyDto();
		reply1.setContent("First Reply");
		ReplyDto reply2 = new ReplyDto();
		reply2.setContent("Second Reply");
		List<ReplyDto> replies = Arrays.asList(reply1, reply2);

		MentionEntity mention = new MentionEntity(UUID.randomUUID(), UUID.randomUUID(), "@mentionedUser",
				tweetEntity.getId(), true);
		HashtagEntity hashtag = new HashtagEntity(UUID.randomUUID(), "#hashtag", true);

		List<MentionEntity> mentions = Collections.singletonList(mention);
		List<HashtagEntity> hashtags = Collections.singletonList(hashtag);

		// When
		TweetDto tweetDto = this.tweetMapper.mapEntityToDto(tweetEntity, likesCount, repliesCount, retweetsCount,
				replies, mentions, hashtags);

		// Assertions
		assertThat(tweetDto).isNotNull();
		assertThat(tweetDto.getId()).isEqualTo(tweetEntity.getId());
		assertThat(tweetDto.getContent()).isEqualTo(tweetEntity.getContent());
		assertThat(tweetDto.getLikesCount()).isEqualTo(likesCount);
		assertThat(tweetDto.getRepliesCount()).isEqualTo(repliesCount);
		assertThat(tweetDto.getRetweetsCount()).isEqualTo(retweetsCount);
		assertThat(tweetDto.getReplies()).isEqualTo(replies);

		assertThat(tweetDto.getMentions()).isNotNull();
		assertThat(tweetDto.getMentions()).hasSize(1);
		assertThat(tweetDto.getMentions().iterator().next().getText()).isEqualTo("@mentionedUser");

		assertThat(tweetDto.getHashtags()).isNotNull();
		assertThat(tweetDto.getHashtags()).hasSize(1);
		assertThat(tweetDto.getHashtags().iterator().next().getText()).isEqualTo("#hashtag");
	}

}
