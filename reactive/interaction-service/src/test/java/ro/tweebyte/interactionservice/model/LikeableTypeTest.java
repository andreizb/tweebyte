package ro.tweebyte.interactionservice.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LikeableTypeTest {

    @Test
    void valueOfTweet() {
        assertEquals(LikeableType.TWEET, LikeableType.valueOf("TWEET"));
    }

    @Test
    void valueOfReply() {
        assertEquals(LikeableType.REPLY, LikeableType.valueOf("REPLY"));
    }

    @Test
    void valuesContainsBoth() {
        LikeableType[] values = LikeableType.values();
        assertEquals(2, values.length);
    }

    @Test
    void fromStringResolvesTweet() {
        LikeableType t = LikeableType.TWEET.fromString("TWEET");
        assertEquals(LikeableType.TWEET, t);
    }

    @Test
    void fromStringResolvesReply() {
        LikeableType t = LikeableType.TWEET.fromString("REPLY");
        assertEquals(LikeableType.REPLY, t);
    }

    @Test
    void fromStringThrowsForUnknown() {
        assertThrows(RuntimeException.class, () -> LikeableType.TWEET.fromString("UNKNOWN"));
    }

    @Test
    void nameMatchesEnumConstant() {
        assertEquals("TWEET", LikeableType.TWEET.name());
        assertEquals("REPLY", LikeableType.REPLY.name());
    }

    @Test
    void valuesArrayNotNull() {
        assertNotNull(LikeableType.values());
    }
}
