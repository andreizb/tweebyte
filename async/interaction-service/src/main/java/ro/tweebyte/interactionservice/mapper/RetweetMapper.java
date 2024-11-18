package ro.tweebyte.interactionservice.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.model.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Mapper(componentModel = "spring")
public abstract class RetweetMapper {

    public RetweetEntity mapRequestToEntity(RetweetCreateRequest request) {
        RetweetEntity retweetEntity = mapCreationRequestToEntity(request);
        retweetEntity.setId(UUID.randomUUID());
        retweetEntity.setCreatedAt(LocalDateTime.now());
        return retweetEntity;
    }

    public abstract RetweetDto mapEntityToDto(RetweetEntity entity);

    public RetweetDto mapEntityToDto(RetweetEntity entity, UserDto user) {
        RetweetDto dto = mapEntityToDto(entity);
        dto.setUser(user);
        return dto;
    }

    public RetweetDto mapEntityToDto(RetweetEntity entity, UserDto user, TweetDto tweet) {
        RetweetDto dto = mapEntityToDto(entity, user);
        dto.setTweet(tweet);
        return dto;
    }

    public abstract void mapRequestToEntity(RetweetUpdateRequest request, @MappingTarget RetweetEntity entity);

    protected abstract RetweetEntity mapCreationRequestToEntity(RetweetCreateRequest request);

}
