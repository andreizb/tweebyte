/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.exception;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;

/**
 * GlobalExceptionHandler for the reactive interaction-service. Maps service-layer
 * IllegalArgumentException (the "resource does not exist" / "unauthorised or not found"
 * cases from LikeService / ReplyService / RetweetService) to 404 Not Found, matching the
 * dedicated *NotFoundException handlers on this stack.
 *
 * Body shape: {@code { "timestamp": "...", "status": <int>, "path": "<path>", "errors":
 * ["..."] }}
 *
 * @author Andrei Zbarcea
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

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

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex,
			ServerWebExchange exchange) {
		return new ResponseEntity<>(getErrorsMap(List.of(describe(ex)), exchange, HttpStatus.NOT_FOUND),
				new HttpHeaders(), HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(TweetNotFoundException.class)
	public ResponseEntity<Map<String, Object>> handleTweetNotFound(TweetNotFoundException ex,
			ServerWebExchange exchange) {
		return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), exchange, HttpStatus.NOT_FOUND),
				new HttpHeaders(), HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(FollowNotFoundException.class)
	public ResponseEntity<Map<String, Object>> handleFollowNotFound(FollowNotFoundException ex,
			ServerWebExchange exchange) {
		return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), exchange, HttpStatus.NOT_FOUND),
				new HttpHeaders(), HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(UserNotFoundException.class)
	public ResponseEntity<Map<String, Object>> handleUserNotFound(UserNotFoundException ex,
			ServerWebExchange exchange) {
		return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), exchange, HttpStatus.NOT_FOUND),
				new HttpHeaders(), HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(InteractionException.class)
	public ResponseEntity<Map<String, Object>> handleInteractionException(InteractionException ex,
			ServerWebExchange exchange) {
		HttpStatus status = (ex.getStatus() != null) ? ex.getStatus() : HttpStatus.INTERNAL_SERVER_ERROR;
		if (status.is5xxServerError()) {
			log.error("Unhandled InteractionException on {}", exchange.getRequest().getPath().value(), ex);
		}
		return new ResponseEntity<>(getErrorsMap(List.of(describe(ex)), exchange, status), new HttpHeaders(), status);
	}

	/**
	 * Surface a ResponseStatusException (e.g. 400 for a malformed @PathVariable UUID)
	 * with its carried status instead of letting the Throwable catch-all clobber it to
	 * 500.
	 * @param ex the response-status exception carrying the intended HTTP status
	 * @param exchange the current server exchange, used to report the request path
	 * @return a response entity preserving the exception's status and error message
	 */
	@ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
	public ResponseEntity<Map<String, Object>> handleResponseStatus(
			org.springframework.web.server.ResponseStatusException ex, ServerWebExchange exchange) {
		HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
		if (status == null) {
			status = HttpStatus.INTERNAL_SERVER_ERROR;
		}
		return new ResponseEntity<>(
				getErrorsMap(List.of((ex.getReason() != null) ? ex.getReason() : ex.getMessage()), exchange, status),
				new HttpHeaders(), status);
	}

	@ExceptionHandler(Throwable.class)
	public ResponseEntity<Map<String, Object>> handleGeneric(Throwable ex, ServerWebExchange exchange) {
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
