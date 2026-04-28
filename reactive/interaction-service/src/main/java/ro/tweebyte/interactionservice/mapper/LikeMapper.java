package ro.tweebyte.interactionservice.mapper;

import org.mapstruct.Mapper;
import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.model.LikeDto;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.UserDto;

import java.time.LocalDateTime;
import java.util.UUID;

@Mapper(componentModel = "spring")
public abstract class LikeMapper {

    public LikeEntity mapRequestToEntity(UUID userId, UUID likeableId, String likeableType) {
        LikeEntity likeEntity = mapCreationRequestToEntity(userId, likeableId, likeableType);
        likeEntity.setId(UUID.randomUUID());
        likeEntity.setCreatedAt(LocalDateTime.now());
        likeEntity.setInsertable(true);
        return likeEntity;
    }

    public abstract LikeDto mapEntityToDto(LikeEntity entity);

    public LikeDto mapToDto(LikeEntity likeEntity, UserDto userDto) {
        LikeDto likeDto = mapEntityToDto(likeEntity);
        likeDto.setUser(userDto);
        return likeDto;
    }

    public LikeDto mapToDto(LikeEntity likeEntity, TweetDto tweetDto) {
        LikeDto likeDto = mapEntityToDto(likeEntity);
        likeDto.setTweet(tweetDto);
        return likeDto;
    }

    protected abstract LikeEntity mapCreationRequestToEntity(UUID userId, UUID likeableId, String likeableType);

}
