package ro.tweebyte.tweetservice.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TweetCreationRequestTest {

    @Test
    void getId() {
        UUID id = UUID.randomUUID();
        TweetCreationRequest request = new TweetCreationRequest();
        request.setId(id);
        assertEquals(id, request.getId());
    }

    @Test
    void getUserId() {
        UUID userId = UUID.randomUUID();
        TweetCreationRequest request = new TweetCreationRequest();
        request.setUserId(userId);
        assertEquals(userId, request.getUserId());
    }

    @Test
    void getContent() {
        String content = "Test tweet content";
        TweetCreationRequest request = new TweetCreationRequest();
        request.setContent(content);
        assertEquals(content, request.getContent());
    }

    @Test
    void setId() {
        UUID id = UUID.randomUUID();
        TweetCreationRequest request = new TweetCreationRequest();
        assertNull(request.getId()); // Initially null
        request.setId(id);
        assertEquals(id, request.getId());
    }

    @Test
    void setUserId() {
        UUID userId = UUID.randomUUID();
        TweetCreationRequest request = new TweetCreationRequest();
        assertNull(request.getUserId()); // Initially null
        request.setUserId(userId);
        assertEquals(userId, request.getUserId());
    }

    @Test
    void setContent() {
        String content = "Test tweet content";
        TweetCreationRequest request = new TweetCreationRequest();
        assertNull(request.getContent()); // Initially null
        request.setContent(content);
        assertEquals(content, request.getContent());
    }

    @Test
    void testAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String content = "This is a test tweet.";

        TweetCreationRequest request = new TweetCreationRequest(id, userId, content);

        assertEquals(id, request.getId());
        assertEquals(userId, request.getUserId());
        assertEquals(content, request.getContent());
    }

    @Test
    void testBuilder() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String content = "This is a test tweet.";

        TweetCreationRequest request = TweetCreationRequest.builder()
            .id(id)
            .userId(userId)
            .content(content)
            .build();

        assertEquals(id, request.getId());
        assertEquals(userId, request.getUserId());
        assertEquals(content, request.getContent());
    }

}