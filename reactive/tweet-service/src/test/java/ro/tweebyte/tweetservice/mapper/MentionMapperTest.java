package ro.tweebyte.tweetservice.mapper;

import org.junit.jupiter.api.Test;

import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MentionMapperTest {

    private final MentionMapper mentionMapper = new MentionMapper();

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
    void mapEntityToDto_NullEntityReturnsNull() {
        assertNull(mentionMapper.mapEntityToDto(null));
    }

    @Test
    void mapEntityToDto_NonNullEntityMapsAllFields() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        MentionEntity entity = MentionEntity.builder().id(id).userId(userId).text("@u").build();
        var dto = mentionMapper.mapEntityToDto(entity);
        assertEquals(id, dto.getId());
        assertEquals(userId, dto.getUserId());
        assertEquals("@u", dto.getText());
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