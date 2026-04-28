package ro.tweebyte.interactionservice.mapper;

import org.springframework.stereotype.Component;
import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.model.FollowDto;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class FollowMapper {

    public FollowEntity mapRequestToEntity(UUID followerId, UUID followedId, FollowEntity.Status status) {
        FollowEntity followEntity = mapCreationRequestToEntity(followerId, followedId, status);
        followEntity.setId(UUID.randomUUID());
        followEntity.setCreatedAt(LocalDateTime.now());
        return followEntity;
    }

    public FollowDto mapEntityToDto(FollowEntity followEntity) {
        if (followEntity == null) {
            return null;
        }

        FollowDto followDto = new FollowDto();
        followDto.setId(followEntity.getId());
        followDto.setFollowerId(followEntity.getFollowerId());
        followDto.setFollowedId(followEntity.getFollowedId());
        followDto.setCreatedAt(followEntity.getCreatedAt());
        followDto.setStatus(mapStatus(followEntity.getStatus()));

        return followDto;
    }

    public FollowDto mapEntityToDto(FollowEntity followEntity, String userName) {
        if (followEntity == null && userName == null) {
            return null;
        }
        FollowDto dto = mapEntityToDto(followEntity);
        if (dto == null) {
            dto = new FollowDto();
        }
        dto.setUserName(userName);
        return dto;
    }

    protected FollowEntity mapCreationRequestToEntity(UUID followerId, UUID followedId, FollowEntity.Status status) {
        FollowEntity followEntity = new FollowEntity();
        followEntity.setFollowerId(followerId);
        followEntity.setFollowedId(followedId);
        followEntity.setStatus(status);
        return followEntity;
    }

    private FollowDto.Status mapStatus(FollowEntity.Status status) {
        if (status == null) {
            return null;
        }
        return FollowDto.Status.valueOf(status.name());
    }
}
