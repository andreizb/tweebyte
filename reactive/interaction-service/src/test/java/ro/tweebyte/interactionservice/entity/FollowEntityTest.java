package ro.tweebyte.interactionservice.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R2DBC FollowEntity uses String status (no enum)
 * and a Persistable contract; we exercise the same shape on the reactive side.
 */
class FollowEntityTest {

    @Test
    void noArgsConstructorYieldsNullFields() {
        FollowEntity entity = new FollowEntity();
        assertNotNull(entity);
        assertNull(entity.getId());
        assertNull(entity.getStatus());
        assertNull(entity.getFollowerId());
        assertNull(entity.getFollowedId());
        assertNull(entity.getCreatedAt());
        assertFalse(entity.isInsertable());
    }

    @Test
    void allArgsConstructorPopulatesAllFields() {
        UUID id = UUID.randomUUID();
        UUID follower = UUID.randomUUID();
        UUID followed = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        FollowEntity entity = new FollowEntity(id, now, true, follower, followed, "ACCEPTED");
        assertEquals(id, entity.getId());
        assertEquals(now, entity.getCreatedAt());
        assertTrue(entity.isInsertable());
        assertEquals(follower, entity.getFollowerId());
        assertEquals(followed, entity.getFollowedId());
        assertEquals("ACCEPTED", entity.getStatus());
    }

    @Test
    void builderPopulatesAllFields() {
        UUID id = UUID.randomUUID();
        UUID follower = UUID.randomUUID();
        UUID followed = UUID.randomUUID();
        FollowEntity entity = FollowEntity.builder()
                .id(id)
                .followerId(follower)
                .followedId(followed)
                .status("PENDING")
                .build();
        assertEquals(id, entity.getId());
        assertEquals(follower, entity.getFollowerId());
        assertEquals(followed, entity.getFollowedId());
        assertEquals("PENDING", entity.getStatus());
    }

    @Test
    void settersUpdateState() {
        FollowEntity entity = new FollowEntity();
        UUID follower = UUID.randomUUID();
        UUID followed = UUID.randomUUID();
        entity.setFollowerId(follower);
        entity.setFollowedId(followed);
        entity.setStatus("REJECTED");
        assertEquals(follower, entity.getFollowerId());
        assertEquals(followed, entity.getFollowedId());
        assertEquals("REJECTED", entity.getStatus());
    }

    @Test
    void isNewReturnsTrueWhenInsertableTrue() {
        FollowEntity entity = new FollowEntity();
        entity.setId(UUID.randomUUID());
        entity.setInsertable(true);
        assertTrue(entity.isNew());
    }

    @Test
    void isNewReturnsTrueWhenIdNullAndInsertableFalse() {
        FollowEntity entity = new FollowEntity();
        entity.setInsertable(false);
        assertTrue(entity.isNew());
    }

    @Test
    void isNewReturnsFalseWhenIdSetAndNotInsertable() {
        FollowEntity entity = new FollowEntity();
        entity.setId(UUID.randomUUID());
        entity.setInsertable(false);
        assertFalse(entity.isNew());
    }
}
