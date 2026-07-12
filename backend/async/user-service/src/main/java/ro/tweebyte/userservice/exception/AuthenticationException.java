/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.exception;

public class AuthenticationException extends RuntimeException {

	public AuthenticationException(String message) {
		super(message);
	}

}
