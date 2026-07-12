/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.model.HashtagDto;
import ro.tweebyte.tweetservice.model.MentionDto;
import ro.tweebyte.tweetservice.model.ReplyDto;
import ro.tweebyte.tweetservice.model.TweetCreationRequest;
import ro.tweebyte.tweetservice.model.TweetDto;
import ro.tweebyte.tweetservice.model.TweetUpdateRequest;
import ro.tweebyte.tweetservice.model.UserDto;

@Mapper(componentModel = "spring", uses = { HashtagMapper.class, MentionMapper.class })
public abstract class TweetMapper {

	@Mapping(target = "mentions", ignore = true)
	@Mapping(target = "hashtags", ignore = true)
	public abstract TweetDto mapEntityToDto(TweetEntity tweetEntity);

	public TweetDto mapEntityToDto(TweetEntity tweetEntity, UserDto userDto) {
		TweetDto tweetDto = mapEntityToDto(tweetEntity);
		tweetDto.setUser(userDto);
		return tweetDto;
	}

	// R2DBC Persistable: client-generated id + createdAt before save().
	public TweetEntity mapCreationRequestToEntity(TweetCreationRequest request) {
		TweetEntity tweetEntity = mapCreationRequestToTweetEntity(request);
		tweetEntity.setId(UUID.randomUUID());
		tweetEntity.setCreatedAt(LocalDateTime.now());
		return tweetEntity;
	}

	@BeanMapping(ignoreByDefault = true)
	@Mapping(source = "id", target = "id")
	public abstract TweetDto mapEntityToCreationDto(TweetEntity tweetEntity);

	public TweetDto mapEntityToDto(TweetEntity tweetEntity, Long likesCount, Long repliesCount, Long retweetsCount,
			ReplyDto topReply) {
		TweetDto tweetDto = mapEntityToDto(tweetEntity);
		tweetDto.setLikesCount(likesCount);
		tweetDto.setRepliesCount(repliesCount);
		tweetDto.setRetweetsCount(retweetsCount);
		tweetDto.setTopReply(topReply);
		return tweetDto;
	}

	public TweetDto mapEntityToDto(TweetEntity tweetEntity, Long likesCount, Long repliesCount, Long retweetsCount,
			ReplyDto topReply, List<HashtagEntity> hashtags, List<MentionEntity> mentions) {
		TweetDto tweetDto = mapEntityToDto(tweetEntity, likesCount, repliesCount, retweetsCount, topReply);
		if (mentions != null) {
			tweetDto.setMentions(mapMentions(mentions));
		}
		if (hashtags != null) {
			tweetDto.setHashtags(mapHashtags(hashtags));
		}
		return tweetDto;
	}

	public TweetDto mapEntityToDto(TweetEntity tweetEntity, Long likesCount, Long repliesCount, Long retweetsCount,
			List<ReplyDto> replies, List<MentionEntity> mentions, List<HashtagEntity> hashtags) {
		TweetDto tweetDto = mapEntityToDto(tweetEntity);
		if (mentions != null) {
			tweetDto.setMentions(mapMentions(mentions));
		}
		if (hashtags != null) {
			tweetDto.setHashtags(mapHashtags(hashtags));
		}
		tweetDto.setLikesCount(likesCount);
		tweetDto.setRepliesCount(repliesCount);
		tweetDto.setRetweetsCount(retweetsCount);
		tweetDto.setReplies(replies);
		return tweetDto;
	}

	public TweetEntity mapUpdateRequestToEntity(TweetUpdateRequest request, TweetEntity tweetEntity) {
		if (request == null) {
			return tweetEntity;
		}
		if (request.getContent() != null) {
			tweetEntity.setContent(request.getContent());
		}
		return tweetEntity;
	}

	// Element mapping resolves to MentionMapper/HashtagMapper via the @Mapper uses clause,
	// so collaborators are injected into the generated impl rather than field-autowired here.
	protected abstract Set<MentionDto> mapMentions(List<MentionEntity> mentions);

	protected abstract Set<HashtagDto> mapHashtags(List<HashtagEntity> hashtags);

	protected TweetEntity mapCreationRequestToTweetEntity(TweetCreationRequest request) {
		if (request == null) {
			return null;
		}
		TweetEntity entity = new TweetEntity();
		entity.setUserId(request.getUserId());
		entity.setContent(request.getContent());
		entity.setMediaIds(request.getMediaIds());
		return entity;
	}

}
