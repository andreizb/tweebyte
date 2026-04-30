package ro.tweebyte.userservice.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserNotFoundExceptionTest {

    @Test
    void testConstructorWithMessage() {
        UserNotFoundException ex = new UserNotFoundException("not found");
        assertEquals("not found", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void testIsRuntimeException() {
        UserNotFoundException ex = new UserNotFoundException("x");
        assertTrue(ex instanceof RuntimeException);
    }

    @Test
    void testCanBeThrown() {
        try {
            throw new UserNotFoundException("user 42 missing");
        } catch (UserNotFoundException caught) {
            assertEquals("user 42 missing", caught.getMessage());
        }
    }

    @Test
    void testNullMessage() {
        UserNotFoundException ex = new UserNotFoundException(null);
        assertNull(ex.getMessage());
    }

    @Test
    void testEmptyMessage() {
        UserNotFoundException ex = new UserNotFoundException("");
        assertEquals("", ex.getMessage());
        assertNotNull(ex.getStackTrace());
    }
}
