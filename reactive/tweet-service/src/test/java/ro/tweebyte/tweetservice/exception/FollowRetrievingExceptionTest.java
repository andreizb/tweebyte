package ro.tweebyte.tweetservice.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class FollowRetrievingExceptionTest {

    @Test
    void testFollowRetrievingException() {
        FollowRetrievingException exception = new FollowRetrievingException();
        assertNotNull(exception);
        assertNull(exception.getMessage());
    }
}