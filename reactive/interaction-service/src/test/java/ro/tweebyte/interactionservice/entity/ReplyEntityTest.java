package ro.tweebyte.interactionservice.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplyEntityTest {

    @Test
    void noArgsConstructorYieldsNullFields() {
        ReplyEntity entity = new ReplyEntity();
        assertNotNull(entity);
        assertNull(entity.getId());
        assertNull(entity.getTweetId());
        assertNull(entity.getUserId());
        assertNull(entity.getContent());
    }

    @Test
    void allArgsConstructor() {
        UUID id = UUID.randomUUID();
        UUID tweetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        ReplyEntity entity = new ReplyEntity(id, now, tweetId, userId, "hello", true);
        assertEquals(id, entity.getId());
        assertEquals(now, entity.getCreatedAt());
        assertEquals(tweetId, entity.getTweetId());
        assertEquals(userId, entity.getUserId());
        assertEquals("hello", entity.getContent());
        assertTrue(entity.isInsertable());
    }

    @Test
    void builderPopulatesFields() {
        UUID id = UUID.randomUUID();
        ReplyEntity entity = ReplyEntity.builder()
                .id(id)
                .content("text")
                .build();
        assertEquals(id, entity.getId());
        assertEquals("text", entity.getContent());
    }

    @Test
    void isNewBranchInsertableTrue() {
        ReplyEntity e = new ReplyEntity();
        e.setId(UUID.randomUUID());
        e.setInsertable(true);
        assertTrue(e.isNew());
    }

    @Test
    void isNewBranchIdNull() {
        ReplyEntity e = new ReplyEntity();
        assertTrue(e.isNew());
    }

    @Test
    void isNewBranchIdSetAndNotInsertable() {
        ReplyEntity e = new ReplyEntity();
        e.setId(UUID.randomUUID());
        e.setInsertable(false);
        assertFalse(e.isNew());
    }

    @Test
    void settersUpdateState() {
        ReplyEntity e = new ReplyEntity();
        UUID id = UUID.randomUUID();
        e.setTweetId(id);
        e.setUserId(id);
        e.setContent("c");
        assertEquals(id, e.getTweetId());
        assertEquals(id, e.getUserId());
        assertEquals("c", e.getContent());
    }
}
