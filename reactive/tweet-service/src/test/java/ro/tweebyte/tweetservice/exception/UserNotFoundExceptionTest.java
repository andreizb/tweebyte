package ro.tweebyte.tweetservice.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class UserNotFoundExceptionTest {

    @Test
    void testUserNotFoundException() {
        UserNotFoundException exception = new UserNotFoundException("Test message");
        assertNotNull(exception);
        assertEquals("Test message", exception.getMessage());
    }
}