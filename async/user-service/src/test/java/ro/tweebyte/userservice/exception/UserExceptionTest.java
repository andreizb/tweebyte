package ro.tweebyte.userservice.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserExceptionTest {

    @Test
    void testConstructorWithCause() {
        Exception cause = new RuntimeException("Test cause");

        UserException userException = new UserException(cause);

        assertNotNull(userException);
        assertEquals(cause, userException.getCause());
    }

    @Test
    void testConstructorWithMessage() {
        String message = "Test message";

        UserException userException = new UserException(message);

        assertNotNull(userException);
        assertEquals(message, userException.getMessage());
    }

    @Test
    void testExceptionConstructorWithMessage() {
        String message = "User not found";

        UserException exception = new UserException(message);

        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
    }

    @Test
    void testExceptionConstructorWithCause() {
        Exception cause = new Exception("Database error");

        UserException exception = new UserException(cause);

        assertNotNull(exception);
        assertEquals(cause, exception.getCause());
    }

}