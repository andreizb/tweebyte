package ro.tweebyte.userservice.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UserAlreadyExistsExceptionTest {

	@Test
	void testUserAlreadyExistsExceptionMessage() {
		String errorMessage = "User already exists";
		UserAlreadyExistsException exception = new UserAlreadyExistsException(errorMessage);

		assertEquals(errorMessage, exception.getMessage());
	}
}