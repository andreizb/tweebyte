package ro.tweebyte.interactionservice.mapper;

import org.springframework.stereotype.Component;
import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.model.LikeDto;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.UserDto;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class LikeMapper {

    public LikeEntity mapRequestToEntity(UUID userId, UUID likeableId, LikeEntity.LikeableType likeableType) {
        LikeEntity likeEntity = mapCreationRequestToEntity(userId, likeableId, likeableType);
        likeEntity.setId(UUID.randomUUID());
        likeEntity.setCreatedAt(LocalDateTime.now());
        return likeEntity;
    }

    public LikeDto mapEntityToDto(LikeEntity entity) {
        if (entity == null) {
            return null;
        }
        LikeDto dto = new LikeDto();
        dto.setId(entity.getId());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    public LikeDto mapToDto(LikeEntity likeEntity, UserDto userDto) {
        LikeDto likeDto = mapEntityToDto(likeEntity);
        if (likeDto != null) {
            likeDto.setUser(userDto);
        }
        return likeDto;
    }

    public LikeDto mapToDto(LikeEntity likeEntity, TweetDto tweetDto) {
        LikeDto likeDto = mapEntityToDto(likeEntity);
        if (likeDto != null) {
            likeDto.setTweet(tweetDto);
        }
        return likeDto;
    }

    protected LikeEntity mapCreationRequestToEntity(UUID userId, UUID likeableId,
            LikeEntity.LikeableType likeableType) {
        if (userId == null && likeableId == null && likeableType == null) {
            return null;
        }
        LikeEntity entity = new LikeEntity();
        entity.setUserId(userId);
        entity.setLikeableId(likeableId);
        entity.setLikeableType(likeableType);
        return entity;
    }

}
