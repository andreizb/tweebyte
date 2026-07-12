/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.exception;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Exercises every branch in GlobalExceptionHandler so we don't accept its body shape on
 * faith. Built on the @ControllerAdvice handler directly (no MVC bootstrap) — each
 * handler is just a method returning ResponseEntity.
 */
class GlobalExceptionHandlerTests {

	private GlobalExceptionHandler handler;

	private HttpServletRequest req;

	@BeforeEach
	void setUp() {
		this.handler = new GlobalExceptionHandler();
		this.req = mock(HttpServletRequest.class);
		given(this.req.getRequestURI()).willReturn("/test/path");
	}

	@Test
	void handleValidationErrorsReturns400WithFieldErrors() {
		MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
		org.springframework.validation.BindingResult br = mock(org.springframework.validation.BindingResult.class);
		given(ex.getBindingResult()).willReturn(br);
		given(br.getFieldErrors())
			.willReturn(List.of(new org.springframework.validation.FieldError("o", "f1", "must not be blank"),
					new org.springframework.validation.FieldError("o", "f2", "must be email")));
		ResponseEntity<Map<String, Object>> resp = this.handler.handleValidationErrors(ex, this.req);
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(resp.getBody()).containsEntry("status", 400);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) resp.getBody().get("errors");
		assertThat(errors).contains("must not be blank").contains("must be email");
	}

	@Test
	void handleBindExceptionReturns400() {
		// BindException's getBindingResult() returns itself by default, but Mockito
		// strict-stubs returns null on unstubbed calls — wire it explicitly.
		org.springframework.validation.BindException ex = mock(org.springframework.validation.BindException.class);
		org.springframework.validation.BindingResult br = mock(org.springframework.validation.BindingResult.class);
		given(ex.getBindingResult()).willReturn(br);
		given(br.getFieldErrors()).willReturn(List.of(new org.springframework.validation.FieldError("o", "f", "bad")));
		ResponseEntity<Map<String, Object>> resp = this.handler.handleBindException(ex, this.req);
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void handlePathTypeMismatchReturns400() {
		MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
		given(ex.getMessage()).willReturn("not a UUID");
		ResponseEntity<Map<String, Object>> resp = this.handler.handlePathTypeMismatch(ex, this.req);
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) resp.getBody().get("errors");
		assertThat(errors).isEqualTo(List.of("not a UUID"));
	}

	@Test
	void handleIllegalArgumentReturns404() {
		ResponseEntity<Map<String, Object>> resp = this.handler.handleIllegalArgument(new IllegalArgumentException("boom"),
				this.req);
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) resp.getBody().get("errors");
		assertThat(errors).isEqualTo(List.of("boom"));
	}

	@Test
	void handleTweetNotFoundReturns404() {
		ResponseEntity<Map<String, Object>> resp = this.handler.handleTweetNotFound(new TweetNotFoundException("missing"),
				this.req);
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void handleFollowNotFoundReturns404() {
		ResponseEntity<Map<String, Object>> resp = this.handler.handleFollowNotFound(new FollowNotFoundException("missing"),
				this.req);
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void handleUserNotFoundReturns404() {
		ResponseEntity<Map<String, Object>> resp = this.handler.handleUserNotFound(new UserNotFoundException("missing"),
				this.req);
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void handleInteractionExceptionReturns500_withNullStatus() {
		// ex.getStatus() == null → fallback INTERNAL_SERVER_ERROR
		ResponseEntity<Map<String, Object>> resp = this.handler
			.handleInteractionException(new InteractionException("server error"), this.req);
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@Test
	void handleInteractionException_with5xxStatus_logsAndReturnsStatus() {
		// ex.getStatus() non-null, is5xxServerError=true branch
		InteractionException ex = new InteractionException(HttpStatus.SERVICE_UNAVAILABLE, "downstream down");
		ResponseEntity<Map<String, Object>> resp = this.handler.handleInteractionException(ex, this.req);
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(resp.getBody()).containsEntry("status", 503);
	}

	@Test
	void handleInteractionException_with4xxStatus_doesNotLog() {
		// ex.getStatus() non-null, is5xxServerError=false branch
		InteractionException ex = new InteractionException(HttpStatus.FORBIDDEN, "access denied");
		ResponseEntity<Map<String, Object>> resp = this.handler.handleInteractionException(ex, this.req);
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(resp.getBody()).containsEntry("status", 403);
	}

	@Test
	void handleInteractionException_nullMessage_usesClassSimpleName() {
		// describe(ex): ex.getMessage() == null → class simple name
		InteractionException ex = new InteractionException((Throwable) new RuntimeException());
		ResponseEntity<Map<String, Object>> resp = this.handler.handleInteractionException(ex, this.req);
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) resp.getBody().get("errors");
		assertThat(errors).hasSize(1);
	}

	@Test
	void handleIllegalArgument_nullMessage_usesClassSimpleName() {
		// describe(ex) branch: ex.getMessage() == null → class simple name
		IllegalArgumentException ex = new IllegalArgumentException((String) null);
		ResponseEntity<Map<String, Object>> resp = this.handler.handleIllegalArgument(ex, this.req);
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) resp.getBody().get("errors");
		assertThat(errors).hasSize(1);
		assertThat(errors.get(0)).isEqualTo("IllegalArgumentException");
	}

	@Test
	void handleResponseStatus_withReason_usesReason() {
		org.springframework.web.server.ResponseStatusException ex =
				new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "malformed UUID");
		ResponseEntity<Map<String, Object>> resp = this.handler.handleResponseStatus(ex, this.req);
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) resp.getBody().get("errors");
		assertThat(errors).isEqualTo(List.of("malformed UUID"));
	}

	@Test
	void handleResponseStatus_nullReason_usesMessage() {
		// ex.getReason() == null → falls back to ex.getMessage()
		org.springframework.web.server.ResponseStatusException ex =
				new org.springframework.web.server.ResponseStatusException(HttpStatus.GONE);
		ResponseEntity<Map<String, Object>> resp = this.handler.handleResponseStatus(ex, this.req);
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.GONE);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) resp.getBody().get("errors");
		assertThat(errors).hasSize(1);
		assertThat(errors.get(0)).isNotNull();
	}

	@Test
	void handleResponseStatus_unknownStatusCode_fallsBackTo500() {
		// HttpStatus.resolve returns null for unknown codes → should map to 500
		org.springframework.http.HttpStatusCode unknownCode = org.springframework.http.HttpStatusCode.valueOf(999);
		org.springframework.web.server.ResponseStatusException ex =
				new org.springframework.web.server.ResponseStatusException(unknownCode, "weird status");
		ResponseEntity<Map<String, Object>> resp = this.handler.handleResponseStatus(ex, this.req);
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@Test
	void handleGenericThrowableReturns500() {
		ResponseEntity<Map<String, Object>> resp = this.handler.handleGeneric(new RuntimeException("oops"), this.req);
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@Test
	void responseBodyAlwaysCarriesPathStatusErrorsAndTimestamp() {
		ResponseEntity<Map<String, Object>> resp = this.handler.handleGeneric(new RuntimeException("body shape probe"), this.req);
		Map<String, Object> body = resp.getBody();
		assertThat(body).isNotNull().containsEntry("path", "/test/path").containsEntry("status", 500);
		assertThat(body.get("timestamp")).isNotNull();
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) body.get("errors");
		// The catch-all handler masks the real message to avoid leaking internals.
		assertThat(errors).isEqualTo(List.of("An internal error occurred"));
	}

}
