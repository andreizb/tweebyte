package ro.tweebyte.interactionservice.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LikeEntityTest {

    @Test
    void noArgsConstructorYieldsNullFields() {
        LikeEntity entity = new LikeEntity();
        assertNotNull(entity);
        assertNull(entity.getId());
        assertNull(entity.getUserId());
        assertNull(entity.getLikeableId());
        assertNull(entity.getLikeableType());
    }

    @Test
    void allArgsConstructor() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID likeableId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        LikeEntity entity = new LikeEntity(id, now, true, userId, likeableId, "TWEET");
        assertEquals(id, entity.getId());
        assertEquals(now, entity.getCreatedAt());
        assertTrue(entity.isInsertable());
        assertEquals(userId, entity.getUserId());
        assertEquals(likeableId, entity.getLikeableId());
        assertEquals("TWEET", entity.getLikeableType());
    }

    @Test
    void builderPopulatesFields() {
        UUID userId = UUID.randomUUID();
        UUID likeableId = UUID.randomUUID();
        LikeEntity entity = LikeEntity.builder()
                .userId(userId)
                .likeableId(likeableId)
                .likeableType("REPLY")
                .build();
        assertEquals(userId, entity.getUserId());
        assertEquals(likeableId, entity.getLikeableId());
        assertEquals("REPLY", entity.getLikeableType());
    }

    @Test
    void isNewBranchInsertableTrue() {
        LikeEntity e = new LikeEntity();
        e.setId(UUID.randomUUID());
        e.setInsertable(true);
        assertTrue(e.isNew());
    }

    @Test
    void isNewBranchIdNull() {
        LikeEntity e = new LikeEntity();
        e.setInsertable(false);
        assertTrue(e.isNew());
    }

    @Test
    void isNewBranchIdSetAndNotInsertable() {
        LikeEntity e = new LikeEntity();
        e.setId(UUID.randomUUID());
        e.setInsertable(false);
        assertFalse(e.isNew());
    }

    @Test
    void settersUpdateState() {
        LikeEntity e = new LikeEntity();
        UUID id = UUID.randomUUID();
        e.setId(id);
        e.setUserId(id);
        e.setLikeableId(id);
        e.setLikeableType("TWEET");
        assertEquals(id, e.getId());
        assertEquals(id, e.getUserId());
        assertEquals(id, e.getLikeableId());
        assertEquals("TWEET", e.getLikeableType());
    }
}
