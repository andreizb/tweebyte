package ro.tweebyte.tweetservice.mapper;

import org.mapstruct.*;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.model.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring")
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
    @Mapping(source = "tweetEntity.id", target = "id")
    @Mapping(source = "tweetEntity.content", target = "content")
    @Mapping(source = "tweetEntity.createdAt", target = "createdAt")
    @Mapping(source = "likesCount", target = "likesCount")
    @Mapping(source = "tweetEntity.userId", target = "userId")
    public abstract TweetDto mapEntityToDto(TweetEntity tweetEntity, Long likesCount, Long repliesCount, Long retweetsCount, ReplyDto topReply);

    @Mapping(source = "tweetEntity.content", target = "content")
    public abstract TweetDto mapEntityToDto(TweetEntity tweetEntity, Long likesCount, Long repliesCount, Long retweetsCount, List<ReplyDto> replies);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    public abstract TweetEntity mapUpdateRequestToEntity(TweetUpdateRequest request, @MappingTarget TweetEntity tweetEntity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    protected abstract TweetEntity mapCreationRequestToTweetEntity(TweetCreationRequest request);

}
