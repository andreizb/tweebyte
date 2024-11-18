package ro.tweebyte.interactionservice.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReplyUpdateRequestTest {

    @Test
    void testGetterAndSetter() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String content = "Test content";

        ReplyUpdateRequest request = new ReplyUpdateRequest()
            .setId(id)
            .setUserId(userId)
            .setContent(content);

        assertEquals(id, request.getId());
        assertEquals(userId, request.getUserId());
        assertEquals(content, request.getContent());

        // Test setters
        UUID newId = UUID.randomUUID();
        UUID newUserId = UUID.randomUUID();
        String newContent = "New test content";

        request.setId(newId);
        request.setUserId(newUserId);
        request.setContent(newContent);

        assertEquals(newId, request.getId());
        assertEquals(newUserId, request.getUserId());
        assertEquals(newContent, request.getContent());
    }

    @Test
    void testAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String content = "Test content";

        ReplyUpdateRequest request = new ReplyUpdateRequest(id, userId, content);

        assertEquals(id, request.getId());
        assertEquals(userId, request.getUserId());
        assertEquals(content, request.getContent());
    }

    @Test
    void testNoArgsConstructor() {
        ReplyUpdateRequest request = new ReplyUpdateRequest();

        assertNotNull(request);
    }

}