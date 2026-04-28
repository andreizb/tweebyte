package ro.tweebyte.interactionservice.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TweetNotFoundExceptionTest {

	@Test
	void testTweetNotFoundExceptionWithMessage() {
		String message = "Tweet not found";
		TweetNotFoundException exception = new TweetNotFoundException(message);

		assertNotNull(exception.getMessage());
		assertEquals(message, exception.getMessage());
	}
}
