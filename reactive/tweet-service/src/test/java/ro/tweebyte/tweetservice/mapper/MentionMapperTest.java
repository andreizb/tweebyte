package ro.tweebyte.tweetservice.mapper;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MentionMapperTest {

    private final MentionMapper mentionMapper = Mappers.getMapper(MentionMapper.class);

    @Test
    void testMapFieldsToEntity() {
        // Given
        UUID userId = UUID.randomUUID();
        String text = "mentionText";
        TweetEntity tweetEntity = new TweetEntity();

        // When
        MentionEntity mentionEntity = mentionMapper.mapFieldsToEntity(userId, text, tweetEntity);

        // Then
        assertEquals(userId, mentionEntity.getUserId());
        assertEquals(text, mentionEntity.getText());
        assertEquals(tweetEntity.getId(), mentionEntity.getTweetId());
    }

    @Test
    void testMapFieldsToEntityWithoutTweetEntity() {
        // Given
        UUID userId = UUID.randomUUID();
        String text = "mentionText";

        // When
        MentionEntity mentionEntity = mentionMapper.mapFieldsToEntity(userId, text);

        // Then
        assertEquals(userId, mentionEntity.getUserId());
        assertEquals(text, mentionEntity.getText());
        assertNull(mentionEntity.getTweetId());
    }

}