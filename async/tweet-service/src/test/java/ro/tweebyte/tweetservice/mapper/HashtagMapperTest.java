package ro.tweebyte.tweetservice.mapper;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ro.tweebyte.tweetservice.entity.HashtagEntity;

import static org.junit.jupiter.api.Assertions.*;

class HashtagMapperTest {

    private final HashtagMapper hashtagMapper = Mappers.getMapper(HashtagMapper.class);

    @Test
    void testMapTextToEntity() {
        // Given
        String hashtagText = "example";

        // When
        HashtagEntity hashtagEntity = hashtagMapper.mapTextToEntity(hashtagText);

        // Then
        assertEquals(hashtagText, hashtagEntity.getText());
    }

    @Test
    void testMapTextToEntityWithNullText() {
        // Given
        String hashtagText = null;

        // When
        HashtagEntity hashtagEntity = hashtagMapper.mapTextToEntity(hashtagText);

        // Then
        assertNull(hashtagEntity);
    }

    @Test
    void testMapTextToEntityWithEmptyText() {
        String hashtagText = "";

        HashtagEntity hashtagEntity = hashtagMapper.mapTextToEntity(hashtagText);

        assertNotNull(hashtagEntity);
        assertTrue(hashtagEntity.getText().isEmpty());
    }

}