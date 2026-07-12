/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.exception;

import java.util.List;
import java.util.Map;

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
	void handleValidationErrors_shouldReturnBadRequest() {
		var fieldError = new FieldError("objectName", "fieldName", "Validation error message");
		var bindingResult = mock(org.springframework.validation.BindingResult.class);
		given(bindingResult.getFieldErrors()).willReturn(List.of(fieldError));

		var exception = mock(MethodArgumentNotValidException.class);
		given(exception.getBindingResult()).willReturn(bindingResult);
		given(this.request.getRequestURI()).willReturn("/test");

		ResponseEntity<Map<String, Object>> response = this.globalExceptionHandler.handleValidationErrors(exception,
				this.request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).containsEntry("status", 400).containsEntry("path", "/test");
		assertThat(((List<?>) response.getBody().get("errors")).get(0)).isEqualTo("Validation error message");
	}

	@Test
	void handleBindException_shouldReturnBadRequest() {
		var fieldError = new FieldError("objectName", "fieldName", "Bind error message");
		var bindingResult = mock(org.springframework.validation.BindingResult.class);
		given(bindingResult.getFieldErrors()).willReturn(List.of(fieldError));

		var exception = mock(BindException.class);
		given(exception.getBindingResult()).willReturn(bindingResult);
		given(this.request.getRequestURI()).willReturn("/test");

		ResponseEntity<Map<String, Object>> response = this.globalExceptionHandler.handleBindException(exception,
				this.request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).containsEntry("status", 400).containsEntry("path", "/test");
		assertThat(((List<?>) response.getBody().get("errors")).get(0)).isEqualTo("Bind error message");
	}

	@Test
	void handleGeneric_shouldReturnInternalServerError() {
		var exception = new RuntimeException("Unexpected error");
		given(this.request.getRequestURI()).willReturn("/test");

		ResponseEntity<Map<String, Object>> response = this.globalExceptionHandler.handleGeneric(exception,
				this.request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody()).containsEntry("status", 500).containsEntry("path", "/test");
		// The catch-all handler masks the real message to avoid leaking internals.
		assertThat(((List<?>) response.getBody().get("errors")).get(0)).isEqualTo("An internal error occurred");
	}

}
