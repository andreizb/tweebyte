package ro.tweebyte.interactionservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.junit.jupiter.api.Assertions.*;

/** Mirrors reactive/.../exception/FollowNotFoundExceptionTest. */
class FollowNotFoundExceptionTest {

    @Test
    void constructorWithMessage() {
        FollowNotFoundException ex = new FollowNotFoundException("not found");
        assertEquals("not found", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void isRuntimeException() {
        assertTrue(new FollowNotFoundException("x") instanceof RuntimeException);
    }

    @Test
    void canBeThrownAndCaught() {
        try {
            throw new FollowNotFoundException("missing follow");
        } catch (FollowNotFoundException caught) {
            assertEquals("missing follow", caught.getMessage());
        }
    }

    @Test
    void nullMessage() {
        assertNull(new FollowNotFoundException(null).getMessage());
    }

    @Test
    void hasResponseStatusAnnotation() {
        ResponseStatus rs = FollowNotFoundException.class.getAnnotation(ResponseStatus.class);
        assertNotNull(rs);
        assertEquals("404 NOT_FOUND", rs.value().toString());
    }
}
