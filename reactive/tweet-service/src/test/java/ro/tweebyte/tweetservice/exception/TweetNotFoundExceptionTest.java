package ro.tweebyte.tweetservice.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TweetNotFoundExceptionTest {

    @Test
    void testTweetNotFoundException() {
        TweetNotFoundException exception = new TweetNotFoundException("Test message");
        assertNotNull(exception);
        assertEquals("Test message", exception.getMessage());
    }
}