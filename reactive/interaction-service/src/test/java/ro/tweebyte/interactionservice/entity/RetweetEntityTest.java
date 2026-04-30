package ro.tweebyte.interactionservice.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetweetEntityTest {

    @Test
    void noArgsConstructorYieldsNullFields() {
        RetweetEntity entity = new RetweetEntity();
        assertNotNull(entity);
        assertNull(entity.getId());
        assertNull(entity.getOriginalTweetId());
        assertNull(entity.getRetweeterId());
        assertNull(entity.getContent());
    }

    @Test
    void allArgsConstructor() {
        UUID id = UUID.randomUUID();
        UUID original = UUID.randomUUID();
        UUID retweeter = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        RetweetEntity entity = new RetweetEntity(id, now, original, retweeter, "rt", true);
        assertEquals(id, entity.getId());
        assertEquals(now, entity.getCreatedAt());
        assertEquals(original, entity.getOriginalTweetId());
        assertEquals(retweeter, entity.getRetweeterId());
        assertEquals("rt", entity.getContent());
        assertTrue(entity.isInsertable());
    }

    @Test
    void builderPopulatesFields() {
        UUID original = UUID.randomUUID();
        RetweetEntity entity = RetweetEntity.builder()
                .originalTweetId(original)
                .content("hello")
                .build();
        assertEquals(original, entity.getOriginalTweetId());
        assertEquals("hello", entity.getContent());
    }

    @Test
    void isNewBranchInsertableTrue() {
        RetweetEntity e = new RetweetEntity();
        e.setId(UUID.randomUUID());
        e.setInsertable(true);
        assertTrue(e.isNew());
    }

    @Test
    void isNewBranchIdNull() {
        RetweetEntity e = new RetweetEntity();
        assertTrue(e.isNew());
    }

    @Test
    void isNewBranchIdSetAndNotInsertable() {
        RetweetEntity e = new RetweetEntity();
        e.setId(UUID.randomUUID());
        e.setInsertable(false);
        assertFalse(e.isNew());
    }

    @Test
    void settersUpdateState() {
        RetweetEntity e = new RetweetEntity();
        UUID id = UUID.randomUUID();
        e.setOriginalTweetId(id);
        e.setRetweeterId(id);
        e.setContent("c");
        assertEquals(id, e.getOriginalTweetId());
        assertEquals(id, e.getRetweeterId());
        assertEquals("c", e.getContent());
    }
}
