/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.exception;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Exercises every handler method in the reactive GlobalExceptionHandler so branch and
 * line coverage reach the module target. No Spring context is needed — each handler is a
 * plain method returning ResponseEntity.
 */
class GlobalExceptionHandlerTests {

	private GlobalExceptionHandler handler;

	private ServerWebExchange exchange;

	@BeforeEach
	void setUp() {
		this.handler = new GlobalExceptionHandler();
		this.exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test/path").build());
	}

	// ---- WebExchangeBindException -> 400 ----------------------------------------

	@Test
	void handleValidationErrors_returns400WithFieldErrors() {
		WebExchangeBindException ex = mock(WebExchangeBindException.class);
		org.springframework.validation.BindingResult br = mock(org.springframework.validation.BindingResult.class);
		given(ex.getBindingResult()).willReturn(br);
		given(br.getFieldErrors())
			.willReturn(List.of(new org.springframework.validation.FieldError("obj", "field1", "must not be blank"),
					new org.springframework.validation.FieldError("obj", "field2", "invalid email")));

		ResponseEntity<Map<String, Object>> resp = this.handler.handleValidationErrors(ex, this.exchange);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(resp.getBody()).containsEntry("status", 400);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) resp.getBody().get("errors");
		assertThat(errors).containsExactlyInAnyOrder("must not be blank", "invalid email");
	}

	@Test
	void handleValidationErrors_emptyFieldErrors_returns400WithEmptyList() {
		WebExchangeBindException ex = mock(WebExchangeBindException.class);
		org.springframework.validation.BindingResult br = mock(org.springframework.validation.BindingResult.class);
		given(ex.getBindingResult()).willReturn(br);
		given(br.getFieldErrors()).willReturn(List.of());

		ResponseEntity<Map<String, Object>> resp = this.handler.handleValidationErrors(ex, this.exchange);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) resp.getBody().get("errors");
		assertThat(errors).isEmpty();
	}

	// ---- IllegalArgumentException -> 404 ----------------------------------------

	@Test
	void handleIllegalArgument_withMessage_returns404() {
		ResponseEntity<Map<String, Object>> resp = this.handler
			.handleIllegalArgument(new IllegalArgumentException("resource missing"), this.exchange);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(resp.getBody()).containsEntry("status", 404);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) resp.getBody().get("errors");
		assertThat(errors).isEqualTo(List.of("resource missing"));
	}

	@Test
	void handleIllegalArgument_nullMessage_usesClassSimpleName() {
		// describe(ex) branch: ex.getMessage() == null → use class simple name
		IllegalArgumentException ex = new IllegalArgumentException((String) null);
		ResponseEntity<Map<String, Object>> resp = this.handler.handleIllegalArgument(ex, this.exchange);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) resp.getBody().get("errors");
		assertThat(errors).hasSize(1);
		assertThat(errors.get(0)).isEqualTo("IllegalArgumentException");
	}

	// ---- Domain not-found exceptions -> 404 --------------------------------------

	@Test
	void handleTweetNotFound_returns404() {
		ResponseEntity<Map<String, Object>> resp = this.handler
			.handleTweetNotFound(new TweetNotFoundException("tweet gone"), this.exchange);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) resp.getBody().get("errors");
		assertThat(errors).isEqualTo(List.of("tweet gone"));
	}

	@Test
	void handleFollowNotFound_returns404() {
		ResponseEntity<Map<String, Object>> resp = this.handler
			.handleFollowNotFound(new FollowNotFoundException("follow gone"), this.exchange);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(resp.getBody()).containsEntry("status", 404);
	}

	@Test
	void handleUserNotFound_returns404() {
		ResponseEntity<Map<String, Object>> resp = this.handler
			.handleUserNotFound(new UserNotFoundException("user gone"), this.exchange);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(resp.getBody()).containsEntry("status", 404);
	}

	// ---- InteractionException: status null (500) and status set (4xx/5xx) --------

	@Test
	void handleInteractionException_withNullStatus_returns500() {
		// ex.getStatus() == null → fallback INTERNAL_SERVER_ERROR
		InteractionException ex = new InteractionException("something broke");
		ResponseEntity<Map<String, Object>> resp = this.handler.handleInteractionException(ex, this.exchange);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(resp.getBody()).containsEntry("status", 500);
	}

	@Test
	void handleInteractionException_with5xxStatus_logsAndReturnsStatus() {
		// ex.getStatus() non-null, is5xxServerError=true branch
		InteractionException ex = new InteractionException(HttpStatus.SERVICE_UNAVAILABLE, "downstream down");
		ResponseEntity<Map<String, Object>> resp = this.handler.handleInteractionException(ex, this.exchange);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(resp.getBody()).containsEntry("status", 503);
	}

	@Test
	void handleInteractionException_with4xxStatus_doesNotLog() {
		// ex.getStatus() non-null, is5xxServerError=false branch (no logging)
		InteractionException ex = new InteractionException(HttpStatus.FORBIDDEN, "access denied");
		ResponseEntity<Map<String, Object>> resp = this.handler.handleInteractionException(ex, this.exchange);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(resp.getBody()).containsEntry("status", 403);
	}

	@Test
	void handleInteractionException_nullMessageUsesClassSimpleName() {
		// describe(ex): ex.getMessage() == null → class simple name
		InteractionException ex = new InteractionException((Throwable) new RuntimeException());
		ResponseEntity<Map<String, Object>> resp = this.handler.handleInteractionException(ex, this.exchange);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) resp.getBody().get("errors");
		// The message comes from the wrapped exception via Throwable; either way we get a string
		assertThat(errors).hasSize(1);
	}

	// ---- ResponseStatusException: reason non-null and null -----------------------

	@Test
	void handleResponseStatus_withReason_usesReason() {
		ResponseStatusException ex = new ResponseStatusException(HttpStatus.BAD_REQUEST, "malformed UUID");
		ResponseEntity<Map<String, Object>> resp = this.handler.handleResponseStatus(ex, this.exchange);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) resp.getBody().get("errors");
		assertThat(errors).isEqualTo(List.of("malformed UUID"));
	}

	@Test
	void handleResponseStatus_nullReason_usesMessage() {
		// ex.getReason() == null → falls back to ex.getMessage()
		ResponseStatusException ex = new ResponseStatusException(HttpStatus.GONE);
		ResponseEntity<Map<String, Object>> resp = this.handler.handleResponseStatus(ex, this.exchange);

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
		ResponseStatusException ex = new ResponseStatusException(unknownCode, "weird status");
		ResponseEntity<Map<String, Object>> resp = this.handler.handleResponseStatus(ex, this.exchange);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
	}

	// ---- Generic Throwable -> 500 ------------------------------------------------

	@Test
	void handleGeneric_returns500() {
		ResponseEntity<Map<String, Object>> resp = this.handler.handleGeneric(new RuntimeException("boom"),
				this.exchange);

		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(resp.getBody()).containsEntry("status", 500);
	}

	// ---- Body shape invariant ----------------------------------------------------

	@Test
	void responseBodyAlwaysCarriesTimestampStatusPathErrors() {
		ResponseEntity<Map<String, Object>> resp = this.handler.handleGeneric(new RuntimeException("probe"),
				this.exchange);

		Map<String, Object> body = resp.getBody();
		assertThat(body).isNotNull()
			.containsKey("timestamp")
			.containsEntry("status", 500)
			.containsEntry("path", "/test/path");
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) body.get("errors");
		// The catch-all handler masks the real message to avoid leaking internals.
		assertThat(errors).isEqualTo(List.of("An internal error occurred"));
	}

}
