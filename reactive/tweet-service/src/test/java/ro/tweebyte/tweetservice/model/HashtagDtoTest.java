package ro.tweebyte.tweetservice.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HashtagDtoTest {

    @Test
    void testGetId() {
        UUID id = UUID.randomUUID();
        HashtagDto hashtagDto = new HashtagDto();
        hashtagDto.setId(id);
        assertEquals(id, hashtagDto.getId());
    }

    @Test
    void testGetText() {
        String text = "example";
        HashtagDto hashtagDto = new HashtagDto();
        hashtagDto.setText(text);
        assertEquals(text, hashtagDto.getText());
    }

    @Test
    void testGetCount() {
        Long count = 5L;
        HashtagDto hashtagDto = new HashtagDto();
        hashtagDto.setCount(count);
        assertEquals(count, hashtagDto.getCount());
    }

    @Test
    void testSetId() {
        UUID id = UUID.randomUUID();
        HashtagDto hashtagDto = new HashtagDto();
        hashtagDto.setId(id);
        assertEquals(id, hashtagDto.getId());
    }

    @Test
    void testSetText() {
        String text = "example";
        HashtagDto hashtagDto = new HashtagDto();
        hashtagDto.setText(text);
        assertEquals(text, hashtagDto.getText());
    }

    @Test
    void testSetCount() {
        Long count = 5L;
        HashtagDto hashtagDto = new HashtagDto();
        hashtagDto.setCount(count);
        assertEquals(count, hashtagDto.getCount());
    }

    @Test
    void testAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        String text = "example";
        Long count = 5L;

        HashtagDto hashtagDto = new HashtagDto(id, text, count);

        assertEquals(id, hashtagDto.getId());
        assertEquals(text, hashtagDto.getText());
        assertEquals(count, hashtagDto.getCount());
    }

    @Test
    void testBuilder() {
        UUID id = UUID.randomUUID();
        String text = "example";
        Long count = 5L;

        HashtagDto hashtagDto = HashtagDto.builder()
            .id(id)
            .text(text)
            .count(count)
            .build();

        assertEquals(id, hashtagDto.getId());
        assertEquals(text, hashtagDto.getText());
        assertEquals(count, hashtagDto.getCount());
    }
}
