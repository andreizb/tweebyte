package ro.tweebyte.tweetservice.mapper;

import org.mapstruct.Mapper;
import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.model.HashtagDto;

import java.util.UUID;

@Mapper(componentModel = "spring")
public abstract class HashtagMapper {

    public HashtagEntity mapTextToEntity(String text) {
        HashtagEntity hashtagEntity = new HashtagEntity();
        hashtagEntity.setText(text);
        hashtagEntity.setId(UUID.randomUUID());
        hashtagEntity.setInsertable(true);
        return hashtagEntity;
    };

    public abstract HashtagDto mapEntityToDto(HashtagEntity hashtagEntity);
}
