package ro.tweebyte.interactionservice.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReplyCreateRequestTest {

    @Test
    void testGetterAndSetter() {
        UUID tweetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String content = "Test content";

        ReplyCreateRequest replyCreateRequest = new ReplyCreateRequest()
            .setTweetId(tweetId)
            .setUserId(userId)
            .setContent(content);

        assertEquals(tweetId, replyCreateRequest.getTweetId());
        assertEquals(userId, replyCreateRequest.getUserId());
        assertEquals(content, replyCreateRequest.getContent());

        // Test setters
        UUID newTweetId = UUID.randomUUID();
        UUID newUserId = UUID.randomUUID();
        String newContent = "New test content";

        replyCreateRequest.setTweetId(newTweetId);
        replyCreateRequest.setUserId(newUserId);
        replyCreateRequest.setContent(newContent);

        assertEquals(newTweetId, replyCreateRequest.getTweetId());
        assertEquals(newUserId, replyCreateRequest.getUserId());
        assertEquals(newContent, replyCreateRequest.getContent());
    }

    @Test
    void testAllArgsConstructor() {
        UUID tweetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String content = "Test content";

        ReplyCreateRequest replyCreateRequest = new ReplyCreateRequest(tweetId, userId, content);

        assertEquals(tweetId, replyCreateRequest.getTweetId());
        assertEquals(userId, replyCreateRequest.getUserId());
        assertEquals(content, replyCreateRequest.getContent());
    }

    @Test
    void testNoArgsConstructor() {
        ReplyCreateRequest replyCreateRequest = new ReplyCreateRequest();

        assertNotNull(replyCreateRequest);
    }
}