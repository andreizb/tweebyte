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
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

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

	public TweetEntity mapCreationRequestToEntity(TweetCreationRequest request) {
		TweetEntity tweetEntity = mapCreationRequestToTweetEntity(request);
		tweetEntity.setId(UUID.randomUUID());
		tweetEntity.setCreatedAt(LocalDateTime.now());
		return tweetEntity;
	}

	@BeanMapping(ignoreByDefault = true)
	@Mapping(source = "id", target = "id")
	public abstract TweetDto mapEntityToCreationDto(TweetEntity tweetEntity);

	@Mapping(target = "replies", ignore = true)
	@Mapping(target = "mentions", ignore = true)
	@Mapping(target = "hashtags", ignore = true)
	@Mapping(source = "tweetEntity.id", target = "id")
	@Mapping(source = "tweetEntity.content", target = "content")
	@Mapping(source = "tweetEntity.createdAt", target = "createdAt")
	@Mapping(source = "likesCount", target = "likesCount")
	@Mapping(source = "tweetEntity.userId", target = "userId")
	public abstract TweetDto mapEntityToDto(TweetEntity tweetEntity, Long likesCount, Long repliesCount,
			Long retweetsCount, ReplyDto topReply);

	@Mapping(target = "mentions", ignore = true)
	@Mapping(target = "hashtags", ignore = true)
	@Mapping(source = "tweetEntity.content", target = "content")
	public abstract TweetDto mapEntityToDto(TweetEntity tweetEntity, Long likesCount, Long repliesCount,
			Long retweetsCount, List<ReplyDto> replies);

	// Per-tweet feed enrichment payload: top reply + hashtags + mentions.
	// Mirrors reactive TweetMapper.mapEntityToDto(entity, likes, replies, retweets,
	// topReply, hashtags, mentions).
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

	// Per-tweet single-tweet enrichment payload: full replies list + hashtags + mentions.
	// Mirrors reactive TweetMapper.mapEntityToDto(entity, likes, replies, retweets,
	// repliesList, mentions, hashtags).
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

	@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
	@Mapping(target = "id", ignore = true)
	@Mapping(target = "userId", ignore = true)
	@Mapping(target = "createdAt", ignore = true)
	public abstract TweetEntity mapUpdateRequestToEntity(TweetUpdateRequest request,
			@MappingTarget TweetEntity tweetEntity);

	// Element mapping resolves to MentionMapper/HashtagMapper via the @Mapper uses clause,
	// so collaborators are injected into the generated impl rather than field-autowired here.
	protected abstract Set<MentionDto> mapMentions(List<MentionEntity> mentions);

	protected abstract Set<HashtagDto> mapHashtags(List<HashtagEntity> hashtags);

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "createdAt", ignore = true)
	protected abstract TweetEntity mapCreationRequestToTweetEntity(TweetCreationRequest request);

}
