package ro.tweebyte.interactionservice.exception;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ClientExceptionTest {

	@Test
	void testClientExceptionWithResponse() {
		HttpResponse<String> mockResponse = mock(HttpResponse.class);
		ClientException exception = new ClientException(mockResponse);

		assertNotNull(exception.getResponse());
		assertEquals(mockResponse, exception.getResponse());
	}
}
