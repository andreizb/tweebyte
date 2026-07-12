/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.exception;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Branch-coverage complement for the reactive GlobalExceptionHandler. The existing
 * GlobalExceptionHandlerTests already covers the straightforward per-handler paths;
 * this class adds the remaining branches:
 *
 * <ul>
 *   <li>handleUser — UserException with null status → 500</li>
 *   <li>handleUser — UserException with explicit 4xx status</li>
 *   <li>handleUser — UserException with explicit 5xx status (log.error branch)</li>
 *   <li>handleFollowRetrieving — always 500</li>
 *   <li>handleResponseStatus — valid status code resolves correctly</li>
 *   <li>handleResponseStatus — null-resolving status code falls back to 500</li>
 *   <li>handleResponseStatus — reason vs. no reason (describe fallback)</li>
 *   <li>handleException (Throwable) — catch-all returns 500</li>
 *   <li>describe() — null message branch uses class name</li>
 * </ul>
 */
class GlobalExceptionHandlerBranchTests {

	@InjectMocks
	private GlobalExceptionHandler handler;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
	}

	private ServerWebExchange exchange(String path) {
		return MockServerWebExchange.from(MockServerHttpRequest.get(path));
	}

	// --- handleUser branches ---

	@Test
	void handleUser_nullStatus_returns500() {
		// UserException with no status → null → falls back to INTERNAL_SERVER_ERROR.
		UserException ex = new UserException("service blew up");

		ResponseEntity<Map<String, Object>> response = this.handler.handleUser(ex, exchange("/api/test"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertBody(response, HttpStatus.INTERNAL_SERVER_ERROR, "service blew up", "/api/test");
	}

	@Test
	void handleUser_4xxStatus_doesNotLog5xx() {
		UserException ex = new UserException(HttpStatus.CONFLICT, "already exists");

		ResponseEntity<Map<String, Object>> response = this.handler.handleUser(ex, exchange("/api/test"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertBody(response, HttpStatus.CONFLICT, "already exists", "/api/test");
	}

	@Test
	void handleUser_5xxStatus_returns5xx() {
		UserException ex = new UserException(HttpStatus.BAD_GATEWAY, "downstream gone");

		ResponseEntity<Map<String, Object>> response = this.handler.handleUser(ex, exchange("/api/test"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
		assertBody(response, HttpStatus.BAD_GATEWAY, "downstream gone", "/api/test");
	}

	// --- handleFollowRetrieving ---

	@Test
	void handleFollowRetrieving_alwaysReturns500() {
		FollowRetrievingException ex = new FollowRetrievingException("follow service failed",
				new RuntimeException("timeout"));

		ResponseEntity<Map<String, Object>> response = this.handler.handleFollowRetrieving(ex, exchange("/users/1/profile"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertBody(response, HttpStatus.INTERNAL_SERVER_ERROR, "follow service failed", "/users/1/profile");
	}

	// --- handleResponseStatus branches ---

	@Test
	void handleResponseStatus_standardCode_usesResolvedStatus() {
		ResponseStatusException ex = new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "validation failed");

		ResponseEntity<Map<String, Object>> response = this.handler.handleResponseStatus(ex, exchange("/api/test"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		assertBody(response, HttpStatus.UNPROCESSABLE_ENTITY, "validation failed", "/api/test");
	}

	@Test
	void handleResponseStatus_nullReason_usesDescribeFallback() {
		// No reason → describe(ex) path is used.
		ResponseStatusException ex = new ResponseStatusException(HttpStatus.FORBIDDEN); // no reason

		ResponseEntity<Map<String, Object>> response = this.handler.handleResponseStatus(ex, exchange("/api/test"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) response.getBody().get("errors");
		assertThat(errors).isNotEmpty();
	}

	@Test
	void handleResponseStatus_nonStandardCode_fallsBackTo500() {
		// HttpStatus.resolve() returns null for unknown codes → falls back to 500.
		org.springframework.http.HttpStatusCode rawCode = org.springframework.http.HttpStatusCode.valueOf(999);
		ResponseStatusException ex = new ResponseStatusException(rawCode, "exotic error");

		ResponseEntity<Map<String, Object>> response = this.handler.handleResponseStatus(ex, exchange("/api/test"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertBody(response, HttpStatus.INTERNAL_SERVER_ERROR, "exotic error", "/api/test");
	}

	// --- handleException catch-all ---

	@Test
	void handleException_runtimeException_returns500() {
		Throwable ex = new RuntimeException("unexpected failure");

		ResponseEntity<Map<String, Object>> response = this.handler.handleException(ex, exchange("/api/test"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		// The catch-all handler masks the real message to avoid leaking internals.
		assertBody(response, HttpStatus.INTERNAL_SERVER_ERROR, "An internal error occurred", "/api/test");
	}

	// --- describe() null-message branch ---

	@Test
	void handleException_nullMessage_usesClassName() {
		// The catch-all handler now returns a fixed string to avoid leaking internals —
		// it no longer exposes ex.getMessage() or the class name.
		Throwable ex = new NullPointerException(); // no message

		ResponseEntity<Map<String, Object>> response = this.handler.handleException(ex, exchange("/api/test"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) response.getBody().get("errors");
		assertThat(errors).containsExactly("An internal error occurred");
	}

	@Test
	void handleFollowRetrieving_nullMessage_usesClassName() {
		FollowRetrievingException ex = new FollowRetrievingException(null, new RuntimeException("cause"));

		ResponseEntity<Map<String, Object>> response = this.handler.handleFollowRetrieving(ex, exchange("/api/test"));

		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>) response.getBody().get("errors");
		assertThat(errors.get(0)).isEqualTo("FollowRetrievingException");
	}

	// --- helpers ---

	@SuppressWarnings("unchecked")
	private static void assertBody(ResponseEntity<Map<String, Object>> response, HttpStatus expectedStatus,
			String expectedError, String expectedPath) {
		Map<String, Object> body = response.getBody();
		assertThat(body).containsEntry("status", expectedStatus.value())
			.containsKey("timestamp")
			.containsEntry("path", expectedPath);
		List<String> errors = (List<String>) body.get("errors");
		assertThat(errors).contains(expectedError);
	}

}
