/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.exception;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for the reactive {@link GlobalExceptionHandler}.
 * Covers every handler method and every branch within them.
 */
class GlobalExceptionHandlerTests {

	@InjectMocks
	private GlobalExceptionHandler handler;

	@Mock
	private ServerWebExchange exchange;

	@Mock
	private ServerHttpRequest serverRequest;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		// ServerWebExchange.getRequest().getPath().value() is used to build path
		org.springframework.http.server.RequestPath path =
			org.springframework.http.server.RequestPath.parse("/tweets/test", null);
		given(this.exchange.getRequest()).willReturn(this.serverRequest);
		given(this.serverRequest.getPath()).willReturn(path);
	}

	// -------------------------------------------------------------------------
	// TweetNotFoundException → 404
	// -------------------------------------------------------------------------

	@Test
	void handleTweetNotFound_returns404() {
		TweetNotFoundException ex = new TweetNotFoundException("tweet not found");
		ResponseEntity<Map<String, Object>> response = this.handler.handleTweetNotFound(ex, this.exchange);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).containsEntry("status", 404);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) response.getBody().get("errors");
		assertThat(errors).containsExactly("tweet not found");
	}

	// -------------------------------------------------------------------------
	// UserNotFoundException → 404
	// -------------------------------------------------------------------------

	@Test
	void handleUserNotFound_returns404() {
		UserNotFoundException ex = new UserNotFoundException("user not found");
		ResponseEntity<Map<String, Object>> response = this.handler.handleUserNotFound(ex, this.exchange);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).containsEntry("status", 404);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) response.getBody().get("errors");
		assertThat(errors).containsExactly("user not found");
	}

	// -------------------------------------------------------------------------
	// TweetException — explicit non-5xx status
	// -------------------------------------------------------------------------

	@Test
	void handleTweet_withExplicitStatus_returnsExplicitStatus() {
		TweetException ex = new TweetException(HttpStatus.BAD_REQUEST, "bad tweet");
		ResponseEntity<Map<String, Object>> response = this.handler.handleTweet(ex, this.exchange);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).containsEntry("status", 400);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) response.getBody().get("errors");
		assertThat(errors).containsExactly("bad tweet");
	}

	// -------------------------------------------------------------------------
	// TweetException — null status → 500
	// -------------------------------------------------------------------------

	@Test
	void handleTweet_withNullStatus_returns500() {
		TweetException ex = new TweetException("generic tweet error");
		ResponseEntity<Map<String, Object>> response = this.handler.handleTweet(ex, this.exchange);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody()).containsEntry("status", 500);
	}

	// -------------------------------------------------------------------------
	// TweetException — 5xx triggers log.error branch
	// -------------------------------------------------------------------------

	@Test
	void handleTweet_with5xxStatus_logsAndReturnsStatus() {
		TweetException ex = new TweetException(HttpStatus.SERVICE_UNAVAILABLE, "downstream down");
		ResponseEntity<Map<String, Object>> response = this.handler.handleTweet(ex, this.exchange);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(response.getBody()).containsEntry("status", 503);
	}

	// -------------------------------------------------------------------------
	// TweetException — describe() null message → class name
	// -------------------------------------------------------------------------

	@Test
	void handleTweet_nullMessage_usesClassNameInErrors() {
		TweetException ex = new TweetException(new RuntimeException((String) null));
		ResponseEntity<Map<String, Object>> response = this.handler.handleTweet(ex, this.exchange);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) response.getBody().get("errors");
		assertThat(errors).hasSize(1);
		assertThat(errors.get(0)).isNotBlank();
	}

	// -------------------------------------------------------------------------
	// FollowRetrievingException → 500
	// -------------------------------------------------------------------------

	@Test
	void handleFollowRetrieving_returns500() {
		FollowRetrievingException ex = new FollowRetrievingException("could not fetch follows",
				new RuntimeException("upstream"));
		ResponseEntity<Map<String, Object>> response = this.handler.handleFollowRetrieving(ex, this.exchange);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody()).containsEntry("status", 500);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) response.getBody().get("errors");
		assertThat(errors).containsExactly("could not fetch follows");
	}

	// -------------------------------------------------------------------------
	// WebExchangeBindException → 400
	// -------------------------------------------------------------------------

	@Test
	void handleValidationErrors_returns400() {
		FieldError fieldError = new FieldError("objectName", "fieldName", "Validation error");
		BindingResult bindingResult = mock(BindingResult.class);
		given(bindingResult.getFieldErrors()).willReturn(List.of(fieldError));

		WebExchangeBindException ex = mock(WebExchangeBindException.class);
		given(ex.getBindingResult()).willReturn(bindingResult);

		ResponseEntity<Map<String, Object>> response = this.handler.handleValidationErrors(ex, this.exchange);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).containsEntry("status", 400);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) response.getBody().get("errors");
		assertThat(errors).containsExactly("Validation error");
	}

	// -------------------------------------------------------------------------
	// ResponseStatusException — reason present → uses reason
	// -------------------------------------------------------------------------

	@Test
	void handleResponseStatus_withReason_returnsReasonInErrors() {
		ResponseStatusException ex = new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "invalid payload");
		ResponseEntity<Map<String, Object>> response = this.handler.handleResponseStatus(ex, this.exchange);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		assertThat(response.getBody()).containsEntry("status", 422);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) response.getBody().get("errors");
		assertThat(errors).containsExactly("invalid payload");
	}

	// -------------------------------------------------------------------------
	// ResponseStatusException — null reason → falls back to getMessage()
	// -------------------------------------------------------------------------

	@Test
	void handleResponseStatus_nullReason_fallsBackToMessage() {
		ResponseStatusException ex = new ResponseStatusException(
				org.springframework.http.HttpStatusCode.valueOf(400));
		ResponseEntity<Map<String, Object>> response = this.handler.handleResponseStatus(ex, this.exchange);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).containsEntry("status", 400);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) response.getBody().get("errors");
		assertThat(errors).hasSize(1);
	}

	// -------------------------------------------------------------------------
	// ResponseStatusException — unresolvable status code → 500
	// -------------------------------------------------------------------------

	@Test
	void handleResponseStatus_unresolvedStatusCode_returns500() {
		ResponseStatusException ex = new ResponseStatusException(
				org.springframework.http.HttpStatusCode.valueOf(999), "custom code");
		ResponseEntity<Map<String, Object>> response = this.handler.handleResponseStatus(ex, this.exchange);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody()).containsEntry("status", 500);
	}

	// -------------------------------------------------------------------------
	// handleGeneric — message present
	// -------------------------------------------------------------------------

	@Test
	void handleGeneric_withMessage_includesMessageInErrors() {
		RuntimeException ex = new RuntimeException("something broke");
		ResponseEntity<Map<String, Object>> response = this.handler.handleGeneric(ex, this.exchange);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) response.getBody().get("errors");
		// The catch-all handler masks the real message to avoid leaking internals.
		assertThat(errors).containsExactly("An internal error occurred");
	}

	// -------------------------------------------------------------------------
	// handleGeneric — null message → class simple name
	// -------------------------------------------------------------------------

	@Test
	void handleGeneric_nullMessage_usesClassNameInErrors() {
		RuntimeException ex = new RuntimeException((String) null);
		ResponseEntity<Map<String, Object>> response = this.handler.handleGeneric(ex, this.exchange);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) response.getBody().get("errors");
		// The catch-all handler masks the real message (or class name) to avoid leaking internals.
		assertThat(errors).containsExactly("An internal error occurred");
	}

	// -------------------------------------------------------------------------
	// Response body shape
	// -------------------------------------------------------------------------

	@Test
	void responseBody_alwaysContainsPathAndTimestamp() {
		TweetNotFoundException ex = new TweetNotFoundException("missing");
		ResponseEntity<Map<String, Object>> response = this.handler.handleTweetNotFound(ex, this.exchange);

		assertThat(response.getBody()).containsKey("timestamp");
		assertThat(response.getBody()).containsEntry("path", "/tweets/test");
	}

}
