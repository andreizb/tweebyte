package ro.tweebyte.interactionservice.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RetweetUpdateRequestTest {

    @Test
    void testGetterAndSetter() {
        UUID id = UUID.randomUUID();
        UUID retweeterId = UUID.randomUUID();
        String content = "Test content";

        RetweetUpdateRequest retweetUpdateRequest = new RetweetUpdateRequest()
            .setId(id)
            .setRetweeterId(retweeterId)
            .setContent(content);

        assertEquals(id, retweetUpdateRequest.getId());
        assertEquals(retweeterId, retweetUpdateRequest.getRetweeterId());
        assertEquals(content, retweetUpdateRequest.getContent());

        // Test setters
        UUID newId = UUID.randomUUID();
        UUID newRetweeterId = UUID.randomUUID();
        String newContent = "New test content";

        retweetUpdateRequest.setId(newId);
        retweetUpdateRequest.setRetweeterId(newRetweeterId);
        retweetUpdateRequest.setContent(newContent);

        assertEquals(newId, retweetUpdateRequest.getId());
        assertEquals(newRetweeterId, retweetUpdateRequest.getRetweeterId());
        assertEquals(newContent, retweetUpdateRequest.getContent());
    }

    @Test
    void testAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        UUID retweeterId = UUID.randomUUID();
        String content = "Test content";

        RetweetUpdateRequest retweetUpdateRequest = new RetweetUpdateRequest(id, retweeterId, content);

        assertEquals(id, retweetUpdateRequest.getId());
        assertEquals(retweeterId, retweetUpdateRequest.getRetweeterId());
        assertEquals(content, retweetUpdateRequest.getContent());
    }

    @Test
    void testNoArgsConstructor() {
        RetweetUpdateRequest retweetUpdateRequest = new RetweetUpdateRequest();

        assertNotNull(retweetUpdateRequest);
    }

}