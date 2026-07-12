/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Service-layer umbrella exception. Carries an optional {@link HttpStatus} so a caller
 * can shape the response from code; when left null the GlobalExceptionHandler falls back
 * to 500 INTERNAL_SERVER_ERROR.
 *
 * @author Andrei Zbarcea
 */
public class TweetException extends RuntimeException {

	private final HttpStatus status;

	public TweetException(Throwable cause) {
		super(cause);
		this.status = null;
	}

	public TweetException(String message) {
		super(message);
		this.status = null;
	}

	public TweetException(String message, Throwable cause) {
		super(message, cause);
		this.status = null;
	}

	public TweetException(HttpStatus status, String message) {
		super(message);
		this.status = status;
	}

	public TweetException(HttpStatus status, String message, Throwable cause) {
		super(message, cause);
		this.status = status;
	}

	public HttpStatus getStatus() {
		return this.status;
	}

}
