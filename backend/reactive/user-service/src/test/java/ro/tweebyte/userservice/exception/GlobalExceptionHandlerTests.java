/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.exception;

import java.util.List;
import java.util.Map;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Exercises the reactive user-service GlobalExceptionHandler. The reactive handler
 * accepts ServerWebExchange instead of HttpServletRequest, but the assertions on body
 * shape (timestamp / status / path / errors) and HTTP status code remain identical.
 */
class GlobalExceptionHandlerTests {

	@InjectMocks
	private GlobalExceptionHandler globalExceptionHandler;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
	}

	private ServerWebExchange exchange(String path) {
		return MockServerWebExchange.from(MockServerHttpRequest.get(path));
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

		WebExchangeBindException ex = new WebExchangeBindException(parameter, bindingResult);

		ResponseEntity<Map<String, Object>> responseEntity = this.globalExceptionHandler.handleValidationErrors(ex,
				exchange("/test"));

		assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		Map<String, Object> responseBody = responseEntity.getBody();
		assertThat(responseBody.get("timestamp").getClass()).isEqualTo(String.class);
		assertThat(responseBody).containsEntry("status", HttpStatus.BAD_REQUEST.value())
			.containsEntry("path", "/test");
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) responseBody.get("errors");
		assertThat(errors).hasSize(2);
		assertThat(errors.get(0)).isEqualTo("Error Message 1");
		assertThat(errors.get(1)).isEqualTo("Error Message 2");
	}

	@Test
	void handleUserAlreadyExistsException() {
		// duplicate email/username surfaces as UserAlreadyExistsException → 400.
		String errorMessage = "User already exists";
		UserAlreadyExistsException ex = new UserAlreadyExistsException(errorMessage);

		ResponseEntity<Map<String, Object>> responseEntity = this.globalExceptionHandler.handleUserAlreadyExists(ex,
				exchange("/test"));

		assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		Map<String, Object> responseBody = responseEntity.getBody();
		assertThat(responseBody).containsEntry("status", HttpStatus.BAD_REQUEST.value())
			.containsEntry("path", "/test");
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) responseBody.get("errors");
		assertThat(errors).hasSize(1);
		assertThat(errors.get(0)).isEqualTo(errorMessage);
	}

	@Test
	void handleUserNotFoundException() {
		String errorMessage = "User not found";
		UserNotFoundException ex = new UserNotFoundException(errorMessage);

		ResponseEntity<Map<String, Object>> responseEntity = this.globalExceptionHandler.handleUserNotFound(ex,
				exchange("/test"));

		assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		Map<String, Object> responseBody = responseEntity.getBody();
		assertThat(responseBody).containsEntry("status", HttpStatus.NOT_FOUND.value())
			.containsEntry("path", "/test");
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) responseBody.get("errors");
		assertThat(errors).hasSize(1);
		assertThat(errors.get(0)).isEqualTo(errorMessage);
	}

	@Test
	void handleAuthenticationException() {
		// AuthenticationException must surface as 401.
		String errorMessage = "Authentication failed";
		AuthenticationException ex = new AuthenticationException(errorMessage);

		ResponseEntity<Map<String, Object>> responseEntity = this.globalExceptionHandler.handleAuthentication(ex,
				exchange("/test"));

		assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		Map<String, Object> responseBody = responseEntity.getBody();
		assertThat(responseBody).containsEntry("status", HttpStatus.UNAUTHORIZED.value())
			.containsEntry("path", "/test");
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) responseBody.get("errors");
		assertThat(errors).hasSize(1);
		assertThat(errors.get(0)).isEqualTo(errorMessage);
	}

	@Test
	void handleException() {
		// Reactive analogue to async's catch-all: UserException → 500 with the same
		// body shape as the rest of the handlers.
		String errorMessage = "An unexpected error occurred";
		UserException ex = new UserException(errorMessage);

		ResponseEntity<Map<String, Object>> responseEntity = this.globalExceptionHandler.handleUser(ex,
				exchange("/test"));

		assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		Map<String, Object> responseBody = responseEntity.getBody();
		assertThat(responseBody).containsEntry("status", HttpStatus.INTERNAL_SERVER_ERROR.value())
			.containsEntry("path", "/test");
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) responseBody.get("errors");
		assertThat(errors).hasSize(1);
		assertThat(errors.get(0)).isEqualTo(errorMessage);
	}

	@Test
	void handleRateLimit_returnsTooManyRequests() {
		RequestNotPermitted ex = RequestNotPermitted
			.createRequestNotPermitted(RateLimiter.ofDefaults("userServiceRateLimiter"));

		ResponseEntity<Map<String, Object>> responseEntity = this.globalExceptionHandler.handleRateLimit(ex,
				exchange("/auth/register"));

		assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(responseEntity.getBody()).containsEntry("status", HttpStatus.TOO_MANY_REQUESTS.value());
	}

}
