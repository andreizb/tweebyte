package ro.tweebyte.tweetservice.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TweetNotFoundExceptionTest {

    @Test
    void testTweetNotFoundException() {
        TweetNotFoundException exception = new TweetNotFoundException("Test message");
        assertNotNull(exception);
        assertEquals("Test message", exception.getMessage());
    }
}