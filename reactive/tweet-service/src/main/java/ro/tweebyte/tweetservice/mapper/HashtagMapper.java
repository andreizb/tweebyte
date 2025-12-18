package ro.tweebyte.tweetservice.mapper;

import org.springframework.stereotype.Component;
import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.model.HashtagDto;

import java.util.UUID;

@Component
public class HashtagMapper {

    public HashtagEntity mapTextToEntity(String text) {
        HashtagEntity hashtagEntity = new HashtagEntity();
        hashtagEntity.setText(text);
        hashtagEntity.setId(UUID.randomUUID());
        hashtagEntity.setInsertable(true);
        return hashtagEntity;
    }

    public HashtagDto mapEntityToDto(HashtagEntity hashtagEntity) {
        if (hashtagEntity == null) {
            return null;
        }
        return HashtagDto.builder()
                .id(hashtagEntity.getId())
                .text(hashtagEntity.getText())
                .build();
    }
}
