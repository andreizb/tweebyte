package ro.tweebyte.userservice.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitExceededExceptionTest {

    @Test
    void testConstructorWithMessage() {
        RateLimitExceededException ex = new RateLimitExceededException("too many requests");
        assertEquals("too many requests", ex.getMessage());
    }

    @Test
    void testIsRuntimeException() {
        RateLimitExceededException ex = new RateLimitExceededException("x");
        assertTrue(ex instanceof RuntimeException);
    }

    @Test
    void testNullMessage() {
        RateLimitExceededException ex = new RateLimitExceededException(null);
        assertNull(ex.getMessage());
    }

    @Test
    void testCanBeCaught() {
        try {
            throw new RateLimitExceededException("limit");
        } catch (RateLimitExceededException caught) {
            assertEquals("limit", caught.getMessage());
        }
    }
}
