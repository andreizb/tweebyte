package ro.tweebyte.interactionservice.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.model.FollowDto;

import java.time.LocalDateTime;
import java.util.UUID;

@Mapper(componentModel = "spring")
public abstract class FollowMapper {

    public FollowEntity mapRequestToEntity(UUID followerId, UUID followedId, FollowEntity.Status status) {
        FollowEntity followEntity = mapCreationRequestToEntity(followerId, followedId, status);
        followEntity.setId(UUID.randomUUID());
        followEntity.setCreatedAt(LocalDateTime.now());
        return followEntity;
    }

    public abstract FollowDto mapEntityToDto(FollowEntity followEntity);

    public abstract FollowDto mapEntityToDto(FollowEntity followEntity, String userName);

    protected abstract FollowEntity mapCreationRequestToEntity(UUID followerId, UUID followedId, FollowEntity.Status status);

}
