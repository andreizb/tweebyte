/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationExceptionTests {

	@Test
	void testConstructorWithMessage() {
		AuthenticationException ex = new AuthenticationException("bad creds");
		assertThat(ex.getMessage()).isEqualTo("bad creds");
		assertThat(ex.getCause()).isNull();
	}

	@Test
	void testIsRuntimeException() {
		AuthenticationException ex = new AuthenticationException("x");
		assertThat(ex).isInstanceOf(RuntimeException.class);
	}

	@Test
	void testCanBeThrownAndCaught() {
		try {
			throw new AuthenticationException("login failed");
		}
		catch (AuthenticationException caught) {
			assertThat(caught.getMessage()).isEqualTo("login failed");
		}
	}

	@Test
	void testNullMessageAllowed() {
		AuthenticationException ex = new AuthenticationException(null);
		assertThat(ex.getMessage()).isNull();
	}

	@Test
	void testStackTraceIsNotNull() {
		AuthenticationException ex = new AuthenticationException("hi");
		assertThat(ex.getStackTrace()).isNotNull();
	}

}
