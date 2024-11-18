package ro.tweebyte.tweetservice.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MentionDtoTest {

    @Test
    void testGetId() {
        UUID id = UUID.randomUUID();
        MentionDto mentionDto = new MentionDto();
        mentionDto.setId(id);
        assertEquals(id, mentionDto.getId());
    }

    @Test
    void testGetUserId() {
        UUID userId = UUID.randomUUID();
        MentionDto mentionDto = new MentionDto();
        mentionDto.setUserId(userId);
        assertEquals(userId, mentionDto.getUserId());
    }

    @Test
    void testGetText() {
        String text = "example";
        MentionDto mentionDto = new MentionDto();
        mentionDto.setText(text);
        assertEquals(text, mentionDto.getText());
    }

    @Test
    void testSetId() {
        UUID id = UUID.randomUUID();
        MentionDto mentionDto = new MentionDto();
        mentionDto.setId(id);
        assertEquals(id, mentionDto.getId());
    }

    @Test
    void testSetUserId() {
        UUID userId = UUID.randomUUID();
        MentionDto mentionDto = new MentionDto();
        mentionDto.setUserId(userId);
        assertEquals(userId, mentionDto.getUserId());
    }

    @Test
    void testSetText() {
        String text = "example";
        MentionDto mentionDto = new MentionDto();
        mentionDto.setText(text);
        assertEquals(text, mentionDto.getText());
    }

}