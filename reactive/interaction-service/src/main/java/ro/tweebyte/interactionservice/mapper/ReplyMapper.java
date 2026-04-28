package ro.tweebyte.interactionservice.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.model.ReplyCreateRequest;
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.ReplyUpdateRequest;

import java.time.LocalDateTime;
import java.util.UUID;

@Mapper(componentModel = "spring")
public abstract class ReplyMapper {

    public ReplyEntity mapRequestToEntity(ReplyCreateRequest request) {
        ReplyEntity replyEntity = mapCreationRequestToEntity(request);
        replyEntity.setId(UUID.randomUUID());
        replyEntity.setCreatedAt(LocalDateTime.now());
        replyEntity.setInsertable(true);
        return replyEntity;
    }

    @BeanMapping(ignoreByDefault = true)
    @Mapping(source = "id", target = "id")
    public abstract ReplyDto mapEntityToCreationDto(ReplyEntity entity);

    public abstract ReplyDto mapEntityToDto(ReplyEntity entity, String userName);

    public abstract void mapRequestToEntity(ReplyUpdateRequest request, @MappingTarget ReplyEntity entity);

    protected abstract ReplyEntity mapCreationRequestToEntity(ReplyCreateRequest request);

}
