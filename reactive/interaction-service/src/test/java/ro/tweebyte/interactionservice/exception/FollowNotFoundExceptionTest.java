package ro.tweebyte.interactionservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FollowNotFoundExceptionTest {

    @Test
    void testConstructorWithMessage() {
        FollowNotFoundException ex = new FollowNotFoundException("not found");
        assertEquals("not found", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void testIsRuntimeException() {
        FollowNotFoundException ex = new FollowNotFoundException("x");
        assertTrue(ex instanceof RuntimeException);
    }

    @Test
    void testCanBeThrown() {
        try {
            throw new FollowNotFoundException("missing follow");
        } catch (FollowNotFoundException caught) {
            assertEquals("missing follow", caught.getMessage());
        }
    }

    @Test
    void testNullMessage() {
        FollowNotFoundException ex = new FollowNotFoundException(null);
        assertNull(ex.getMessage());
    }

    @Test
    void testHasResponseStatusAnnotation() {
        ResponseStatus rs = FollowNotFoundException.class.getAnnotation(ResponseStatus.class);
        assertNotNull(rs);
        assertEquals("404 NOT_FOUND", rs.value().toString());
    }
}
