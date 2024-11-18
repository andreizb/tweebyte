package ro.tweebyte.tweetservice.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

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
}
