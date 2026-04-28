package ro.tweebyte.interactionservice.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LikeEntityTest {

    @Test
    void testLikeEntityConstructor() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID likeableId = UUID.randomUUID();
        LikeEntity.LikeableType likeableType = LikeEntity.LikeableType.TWEET;

        // When
        LikeEntity likeEntity = new LikeEntity(userId, likeableId, likeableType);

        // Then
        assertNotNull(likeEntity);
        assertEquals(userId, likeEntity.getUserId());
        assertEquals(likeableId, likeEntity.getLikeableId());
        assertEquals(likeableType, likeEntity.getLikeableType());
    }

    @Test
    void testLikeEntitySettersAndGetters() {
        // Given
        LikeEntity likeEntity = new LikeEntity();
        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();
        UUID userId = UUID.randomUUID();
        UUID likeableId = UUID.randomUUID();
        LikeEntity.LikeableType likeableType = LikeEntity.LikeableType.REPLY;

        // When
        likeEntity.setId(id);
        likeEntity.setCreatedAt(createdAt);
        likeEntity.setUserId(userId);
        likeEntity.setLikeableId(likeableId);
        likeEntity.setLikeableType(likeableType);

        // Then
        assertEquals(id, likeEntity.getId());
        assertEquals(createdAt, likeEntity.getCreatedAt());
        assertEquals(userId, likeEntity.getUserId());
        assertEquals(likeableId, likeEntity.getLikeableId());
        assertEquals(likeableType, likeEntity.getLikeableType());
    }

}