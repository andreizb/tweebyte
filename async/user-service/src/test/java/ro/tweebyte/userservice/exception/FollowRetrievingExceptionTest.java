package ro.tweebyte.userservice.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FollowRetrievingExceptionTest {

    @Test
    void testNoArgConstructor() {
        FollowRetrievingException ex = new FollowRetrievingException();
        assertNotNull(ex);
        assertNull(ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void testIsRuntimeException() {
        FollowRetrievingException ex = new FollowRetrievingException();
        assertTrue(ex instanceof RuntimeException);
    }

    @Test
    void testCanBeThrown() {
        try {
            throw new FollowRetrievingException();
        } catch (FollowRetrievingException caught) {
            assertNotNull(caught);
        }
    }
}
