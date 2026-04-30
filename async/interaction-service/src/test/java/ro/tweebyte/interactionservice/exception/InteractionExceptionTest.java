package ro.tweebyte.interactionservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.junit.jupiter.api.Assertions.*;

/** Mirrors reactive/.../exception/InteractionExceptionTest. */
class InteractionExceptionTest {

    @Test
    void withCause() {
        Throwable cause = new RuntimeException("Root");
        InteractionException ex = new InteractionException((Exception) cause);
        assertEquals(cause, ex.getCause());
    }

    @Test
    void withMessage() {
        InteractionException ex = new InteractionException("explicit message");
        assertEquals("explicit message", ex.getMessage());
    }

    @Test
    void hasInternalServerErrorResponseStatus() {
        ResponseStatus rs = InteractionException.class.getAnnotation(ResponseStatus.class);
        assertNotNull(rs);
        assertEquals("500 INTERNAL_SERVER_ERROR", rs.value().toString());
    }
}
