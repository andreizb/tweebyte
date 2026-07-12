/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised by /media/{id}/preview when the source asset's content type has no registered
 * preview pipeline. Carries 415 UNSUPPORTED_MEDIA_TYPE so the GlobalExceptionHandler
 * shapes the response from {@link UserException#getStatus()}. Today only {@code image/*}
 * is supported; this is the default branch of the per-content-type dispatch.
 *
 * @author Andrei Zbarcea
 */
public class UnsupportedMediaTypeException extends UserException {

	public UnsupportedMediaTypeException(String contentType) {
		super(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
				"No preview pipeline for content type: " + ((contentType != null) ? contentType : "unknown"));
	}

}
