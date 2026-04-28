package ro.tweebyte.interactionservice.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserNotFoundExceptionTest {

	@Test
	void testUserNotFoundExceptionWithMessage() {
		String message = "User not found";
		UserNotFoundException exception = new UserNotFoundException(message);

		assertNotNull(exception.getMessage());
		assertEquals(message, exception.getMessage());
	}
}
