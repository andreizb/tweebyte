package ro.tweebyte.tweetservice.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TweetExceptionTest {

    @Test
    void testTweetExceptionWithCause() {
        Exception cause = new Exception("Test cause");
        TweetException exception = new TweetException(cause);
        assertNotNull(exception);
        assertEquals("Test cause", exception.getCause().getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testTweetExceptionWithMessage() {
        TweetException exception = new TweetException("Test message");
        assertNotNull(exception);
        assertEquals("Test message", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testTweetExceptionRequiredArgsConstructor() {
        TweetException exception = new TweetException();
        assertNotNull(exception);
    }

}