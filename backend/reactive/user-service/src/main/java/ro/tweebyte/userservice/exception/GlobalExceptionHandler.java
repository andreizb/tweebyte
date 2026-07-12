/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.exception;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

/**
 * Returns the same response shape as the async/user-service handler so the FE Cucumber
 * suite sees the same response shape on both stacks.
 *
 * <p>Response body is
 * {@code {"timestamp": "...", "status": 400, "path": "/...", "errors": ["..."]}}.
 *
 * @author Andrei Zbarcea
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(UserAlreadyExistsException.class)
	public ResponseEntity<Map<String, Object>> handleUserAlreadyExists(UserAlreadyExistsException ex,
			ServerWebExchange exchange) {
		return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), exchange, HttpStatus.BAD_REQUEST),
				new HttpHeaders(), HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(UserNotFoundException.class)
	public ResponseEntity<Map<String, Object>> handleUserNotFound(UserNotFoundException ex,
			ServerWebExchange exchange) {
		return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), exchange, HttpStatus.NOT_FOUND),
				new HttpHeaders(), HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex,
			ServerWebExchange exchange) {
		return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), exchange, HttpStatus.UNAUTHORIZED),
				new HttpHeaders(), HttpStatus.UNAUTHORIZED);
	}

	@ExceptionHandler(RequestNotPermitted.class)
	public ResponseEntity<Map<String, Object>> handleRateLimit(RequestNotPermitted ex,
			ServerWebExchange exchange) {
		return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), exchange, HttpStatus.TOO_MANY_REQUESTS),
				new HttpHeaders(), HttpStatus.TOO_MANY_REQUESTS);
	}

	@ExceptionHandler(WebExchangeBindException.class)
	public ResponseEntity<Map<String, Object>> handleValidationErrors(WebExchangeBindException ex,
			ServerWebExchange exchange) {
		List<String> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(FieldError::getDefaultMessage)
			.toList();
		return new ResponseEntity<>(getErrorsMap(errors, exchange, HttpStatus.BAD_REQUEST), new HttpHeaders(),
				HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(UserException.class)
	public ResponseEntity<Map<String, Object>> handleUser(UserException ex, ServerWebExchange exchange) {
		HttpStatus status = (ex.getStatus() != null) ? ex.getStatus() : HttpStatus.INTERNAL_SERVER_ERROR;
		if (status.is5xxServerError()) {
			log.error("Unhandled UserException on {}", exchange.getRequest().getPath().value(), ex);
		}
		return new ResponseEntity<>(getErrorsMap(List.of(describe(ex)), exchange, status), new HttpHeaders(), status);
	}

	// The InteractionClient already logs the real downstream cause with the userId
	// context before wrapping; this handler only maps to the status.
	@ExceptionHandler(FollowRetrievingException.class)
	public ResponseEntity<Map<String, Object>> handleFollowRetrieving(FollowRetrievingException ex,
			ServerWebExchange exchange) {
		return new ResponseEntity<>(getErrorsMap(List.of(describe(ex)), exchange, HttpStatus.INTERNAL_SERVER_ERROR),
				new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex,
			ServerWebExchange exchange) {
		// Null-safe resolve (matching the other services' handlers): a non-standard status
		// code resolves to null rather than throwing, and falls back to 500.
		HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
		if (status == null) {
			status = HttpStatus.INTERNAL_SERVER_ERROR;
		}
		return new ResponseEntity<>(
				getErrorsMap(List.of((ex.getReason() != null) ? ex.getReason() : describe(ex)), exchange, status),
				new HttpHeaders(), status);
	}

	@ExceptionHandler(Throwable.class)
	public ResponseEntity<Map<String, Object>> handleException(Throwable ex, ServerWebExchange exchange) {
		log.error("Unhandled exception on {}", exchange.getRequest().getPath().value(), ex);
		return new ResponseEntity<>(getErrorsMap(List.of("An internal error occurred"), exchange, HttpStatus.INTERNAL_SERVER_ERROR),
				new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
	}

	private static String describe(Throwable ex) {
		return (ex.getMessage() != null) ? ex.getMessage() : ex.getClass().getSimpleName();
	}

	private Map<String, Object> getErrorsMap(List<String> errors, ServerWebExchange exchange, HttpStatus status) {
		Map<String, Object> body = new HashMap<>();
		body.put("timestamp", ZonedDateTime.now().toString());
		body.put("status", status.value());
		body.put("path", exchange.getRequest().getPath().value());
		body.put("errors", errors);
		return body;
	}

}
