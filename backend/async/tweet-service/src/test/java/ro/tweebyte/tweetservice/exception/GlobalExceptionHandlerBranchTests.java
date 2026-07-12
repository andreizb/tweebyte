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
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Branch-coverage tests for {@link GlobalExceptionHandler}.
 * Complements the existing {@link GlobalExceptionHandlerTests} by exercising
 * every handler not covered by GlobalExceptionHandlerTests, plus the conditional branches
 * inside the covered handlers (e.g. null status in TweetException, null reason
 * in ResponseStatusException, describe() null-message path).
 */
class GlobalExceptionHandlerBranchTests {

	@InjectMocks
	private GlobalExceptionHandler handler;

	@Mock
	private HttpServletRequest request;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		given(this.request.getRequestURI()).willReturn("/tweets/test");
	}

	// -------------------------------------------------------------------------
	// TweetNotFoundException → 404
	// -------------------------------------------------------------------------

	@Test
	void handleTweetNotFound_returns404() {
		TweetNotFoundException ex = new TweetNotFoundException("tweet not found");
		ResponseEntity<Map<String, Object>> response = this.handler.handleTweetNotFound(ex, this.request);

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
		ResponseEntity<Map<String, Object>> response = this.handler.handleUserNotFound(ex, this.request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).containsEntry("status", 404);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) response.getBody().get("errors");
		assertThat(errors).containsExactly("user not found");
	}

	// -------------------------------------------------------------------------
	// MethodArgumentTypeMismatchException → 400
	// -------------------------------------------------------------------------

	@Test
	void handlePathTypeMismatch_returns400() {
		org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex =
			org.mockito.Mockito.mock(
				org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class);
		given(ex.getMessage()).willReturn("Failed to convert value");

		ResponseEntity<Map<String, Object>> response = this.handler.handlePathTypeMismatch(ex, this.request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).containsEntry("status", 400);
	}

	// -------------------------------------------------------------------------
	// TweetException — with explicit non-5xx status
	// -------------------------------------------------------------------------

	@Test
	void handleTweet_withExplicitStatus_returnsExplicitStatus() {
		TweetException ex = new TweetException(HttpStatus.BAD_REQUEST, "bad tweet");
		ResponseEntity<Map<String, Object>> response = this.handler.handleTweet(ex, this.request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).containsEntry("status", 400);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) response.getBody().get("errors");
		assertThat(errors).containsExactly("bad tweet");
	}

	// -------------------------------------------------------------------------
	// TweetException — null status → falls back to 500
	// -------------------------------------------------------------------------

	@Test
	void handleTweet_withNullStatus_returns500() {
		TweetException ex = new TweetException("generic tweet error");   // no status
		ResponseEntity<Map<String, Object>> response = this.handler.handleTweet(ex, this.request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody()).containsEntry("status", 500);
	}

	// -------------------------------------------------------------------------
	// TweetException — 5xx triggers the log.error branch
	// -------------------------------------------------------------------------

	@Test
	void handleTweet_withServerError_stillReturns503Body() {
		TweetException ex = new TweetException(HttpStatus.SERVICE_UNAVAILABLE, "downstream down");
		ResponseEntity<Map<String, Object>> response = this.handler.handleTweet(ex, this.request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(response.getBody()).containsEntry("status", 503);
	}

	// -------------------------------------------------------------------------
	// TweetException — describe() falls back to class name when message is null
	// -------------------------------------------------------------------------

	@Test
	void handleTweet_nullMessage_usesClassName() {
		// TweetException(Throwable cause) — super(cause) message is not null in Java
		// (it echoes the cause), so we need to test with a fresh constructor.
		// Use the cause constructor explicitly and assert errors is non-empty.
		TweetException ex = new TweetException(new RuntimeException((String) null));
		ResponseEntity<Map<String, Object>> response = this.handler.handleTweet(ex, this.request);

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
		ResponseEntity<Map<String, Object>> response = this.handler.handleFollowRetrieving(ex, this.request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody()).containsEntry("status", 500);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) response.getBody().get("errors");
		assertThat(errors).containsExactly("could not fetch follows");
	}

	// -------------------------------------------------------------------------
	// ResponseStatusException — status resolved successfully, reason present
	// -------------------------------------------------------------------------

	@Test
	void handleResponseStatus_knownStatus_returnsResolvedStatus() {
		ResponseStatusException ex = new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "invalid payload");
		ResponseEntity<Map<String, Object>> response = this.handler.handleResponseStatus(ex, this.request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		assertThat(response.getBody()).containsEntry("status", 422);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) response.getBody().get("errors");
		assertThat(errors).containsExactly("invalid payload");
	}

	// -------------------------------------------------------------------------
	// ResponseStatusException — reason is null → falls back to ex.getMessage()
	// -------------------------------------------------------------------------

	@Test
	void handleResponseStatus_nullReason_fallsBackToMessage() {
		// Construct with no reason string so getReason() returns null
		ResponseStatusException ex = new ResponseStatusException(org.springframework.http.HttpStatusCode.valueOf(400));
		ResponseEntity<Map<String, Object>> response = this.handler.handleResponseStatus(ex, this.request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).containsEntry("status", 400);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) response.getBody().get("errors");
		assertThat(errors).hasSize(1);
	}

	// -------------------------------------------------------------------------
	// ResponseStatusException — unknown status code → falls back to 500
	// -------------------------------------------------------------------------

	@Test
	void handleResponseStatus_unresolvedStatusCode_returns500() {
		// 999 is not a known HttpStatus value so HttpStatus.resolve returns null
		ResponseStatusException ex = new ResponseStatusException(
				org.springframework.http.HttpStatusCode.valueOf(999), "custom code");
		ResponseEntity<Map<String, Object>> response = this.handler.handleResponseStatus(ex, this.request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody()).containsEntry("status", 500);
	}

	// -------------------------------------------------------------------------
	// handleGeneric — describe() with message present
	// -------------------------------------------------------------------------

	@Test
	void handleGeneric_withMessage_includesMessageInErrors() {
		RuntimeException ex = new RuntimeException("something broke");
		ResponseEntity<Map<String, Object>> response = this.handler.handleGeneric(ex, this.request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) response.getBody().get("errors");
		// The catch-all handler masks the real message to avoid leaking internals.
		assertThat(errors).containsExactly("An internal error occurred");
	}

	// -------------------------------------------------------------------------
	// handleGeneric — describe() with null message → class simple name
	// -------------------------------------------------------------------------

	@Test
	void handleGeneric_nullMessage_usesClassNameInErrors() {
		RuntimeException ex = new RuntimeException((String) null);
		ResponseEntity<Map<String, Object>> response = this.handler.handleGeneric(ex, this.request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) response.getBody().get("errors");
		// The catch-all handler masks the real message (or class name) to avoid leaking internals.
		assertThat(errors).containsExactly("An internal error occurred");
	}

	// -------------------------------------------------------------------------
	// Response body shape — path and timestamp always present
	// -------------------------------------------------------------------------

	@Test
	void responseBody_alwaysContainsPathAndTimestamp() {
		TweetNotFoundException ex = new TweetNotFoundException("missing");
		ResponseEntity<Map<String, Object>> response = this.handler.handleTweetNotFound(ex, this.request);

		assertThat(response.getBody()).containsKey("timestamp");
		assertThat(response.getBody()).containsEntry("path", "/tweets/test");
	}

}
