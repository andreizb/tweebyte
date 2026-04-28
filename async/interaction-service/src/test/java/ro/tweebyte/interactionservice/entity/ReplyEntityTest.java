package ro.tweebyte.interactionservice.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReplyEntityTest {

    @Test
    void testConstructorAndGetters() {
        // Given
        UUID tweetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String content = "This is a reply content.";

        // When
        ReplyEntity replyEntity = new ReplyEntity(tweetId, userId, content);

        // Then
        assertNotNull(replyEntity);
        assertEquals(tweetId, replyEntity.getTweetId());
        assertEquals(userId, replyEntity.getUserId());
        assertEquals(content, replyEntity.getContent());
    }

    @Test
    void testSetters() {
        // Given
        ReplyEntity replyEntity = new ReplyEntity();

        // When
        LocalDateTime createdAt = LocalDateTime.now();
        UUID id = UUID.randomUUID();
        UUID tweetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String content = "This is a reply content.";

        replyEntity.setId(id);
        replyEntity.setCreatedAt(createdAt);
        replyEntity.setTweetId(tweetId);
        replyEntity.setUserId(userId);
        replyEntity.setContent(content);

        // Then
        assertNotNull(replyEntity);
        assertEquals(id, replyEntity.getId());
        assertEquals(createdAt, replyEntity.getCreatedAt());
        assertEquals(tweetId, replyEntity.getTweetId());
        assertEquals(userId, replyEntity.getUserId());
        assertEquals(content, replyEntity.getContent());
    }

    @Test
    void testBuilder() {
        // Given
        UUID tweetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String content = "This is a reply content.";

        // When
        ReplyEntity replyEntity = ReplyEntity.builder()
            .tweetId(tweetId)
            .userId(userId)
            .content(content)
            .build();

        // Then
        assertNotNull(replyEntity);
        assertEquals(tweetId, replyEntity.getTweetId());
        assertEquals(userId, replyEntity.getUserId());
        assertEquals(content, replyEntity.getContent());
    }

}