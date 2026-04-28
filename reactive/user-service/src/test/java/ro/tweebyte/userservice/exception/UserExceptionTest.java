package ro.tweebyte.userservice.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    void testRequiredArgsConstructor() {
        UserException userException = new UserException();

        assertNotNull(userException);
    }

}