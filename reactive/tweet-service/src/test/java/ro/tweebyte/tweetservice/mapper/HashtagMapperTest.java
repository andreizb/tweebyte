package ro.tweebyte.tweetservice.mapper;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.model.HashtagDto;

import java.util.UUID;

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
        assertNotNull(hashtagEntity);
        assertEquals(hashtagText, hashtagEntity.getText());
    }

    @Test
    void testMapTextToEntityWithNullText() {
        // Given
        String hashtagText = null;

        // When
        HashtagEntity hashtagEntity = hashtagMapper.mapTextToEntity(hashtagText);

        // Then
        assertNotNull(hashtagEntity);
        assertNull(hashtagEntity.getText());
    }

    @Test
    void testMapTextToEntityWithEmptyText() {
        // Given
        String hashtagText = "";

        // When
        HashtagEntity hashtagEntity = hashtagMapper.mapTextToEntity(hashtagText);

        // Then
        assertNotNull(hashtagEntity);
        assertEquals("", hashtagEntity.getText());
    }

    @Test
    void testMapEntityToDto() {
        // Given
        UUID id = UUID.randomUUID();
        String text = "example";
        HashtagEntity hashtagEntity = new HashtagEntity();
        hashtagEntity.setId(id);
        hashtagEntity.setText(text);

        // When
        HashtagDto hashtagDto = hashtagMapper.mapEntityToDto(hashtagEntity);

        // Then
        assertNotNull(hashtagDto);
        assertEquals(id, hashtagDto.getId());
        assertEquals(text, hashtagDto.getText());
    }

    @Test
    void testMapEntityToDtoWithNullEntity() {
        // Given
        HashtagEntity hashtagEntity = null;

        // When
        HashtagDto hashtagDto = hashtagMapper.mapEntityToDto(hashtagEntity);

        // Then
        assertNull(hashtagDto);
    }

    @Test
    void testMapEntityToDtoWithEmptyEntity() {
        // Given
        HashtagEntity hashtagEntity = new HashtagEntity();

        // When
        HashtagDto hashtagDto = hashtagMapper.mapEntityToDto(hashtagEntity);

        // Then
        assertNotNull(hashtagDto);
        assertNull(hashtagDto.getId());
        assertNull(hashtagDto.getText());
    }
}
