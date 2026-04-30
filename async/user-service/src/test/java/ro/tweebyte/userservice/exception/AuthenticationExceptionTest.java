package ro.tweebyte.userservice.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationExceptionTest {

    @Test
    void testConstructorWithMessage() {
        AuthenticationException ex = new AuthenticationException("bad creds");
        assertEquals("bad creds", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void testIsRuntimeException() {
        AuthenticationException ex = new AuthenticationException("x");
        assertTrue(ex instanceof RuntimeException);
    }

    @Test
    void testCanBeThrownAndCaught() {
        try {
            throw new AuthenticationException("login failed");
        } catch (AuthenticationException caught) {
            assertEquals("login failed", caught.getMessage());
        }
    }

    @Test
    void testNullMessageAllowed() {
        AuthenticationException ex = new AuthenticationException(null);
        assertNull(ex.getMessage());
    }

    @Test
    void testStackTraceIsNotNull() {
        AuthenticationException ex = new AuthenticationException("hi");
        assertNotNull(ex.getStackTrace());
    }
}
