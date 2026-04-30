package ro.tweebyte.interactionservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.junit.jupiter.api.Assertions.*;

/** Mirrors reactive/.../exception/UserNotFoundExceptionTest. */
class UserNotFoundExceptionTest {

    @Test
    void constructorWithMessage() {
        UserNotFoundException ex = new UserNotFoundException("missing user");
        assertEquals("missing user", ex.getMessage());
    }

    @Test
    void isRuntimeException() {
        assertTrue(new UserNotFoundException("x") instanceof RuntimeException);
    }

    @Test
    void canBeThrownAndCaught() {
        try {
            throw new UserNotFoundException("absent");
        } catch (UserNotFoundException caught) {
            assertEquals("absent", caught.getMessage());
        }
    }

    @Test
    void hasNotFoundResponseStatus() {
        ResponseStatus rs = UserNotFoundException.class.getAnnotation(ResponseStatus.class);
        assertNotNull(rs);
        assertEquals("404 NOT_FOUND", rs.value().toString());
    }
}
