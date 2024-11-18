package ro.tweebyte.interactionservice.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InteractionExceptionTest {

	@Test
	void testInteractionExceptionWithCause() {
		Throwable cause = new Throwable("Test cause");
		InteractionException exception = new InteractionException(cause);

		assertNotNull(exception.getCause());
		assertEquals(cause, exception.getCause());
	}

	@Test
	void testInteractionExceptionWithMessage() {
		String message = "Test message";
		InteractionException exception = new InteractionException(message);

		assertNotNull(exception.getMessage());
		assertEquals(message, exception.getMessage());
	}

	@Test
	void testInteractionExceptionWithoutMessageAndCause() {
		InteractionException interactionException = new InteractionException();
		assertNull(interactionException.getMessage());
	}

}
