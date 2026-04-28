package ro.tweebyte.interactionservice.mapper;

import org.springframework.stereotype.Component;
import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.model.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class RetweetMapper {

    public RetweetEntity mapRequestToEntity(RetweetCreateRequest request) {
        RetweetEntity retweetEntity = mapCreationRequestToEntity(request);
        retweetEntity.setId(UUID.randomUUID());
        retweetEntity.setCreatedAt(LocalDateTime.now());
        return retweetEntity;
    }

    public RetweetDto mapEntityToDto(RetweetEntity entity) {
        if (entity == null) {
            return null;
        }
        RetweetDto dto = new RetweetDto();
        dto.setId(entity.getId());
        dto.setContent(entity.getContent());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    public RetweetDto mapEntityToDto(RetweetEntity entity, UserDto user) {
        RetweetDto dto = mapEntityToDto(entity);
        if (dto != null) {
            dto.setUser(user);
        }
        return dto;
    }

    public RetweetDto mapEntityToDto(RetweetEntity entity, UserDto user, TweetDto tweet) {
        RetweetDto dto = mapEntityToDto(entity, user);
        if (dto != null) {
            dto.setTweet(tweet);
        }
        return dto;
    }

    public void mapRequestToEntity(RetweetUpdateRequest request, RetweetEntity entity) {
        if (request == null || entity == null) {
            return;
        }
        if (request.getContent() != null) {
            entity.setContent(request.getContent());
        }
        if (request.getRetweeterId() != null) {
            entity.setRetweeterId(request.getRetweeterId());
        }
    }

    protected RetweetEntity mapCreationRequestToEntity(RetweetCreateRequest request) {
        if (request == null) {
            return null;
        }
        RetweetEntity entity = new RetweetEntity();
        entity.setContent(request.getContent());
        entity.setRetweeterId(request.getRetweeterId());
        entity.setOriginalTweetId(request.getOriginalTweetId());
        return entity;
    }

}
