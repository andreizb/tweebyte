/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.exception;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * GlobalExceptionHandler for the async interaction-service. Maps service-layer
 * IllegalArgumentException (unauthorised / not-found cases from ReplyService /
 * RetweetService / LikeService) and Spring's MethodArgumentTypeMismatchException
 * (malformed UUID path variables) to structured 4xx responses, matching the user-service
 * / tweet-service error-shape on the same stack.
 *
 * Body shape: {@code { "timestamp": "...", "status": int, "path": "path", "errors":
 * ["..."] }}.
 *
 * @author Andrei Zbarcea
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex,
			HttpServletRequest request) {
		List<String> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(FieldError::getDefaultMessage)
			.toList();
		return new ResponseEntity<>(getErrorsMap(errors, request, HttpStatus.BAD_REQUEST), new HttpHeaders(),
				HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(BindException.class)
	public ResponseEntity<Map<String, Object>> handleBindException(BindException ex, HttpServletRequest request) {
		List<String> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(FieldError::getDefaultMessage)
			.toList();
		return new ResponseEntity<>(getErrorsMap(errors, request, HttpStatus.BAD_REQUEST), new HttpHeaders(),
				HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<Map<String, Object>> handlePathTypeMismatch(MethodArgumentTypeMismatchException ex,
			HttpServletRequest request) {
		return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), request, HttpStatus.BAD_REQUEST),
				new HttpHeaders(), HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex,
			HttpServletRequest request) {
		return new ResponseEntity<>(getErrorsMap(List.of(describe(ex)), request, HttpStatus.NOT_FOUND),
				new HttpHeaders(), HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(TweetNotFoundException.class)
	public ResponseEntity<Map<String, Object>> handleTweetNotFound(TweetNotFoundException ex,
			HttpServletRequest request) {
		return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), request, HttpStatus.NOT_FOUND),
				new HttpHeaders(), HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(FollowNotFoundException.class)
	public ResponseEntity<Map<String, Object>> handleFollowNotFound(FollowNotFoundException ex,
			HttpServletRequest request) {
		return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), request, HttpStatus.NOT_FOUND),
				new HttpHeaders(), HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(UserNotFoundException.class)
	public ResponseEntity<Map<String, Object>> handleUserNotFound(UserNotFoundException ex,
			HttpServletRequest request) {
		return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), request, HttpStatus.NOT_FOUND),
				new HttpHeaders(), HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(InteractionException.class)
	public ResponseEntity<Map<String, Object>> handleInteractionException(InteractionException ex,
			HttpServletRequest request) {
		HttpStatus status = (ex.getStatus() != null) ? ex.getStatus() : HttpStatus.INTERNAL_SERVER_ERROR;
		if (status.is5xxServerError()) {
			log.error("Unhandled InteractionException on {}", request.getRequestURI(), ex);
		}
		return new ResponseEntity<>(getErrorsMap(List.of(describe(ex)), request, status), new HttpHeaders(), status);
	}

	/**
	 * Surface a ResponseStatusException (e.g. 400 for a malformed @PathVariable UUID)
	 * with its carried status instead of letting the Throwable catch-all clobber it to
	 * 500.
	 * @param ex the response-status exception carrying the intended HTTP status
	 * @param request the current request, used to report the failing path
	 * @return the error response carrying the exception's status and reason
	 */
	@ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
	public ResponseEntity<Map<String, Object>> handleResponseStatus(
			org.springframework.web.server.ResponseStatusException ex, HttpServletRequest request) {
		HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
		if (status == null) {
			status = HttpStatus.INTERNAL_SERVER_ERROR;
		}
		return new ResponseEntity<>(
				getErrorsMap(List.of((ex.getReason() != null) ? ex.getReason() : ex.getMessage()), request, status),
				new HttpHeaders(), status);
	}

	@ExceptionHandler(Throwable.class)
	public ResponseEntity<Map<String, Object>> handleGeneric(Throwable ex, HttpServletRequest request) {
		log.error("Unhandled exception on {}", request.getRequestURI(), ex);
		return new ResponseEntity<>(getErrorsMap(List.of("An internal error occurred"), request, HttpStatus.INTERNAL_SERVER_ERROR),
				new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
	}

	private static String describe(Throwable ex) {
		return (ex.getMessage() != null) ? ex.getMessage() : ex.getClass().getSimpleName();
	}

	private Map<String, Object> getErrorsMap(List<String> errors, HttpServletRequest request, HttpStatus status) {
		Map<String, Object> body = new HashMap<>();
		body.put("timestamp", ZonedDateTime.now().toString());
		body.put("status", status.value());
		body.put("path", request.getRequestURI());
		body.put("errors", errors);
		return body;
	}

}
