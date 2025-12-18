package ro.tweebyte.tweetservice.mapper;

import org.springframework.stereotype.Component;
import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.model.MentionDto;

import java.util.UUID;

@Component
public class MentionMapper {

    public MentionEntity mapFieldsToEntity(UUID userId, String text, TweetEntity tweetEntity) {
        MentionEntity mentionEntity = mapFieldsToEntity(userId, text);
        mentionEntity.setTweetId(tweetEntity.getId());
        mentionEntity.setId(UUID.randomUUID());
        mentionEntity.setInsertable(true);
        return mentionEntity;
    }

    public MentionEntity mapFieldsToEntity(UUID userId, String text) {
        return MentionEntity.builder()
                .userId(userId)
                .text(text)
                .build();
    }

    public MentionDto mapEntityToDto(MentionEntity mentionEntity) {
        if (mentionEntity == null) {
            return null;
        }
        return MentionDto.builder()
                .id(mentionEntity.getId())
                .userId(mentionEntity.getUserId())
                .text(mentionEntity.getText())
                .build();
    }
}
