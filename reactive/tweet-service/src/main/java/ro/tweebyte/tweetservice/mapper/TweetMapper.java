package ro.tweebyte.tweetservice.mapper;

import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.model.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public abstract class TweetMapper {

    @Autowired
    private MentionMapper mentionMapper;

    @Autowired
    private HashtagMapper hashtagMapper;

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
    @Mapping(source = "tweetEntity.id", target = "id")
    @Mapping(source = "tweetEntity.content", target = "content")
    @Mapping(source = "tweetEntity.createdAt", target = "createdAt")
    @Mapping(source = "likesCount", target = "likesCount")
    @Mapping(source = "tweetEntity.userId", target = "userId")
    public abstract TweetDto mapEntityToDto(TweetEntity tweetEntity, Long likesCount, Long repliesCount, Long retweetsCount, ReplyDto topReply);


    public TweetDto mapEntityToDto(TweetEntity tweetEntity, Long likesCount, Long repliesCount, Long retweetsCount, ReplyDto topReply, List<HashtagEntity> hashtags, List<MentionEntity> mentions) {
        TweetDto tweetDto = mapEntityToDto(tweetEntity, likesCount, repliesCount, retweetsCount, topReply);
        tweetDto.setMentions(mentions.stream().map(mentionMapper::mapEntityToDto).collect(Collectors.toSet()));
        tweetDto.setHashtags(hashtags.stream().map(hashtagMapper::mapEntityToDto).collect(Collectors.toSet()));
        return tweetDto;
    }

    public TweetDto mapEntityToDto(TweetEntity tweetEntity, Long likesCount, Long repliesCount, Long retweetsCount, List<ReplyDto> replies, List<MentionEntity> mentions, List<HashtagEntity> hashtags) {
        TweetDto tweetDto = mapEntityToDto(tweetEntity);
        tweetDto.setMentions(mentions.stream().map(mentionMapper::mapEntityToDto).collect(Collectors.toSet()));
        tweetDto.setHashtags(hashtags.stream().map(hashtagMapper::mapEntityToDto).collect(Collectors.toSet()));
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
    public abstract TweetEntity mapUpdateRequestToEntity(TweetUpdateRequest request, @MappingTarget TweetEntity tweetEntity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    protected abstract TweetEntity mapCreationRequestToTweetEntity(TweetCreationRequest request);

}
