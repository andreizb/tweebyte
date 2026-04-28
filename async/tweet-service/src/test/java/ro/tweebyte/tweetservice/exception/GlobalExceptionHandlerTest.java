package ro.tweebyte.tweetservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

	@InjectMocks
	private GlobalExceptionHandler globalExceptionHandler;

	@Mock
	private HttpServletRequest request;

	@BeforeEach
	public void setUp() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	void handleValidationErrors_shouldReturnBadRequest() {
		var fieldError = new FieldError("objectName", "fieldName", "Validation error message");
		var bindingResult = mock(org.springframework.validation.BindingResult.class);
		when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

		var exception = mock(MethodArgumentNotValidException.class);
		when(exception.getBindingResult()).thenReturn(bindingResult);
		when(request.getRequestURI()).thenReturn("/test");

		ResponseEntity<Map<String, Object>> response = globalExceptionHandler.handleValidationErrors(exception, request);

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertEquals(400, response.getBody().get("status"));
		assertEquals("/test", response.getBody().get("path"));
		assertEquals("Validation error message", ((List<?>) response.getBody().get("errors")).get(0));
	}

	@Test
	void handleBindException_shouldReturnBadRequest() {
		var fieldError = new FieldError("objectName", "fieldName", "Bind error message");
		var bindingResult = mock(org.springframework.validation.BindingResult.class);
		when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

		var exception = mock(BindException.class);
		when(exception.getBindingResult()).thenReturn(bindingResult);
		when(request.getRequestURI()).thenReturn("/test");

		ResponseEntity<Map<String, Object>> response = globalExceptionHandler.handleBindException(exception, request);

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertEquals(400, response.getBody().get("status"));
		assertEquals("/test", response.getBody().get("path"));
		assertEquals("Bind error message", ((List<?>) response.getBody().get("errors")).get(0));
	}

	@Test
	void handleException_shouldReturnInternalServerError() {
		var exception = new RuntimeException("Unexpected error");
		when(request.getRequestURI()).thenReturn("/test");

		ResponseEntity<Map<String, Object>> response = globalExceptionHandler.handleException(exception, request);

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
		assertEquals(500, response.getBody().get("status"));
		assertEquals("/test", response.getBody().get("path"));
		assertEquals("Unexpected error", ((List<?>) response.getBody().get("errors")).get(0));
	}

}