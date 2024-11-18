package ro.tweebyte.tweetservice.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.model.MentionDto;

import java.util.UUID;

@Mapper(componentModel = "spring")
public abstract class MentionMapper {

    public MentionEntity mapFieldsToEntity(UUID userId, String text, TweetEntity tweetEntity) {
        MentionEntity mentionEntity = mapFieldsToEntity(userId, text);
        mentionEntity.setTweetId(tweetEntity.getId());
        mentionEntity.setId(UUID.randomUUID());
        mentionEntity.setInsertable(true);
        return mentionEntity;
    }

    @Mapping(source = "userId", target = "userId")
    @Mapping(source = "text", target = "text")
    @Mapping(target = "id", ignore = true)
    public abstract MentionEntity mapFieldsToEntity(UUID userId, String text);


    public abstract MentionDto mapEntityToDto(MentionEntity mentionEntity);
}
