/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.exception;

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

	@ExceptionHandler(TweetNotFoundException.class)
	public ResponseEntity<Map<String, Object>> handleTweetNotFound(TweetNotFoundException ex,
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

	// malformed UUID in @PathVariable → 400 (mirroring reactive WebFlux default).
	@ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
	public ResponseEntity<Map<String, Object>> handlePathTypeMismatch(
			org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex,
			HttpServletRequest request) {
		return new ResponseEntity<>(getErrorsMap(List.of(ex.getMessage()), request, HttpStatus.BAD_REQUEST),
				new HttpHeaders(), HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(TweetException.class)
	public ResponseEntity<Map<String, Object>> handleTweet(TweetException ex, HttpServletRequest request) {
		HttpStatus status = (ex.getStatus() != null) ? ex.getStatus() : HttpStatus.INTERNAL_SERVER_ERROR;
		if (status.is5xxServerError()) {
			log.error("Unhandled TweetException on {}", request.getRequestURI(), ex);
		}
		return new ResponseEntity<>(getErrorsMap(List.of(describe(ex)), request, status), new HttpHeaders(), status);
	}

	// The InteractionClient already logs the real downstream cause with the userId
	// context before wrapping; this handler only maps to the status.
	@ExceptionHandler(FollowRetrievingException.class)
	public ResponseEntity<Map<String, Object>> handleFollowRetrieving(FollowRetrievingException ex,
			HttpServletRequest request) {
		return new ResponseEntity<>(getErrorsMap(List.of(describe(ex)), request, HttpStatus.INTERNAL_SERVER_ERROR),
				new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
	}

	// A service-thrown ResponseStatusException carries its own status such as 400, so
	// without this handler the Throwable catch-all below would clobber it to 500.
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

	private Map<String, Object> getErrorsMap(List<String> errors, HttpServletRequest request, HttpStatus httpStatus) {
		Map<String, Object> errorResponse = new HashMap<>();

		errorResponse.put("timestamp", ZonedDateTime.now().toString());
		errorResponse.put("status", httpStatus.value());
		errorResponse.put("path", request.getRequestURI());
		errorResponse.put("errors", errors);

		return errorResponse;
	}

}
