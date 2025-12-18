package ro.tweebyte.interactionservice.mapper;

import org.springframework.stereotype.Component;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.model.ReplyCreateRequest;
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.ReplyUpdateRequest;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class ReplyMapper {

    public ReplyEntity mapRequestToEntity(ReplyCreateRequest request) {
        ReplyEntity replyEntity = mapCreationRequestToEntity(request);
        replyEntity.setId(UUID.randomUUID());
        replyEntity.setCreatedAt(LocalDateTime.now());
        return replyEntity;
    }

    public ReplyDto mapEntityToCreationDto(ReplyEntity entity) {
        if (entity == null) {
            return null;
        }
        ReplyDto dto = new ReplyDto();
        dto.setId(entity.getId());
        return dto;
    }

    public ReplyDto mapEntityToDto(ReplyEntity entity, String userName) {
        if (entity == null) {
            return null;
        }
        ReplyDto dto = new ReplyDto();
        dto.setId(entity.getId());
        dto.setContent(entity.getContent());
        dto.setUserId(entity.getUserId());
        dto.setUserName(userName);
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    public void mapRequestToEntity(ReplyUpdateRequest request, ReplyEntity entity) {
        if (request == null || entity == null) {
            return;
        }
        if (request.getContent() != null) {
            entity.setContent(request.getContent());
        }
        if (request.getUserId() != null) {
            entity.setUserId(request.getUserId());
        }
    }

    protected ReplyEntity mapCreationRequestToEntity(ReplyCreateRequest request) {
        if (request == null) {
            return null;
        }
        ReplyEntity entity = new ReplyEntity();
        entity.setContent(request.getContent());
        entity.setUserId(request.getUserId());
        entity.setTweetId(request.getTweetId());
        return entity;
    }

}
