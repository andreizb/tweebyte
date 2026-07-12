/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Service-layer umbrella exception. Carries an optional {@link HttpStatus} so a caller
 * can shape the response from code; when left null the GlobalExceptionHandler falls back
 * to 500 INTERNAL_SERVER_ERROR.
 *
 * @author Andrei Zbarcea
 */
public class InteractionException extends RuntimeException {

	private final HttpStatus status;

	public InteractionException(Throwable cause) {
		super(cause);
		this.status = null;
	}

	public InteractionException(String message) {
		super(message);
		this.status = null;
	}

	public InteractionException(String message, Throwable cause) {
		super(message, cause);
		this.status = null;
	}

	public InteractionException(HttpStatus status, String message) {
		super(message);
		this.status = status;
	}

	public InteractionException(HttpStatus status, String message, Throwable cause) {
		super(message, cause);
		this.status = status;
	}

	public HttpStatus getStatus() {
		return this.status;
	}

}
