package ro.tweebyte.interactionservice.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RetweetEntityTest {

    @Test
    void testConstructorAndGetters() {
        // Given
        UUID originalTweetId = UUID.randomUUID();
        UUID retweeterId = UUID.randomUUID();
        String content = "This is a retweet content.";

        // When
        RetweetEntity retweetEntity = new RetweetEntity(originalTweetId, retweeterId, content);

        // Then
        assertNotNull(retweetEntity);
        assertEquals(originalTweetId, retweetEntity.getOriginalTweetId());
        assertEquals(retweeterId, retweetEntity.getRetweeterId());
        assertEquals(content, retweetEntity.getContent());
    }

    @Test
    void testSetters() {
        // Given
        RetweetEntity retweetEntity = new RetweetEntity();

        // When
        LocalDateTime createdAt = LocalDateTime.now();
        UUID id = UUID.randomUUID();
        UUID originalTweetId = UUID.randomUUID();
        UUID retweeterId = UUID.randomUUID();
        String content = "This is a retweet content.";

        retweetEntity.setId(id);
        retweetEntity.setCreatedAt(createdAt);
        retweetEntity.setOriginalTweetId(originalTweetId);
        retweetEntity.setRetweeterId(retweeterId);
        retweetEntity.setContent(content);

        // Then
        assertNotNull(retweetEntity);
        assertEquals(id, retweetEntity.getId());
        assertEquals(createdAt, retweetEntity.getCreatedAt());
        assertEquals(originalTweetId, retweetEntity.getOriginalTweetId());
        assertEquals(retweeterId, retweetEntity.getRetweeterId());
        assertEquals(content, retweetEntity.getContent());
    }

    @Test
    void testBuilder() {
        // Given
        UUID originalTweetId = UUID.randomUUID();
        UUID retweeterId = UUID.randomUUID();
        String content = "This is a retweet content.";

        // When
        RetweetEntity retweetEntity = RetweetEntity.builder()
            .originalTweetId(originalTweetId)
            .retweeterId(retweeterId)
            .content(content)
            .build();

        // Then
        assertNotNull(retweetEntity);
        assertEquals(originalTweetId, retweetEntity.getOriginalTweetId());
        assertEquals(retweeterId, retweetEntity.getRetweeterId());
        assertEquals(content, retweetEntity.getContent());
    }

}