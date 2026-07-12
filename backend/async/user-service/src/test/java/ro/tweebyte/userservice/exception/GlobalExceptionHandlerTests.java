/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.exception;

import java.util.List;
import java.util.Map;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTests {

	@InjectMocks
	private GlobalExceptionHandler globalExceptionHandler;

	@Mock
	private HttpServletRequest request;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	void handleValidationErrors() {
		FieldError fieldError1 = new FieldError("objectName", "fieldName1", "Error Message 1");
		FieldError fieldError2 = new FieldError("objectName", "fieldName2", "Error Message 2");

		BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "objectName");
		bindingResult.addError(fieldError1);
		bindingResult.addError(fieldError2);

		MethodParameter parameter = mock(MethodParameter.class);
		given(parameter.getParameterName()).willReturn("parameterName");

		MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

		given(this.request.getRequestURI()).willReturn("/test");

		ResponseEntity<Map<String, Object>> responseEntity = this.globalExceptionHandler.handleValidationErrors(ex,
				this.request);

		assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		Map<String, Object> responseBody = responseEntity.getBody();
		assertThat(responseBody.get("timestamp").getClass()).isEqualTo(String.class);
		assertThat(responseBody).containsEntry("status", HttpStatus.BAD_REQUEST.value()).containsEntry("path", "/test");
		List<String> errors = (List<String>) responseBody.get("errors");
		assertThat(errors).hasSize(2);
		assertThat(errors.get(0)).isEqualTo("Error Message 1");
		assertThat(errors.get(1)).isEqualTo("Error Message 2");
	}

	@Test
	void handleBindException() {
		FieldError fieldError1 = new FieldError("objectName", "fieldName1", "Error Message 1");
		FieldError fieldError2 = new FieldError("objectName", "fieldName2", "Error Message 2");

		BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "objectName");
		bindingResult.addError(fieldError1);
		bindingResult.addError(fieldError2);

		MethodParameter parameter = mock(MethodParameter.class);
		given(parameter.getParameterName()).willReturn("parameterName");

		MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

		given(this.request.getRequestURI()).willReturn("/test");

		ResponseEntity<Map<String, Object>> responseEntity = this.globalExceptionHandler.handleBindException(ex,
				this.request);

		assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		Map<String, Object> responseBody = responseEntity.getBody();
		assertThat(responseBody.get("timestamp").getClass()).isEqualTo(String.class);
		assertThat(responseBody).containsEntry("status", HttpStatus.BAD_REQUEST.value()).containsEntry("path", "/test");
		List<String> errors = (List<String>) responseBody.get("errors");
		assertThat(errors).hasSize(2);
		assertThat(errors.get(0)).isEqualTo("Error Message 1");
		assertThat(errors.get(1)).isEqualTo("Error Message 2");
	}

	@Test
	void handleUserAlreadyExistsException() {
		String errorMessage = "User already exists";
		UserAlreadyExistsException ex = new UserAlreadyExistsException(errorMessage);

		given(this.request.getRequestURI()).willReturn("/test");

		ResponseEntity<Map<String, Object>> responseEntity = this.globalExceptionHandler.handleUserAlreadyExists(ex,
				this.request);

		assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		Map<String, Object> responseBody = responseEntity.getBody();
		assertThat(responseBody).containsEntry("status", HttpStatus.BAD_REQUEST.value()).containsEntry("path", "/test");
		List<String> errors = (List<String>) responseBody.get("errors");
		assertThat(errors).hasSize(1);
		assertThat(errors.get(0)).isEqualTo(errorMessage);
	}

	@Test
	void handleUserNotFoundException() {
		String errorMessage = "User not found";
		UserNotFoundException ex = new UserNotFoundException(errorMessage);

		given(this.request.getRequestURI()).willReturn("/test");

		ResponseEntity<Map<String, Object>> responseEntity = this.globalExceptionHandler.handleUserNotFound(ex,
				this.request);

		assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		Map<String, Object> responseBody = responseEntity.getBody();
		assertThat(responseBody).containsEntry("status", HttpStatus.NOT_FOUND.value()).containsEntry("path", "/test");
		List<String> errors = (List<String>) responseBody.get("errors");
		assertThat(errors).hasSize(1);
		assertThat(errors.get(0)).isEqualTo(errorMessage);
	}

	@Test
	void handleAuthenticationException() {
		String errorMessage = "Authentication failed";
		AuthenticationException ex = new AuthenticationException(errorMessage);

		given(this.request.getRequestURI()).willReturn("/test");

		ResponseEntity<Map<String, Object>> responseEntity = this.globalExceptionHandler
			.handleAuthenticationException(ex, this.request);

		assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		Map<String, Object> responseBody = responseEntity.getBody();
		assertThat(responseBody).containsEntry("status", HttpStatus.UNAUTHORIZED.value()).containsEntry("path", "/test");
		List<String> errors = (List<String>) responseBody.get("errors");
		assertThat(errors).hasSize(1);
		assertThat(errors.get(0)).isEqualTo(errorMessage);
	}

	@Test
	void handleException() {
		Throwable ex = new RuntimeException("An unexpected error occurred");

		given(this.request.getRequestURI()).willReturn("/test");

		ResponseEntity<Map<String, Object>> responseEntity = this.globalExceptionHandler.handleException(ex,
				this.request);

		assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		Map<String, Object> responseBody = responseEntity.getBody();
		assertThat(responseBody).containsEntry("status", HttpStatus.INTERNAL_SERVER_ERROR.value())
			.containsEntry("path", "/test");
		List<String> errors = (List<String>) responseBody.get("errors");
		assertThat(errors).hasSize(1);
		// The catch-all handler masks the real message to avoid leaking internals.
		assertThat(errors.get(0)).isEqualTo("An internal error occurred");
	}

	@Test
	void handleRateLimit_returnsTooManyRequests() {
		RequestNotPermitted ex = RequestNotPermitted
			.createRequestNotPermitted(RateLimiter.ofDefaults("userServiceRateLimiter"));

		ResponseEntity<Map<String, Object>> responseEntity = this.globalExceptionHandler.handleRateLimit(ex,
				this.request);

		assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(responseEntity.getBody()).containsEntry("status", HttpStatus.TOO_MANY_REQUESTS.value());
	}

}
