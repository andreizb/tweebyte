package ro.tweebyte.interactionservice.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RetweetCreateRequestTest {

    @Test
    void testGetterAndSetter() {
        UUID originalTweetId = UUID.randomUUID();
        UUID retweeterId = UUID.randomUUID();
        String content = "Test content";

        RetweetCreateRequest request = new RetweetCreateRequest()
            .setOriginalTweetId(originalTweetId)
            .setRetweeterId(retweeterId)
            .setContent(content);

        assertEquals(originalTweetId, request.getOriginalTweetId());
        assertEquals(retweeterId, request.getRetweeterId());
        assertEquals(content, request.getContent());

        // Test setters
        UUID newOriginalTweetId = UUID.randomUUID();
        UUID newRetweeterId = UUID.randomUUID();
        String newContent = "New test content";

        request.setOriginalTweetId(newOriginalTweetId);
        request.setRetweeterId(newRetweeterId);
        request.setContent(newContent);

        assertEquals(newOriginalTweetId, request.getOriginalTweetId());
        assertEquals(newRetweeterId, request.getRetweeterId());
        assertEquals(newContent, request.getContent());
    }

    @Test
    void testAllArgsConstructor() {
        UUID originalTweetId = UUID.randomUUID();
        UUID retweeterId = UUID.randomUUID();
        String content = "Test content";

        RetweetCreateRequest request = new RetweetCreateRequest(originalTweetId, retweeterId, content);

        assertEquals(originalTweetId, request.getOriginalTweetId());
        assertEquals(retweeterId, request.getRetweeterId());
        assertEquals(content, request.getContent());
    }

    @Test
    void testNoArgsConstructor() {
        RetweetCreateRequest request = new RetweetCreateRequest();

        assertNotNull(request);
    }
}