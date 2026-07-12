/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.gatewayservice.config;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

/**
 * Pure unit tests for the reactive rate-limiter {@link KeyResolver}. Resolves a
 * {@link MockServerWebExchange} with and without a bearer token to drive the
 * {@code subjectFromBearer} JWT-parsing branches (mirroring the async gateway's resolver)
 * and the remote-address / anonymous fallbacks. Tokens are hand-crafted JWT-shaped strings
 * ({@code header.payloadBase64url.sig}); the resolver reads the unverified {@code sub}
 * claim, so no signing is required.
 */
class RateLimiterConfigurationTests {

	// A documentation-range (TEST-NET-3) remote address, built from raw octets so the
	// expected fallback key is whatever getHostAddress() renders for this very address.
	private static final InetAddress REMOTE_ADDRESS = remoteAddress();

	private final KeyResolver resolver = new RateLimiterConfiguration().gatewayKeyResolver();

	private static InetAddress remoteAddress() {
		try {
			return InetAddress.getByAddress(new byte[] { (byte) 203, 0, 113, 7 });
		}
		catch (UnknownHostException ex) {
			throw new IllegalStateException("4-byte literal is always a valid IPv4 address", ex);
		}
	}

	private static String bearerWithPayload(String json) {
		String payload = Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(json.getBytes(StandardCharsets.UTF_8));
		return "Bearer header." + payload + ".signature";
	}

	private MockServerWebExchange exchangeWithAuth(String authHeader) {
		MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("/user-service/anything")
			.remoteAddress(new InetSocketAddress(REMOTE_ADDRESS, 51000));
		if (authHeader != null) {
			builder = builder.header(HttpHeaders.AUTHORIZATION, authHeader);
		}
		return MockServerWebExchange.from(builder.build());
	}

	private void expectKey(MockServerWebExchange exchange, String expectedKey) {
		StepVerifier.create(this.resolver.resolve(exchange)).expectNext(expectedKey).verifyComplete();
	}

	private void expectRemoteAddressKey(MockServerWebExchange exchange) {
		expectKey(exchange, REMOTE_ADDRESS.getHostAddress());
	}

	@Test
	void resolvesSubjectFromValidBearerToken() {
		expectKey(exchangeWithAuth(bearerWithPayload("{\"iss\":\"x\",\"sub\":\"caller-42\"}")), "caller-42");
	}

	@Test
	void fallsBackToRemoteAddressWhenNoAuthorizationHeader() {
		expectRemoteAddressKey(exchangeWithAuth(null));
	}

	@Test
	void fallsBackToRemoteAddressWhenSchemeIsNotBearer() {
		expectRemoteAddressKey(exchangeWithAuth("Basic dXNlcjpwYXNz"));
	}

	@Test
	void fallsBackToRemoteAddressWhenTokenIsNotThreeParts() {
		expectRemoteAddressKey(exchangeWithAuth("Bearer onlyonepart"));
	}

	@Test
	void fallsBackToRemoteAddressWhenSubClaimIsAbsent() {
		expectRemoteAddressKey(exchangeWithAuth(bearerWithPayload("{\"iss\":\"x\",\"aud\":\"y\"}")));
	}

	@Test
	void fallsBackToRemoteAddressWhenPayloadIsNotBase64() {
		expectRemoteAddressKey(exchangeWithAuth("Bearer header.@@@not-base64@@@.signature"));
	}

	@Test
	void fallsBackToRemoteAddressWhenSubValueHasNoOpeningQuote() {
		// "sub" present with a non-string value: no opening quote after the colon → null.
		expectRemoteAddressKey(exchangeWithAuth(bearerWithPayload("{\"sub\":123}")));
	}

	@Test
	void fallsBackToRemoteAddressWhenSubValueHasNoClosingQuote() {
		// "sub" present, opening quote but no closing quote → null.
		expectRemoteAddressKey(exchangeWithAuth(bearerWithPayload("{\"sub\":\"unterminated")));
	}

	@Test
	void fallsBackToAnonymousWhenRemoteAddressIsAbsent() {
		// No bearer and no resolvable remote address → the literal anonymous key.
		MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/user-service/anything"));
		expectKey(exchange, "anonymous");
	}

}
