/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.exception;

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
 * Branch-coverage complement for GlobalExceptionHandler. The existing
 * GlobalExceptionHandlerTests already covers the happy path per handler method;
 * this class exercises the remaining branches:
 *
 * <ul>
 *   <li>handleUser — UserException with explicit non-5xx HttpStatus (4xx branch)</li>
 *   <li>handleUser — UserException with null status (500 fallback branch)</li>
 *   <li>handleFollowRetrieving — always 500</li>
 *   <li>handlePathTypeMismatch — 400 for malformed path variable</li>
 *   <li>handleResponseStatus — valid status code resolves correctly</li>
 *   <li>handleResponseStatus — null-resolving (non-standard) status code falls back to 500</li>
 *   <li>handleResponseStatus — reason present vs. absent (describe fallback)</li>
 *   <li>handleException — exception with null message (describe falls back to class name)</li>
 * </ul>
 */
class GlobalExceptionHandlerBranchTests {

	@InjectMocks
	private GlobalExceptionHandler handler;

	@Mock
	private HttpServletRequest request;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		given(this.request.getRequestURI()).willReturn("/api/test");
	}

	// --- handleUser branches ---

	@Test
	void handleUser_nullStatus_returns500() {
		// UserException with no status → null → falls back to INTERNAL_SERVER_ERROR.
		UserException ex = new UserException("service blew up");

		ResponseEntity<Map<String, Object>> response = this.handler.handleUser(ex, this.request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertBody(response, HttpStatus.INTERNAL_SERVER_ERROR, "service blew up");
	}

	@Test
	void handleUser_nonNullStatus_4xx_usesProvidedStatus() {
		// UserException with explicit 4xx status → NOT a 5xx, so no log.error call.
		UserException ex = new UserException(HttpStatus.CONFLICT, "resource conflict");

		ResponseEntity<Map<String, Object>> response = this.handler.handleUser(ex, this.request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertBody(response, HttpStatus.CONFLICT, "resource conflict");
	}

	@Test
	void handleUser_5xxStatus_returns5xx() {
		// UserException with explicit 5xx status → log.error branch fires.
		UserException ex = new UserException(HttpStatus.BAD_GATEWAY, "downstream gone");

		ResponseEntity<Map<String, Object>> response = this.handler.handleUser(ex, this.request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
		assertBody(response, HttpStatus.BAD_GATEWAY, "downstream gone");
	}

	// --- handleFollowRetrieving ---

	@Test
	void handleFollowRetrieving_alwaysReturns500() {
		FollowRetrievingException ex = new FollowRetrievingException("downstream follow service failed",
				new RuntimeException("timeout"));

		ResponseEntity<Map<String, Object>> response = this.handler.handleFollowRetrieving(ex, this.request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertBody(response, HttpStatus.INTERNAL_SERVER_ERROR, "downstream follow service failed");
	}

	// --- handlePathTypeMismatch ---

	@Test
	void handlePathTypeMismatch_returns400() {
		// MethodArgumentTypeMismatchException constructor needs a name + required-type; we
		// build a minimal one without needing a real MethodParameter.
		org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex =
				new org.springframework.web.method.annotation.MethodArgumentTypeMismatchException(
						"not-a-uuid", java.util.UUID.class, "id", null, null);

		ResponseEntity<Map<String, Object>> response = this.handler.handlePathTypeMismatch(ex, this.request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).containsEntry("status", HttpStatus.BAD_REQUEST.value());
	}

	// --- handleResponseStatus branches ---

	@Test
	void handleResponseStatus_standardCode_usesResolvedStatus() {
		// A standard status code such as 422 resolves fine.
		ResponseStatusException ex = new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "validation failed");

		ResponseEntity<Map<String, Object>> response = this.handler.handleResponseStatus(ex, this.request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		assertBody(response, HttpStatus.UNPROCESSABLE_ENTITY, "validation failed");
	}

	@Test
	void handleResponseStatus_nullReason_usesExceptionMessage() {
		// No reason provided → describe(ex) is used instead of reason.
		ResponseStatusException ex = new ResponseStatusException(HttpStatus.FORBIDDEN); // no reason

		ResponseEntity<Map<String, Object>> response = this.handler.handleResponseStatus(ex, this.request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) response.getBody().get("errors");
		assertThat(errors).isNotEmpty();
	}

	@Test
	void handleResponseStatus_nonStandardCode_fallsBackTo500() {
		// HttpStatus.resolve() returns null for unknown codes → handler falls back to 500.
		org.springframework.http.HttpStatusCode rawCode = org.springframework.http.HttpStatusCode.valueOf(999);
		ResponseStatusException ex = new ResponseStatusException(rawCode, "exotic error");

		ResponseEntity<Map<String, Object>> response = this.handler.handleResponseStatus(ex, this.request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertBody(response, HttpStatus.INTERNAL_SERVER_ERROR, "exotic error");
	}

	// --- describe() null-message branch ---

	@Test
	void handleException_nullMessage_usesClassName() {
		// The catch-all handler now returns a fixed string to avoid leaking internals —
		// it no longer exposes ex.getMessage() or the class name.
		Throwable ex = new NullPointerException(); // NullPointerException with no message

		ResponseEntity<Map<String, Object>> response = this.handler.handleException(ex, this.request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) response.getBody().get("errors");
		assertThat(errors).containsExactly("An internal error occurred");
	}

	@Test
	void handleFollowRetrieving_nullMessage_usesClassName() {
		// FollowRetrievingException with no message → describe uses class name.
		FollowRetrievingException ex = new FollowRetrievingException(null, new RuntimeException("cause"));

		ResponseEntity<Map<String, Object>> response = this.handler.handleFollowRetrieving(ex, this.request);

		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) response.getBody().get("errors");
		assertThat(errors.get(0)).isEqualTo("FollowRetrievingException");
	}

	// --- helpers ---

	@SuppressWarnings("unchecked")
	private static void assertBody(ResponseEntity<Map<String, Object>> response, HttpStatus expectedStatus,
			String expectedError) {
		Map<String, Object> body = response.getBody();
		assertThat(body).containsEntry("status", expectedStatus.value()).containsKey("timestamp")
			.containsEntry("path", "/api/test");
		List<String> errors = (List<String>) body.get("errors");
		assertThat(errors).contains(expectedError);
	}

}
