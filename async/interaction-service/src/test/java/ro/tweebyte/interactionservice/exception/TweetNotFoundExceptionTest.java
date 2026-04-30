package ro.tweebyte.interactionservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.junit.jupiter.api.Assertions.*;

/** Mirrors reactive/.../exception/TweetNotFoundExceptionTest. */
class TweetNotFoundExceptionTest {

    @Test
    void constructorWithMessage() {
        TweetNotFoundException ex = new TweetNotFoundException("missing tweet");
        assertEquals("missing tweet", ex.getMessage());
    }

    @Test
    void isRuntimeException() {
        assertTrue(new TweetNotFoundException("x") instanceof RuntimeException);
    }

    @Test
    void canBeThrownAndCaught() {
        try {
            throw new TweetNotFoundException("nope");
        } catch (TweetNotFoundException caught) {
            assertEquals("nope", caught.getMessage());
        }
    }

    @Test
    void hasNotFoundResponseStatus() {
        ResponseStatus rs = TweetNotFoundException.class.getAnnotation(ResponseStatus.class);
        assertNotNull(rs);
        assertEquals("404 NOT_FOUND", rs.value().toString());
    }
}
