package ro.tweebyte.tweetservice.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UserNotFoundExceptionTest {

    @Test
    void testUserNotFoundException() {
        UserNotFoundException exception = new UserNotFoundException("Test message");
        assertNotNull(exception);
        assertEquals("Test message", exception.getMessage());
    }
}