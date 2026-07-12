/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.gatewayservice.config;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/**
 * Pure unit tests that drive the reactive {@link JwtTokenValidationFilter#filter} method
 * directly against a {@link MockServerWebExchange} and a mocked {@link GatewayFilterChain},
	 * complementing the WebTestClient integration suite. Tokens are signed with an in-memory RSA
	 * key pair and the {@link JwkProvider} is mocked to return the matching public key by
	 * {@code kid}. Covers the path pass-throughs (public auth routes incl. trailing slash, and
	 * downstream service docs), the missing / non-Bearer / invalid token rejections (401 with
	 * {@code WWW-Authenticate}) and the {@code user_id} ownership gate (403).
 */
class JwtTokenValidationFilterFilterTests {

	private static final String KID = "test-kid";

	private static final String ISSUER = "http://localhost:8090/realms/tweebyte";

	private static RSAPublicKey publicKey;

	private static RSAPrivateKey privateKey;

	private static JwkProvider jwkProvider;

	private final JwtTokenValidationFilter filter = new JwtTokenValidationFilter(jwkProvider, new OwnershipRuleSet(),
			ISSUER);

	@BeforeAll
	static void generateKeys() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		KeyPair pair = generator.generateKeyPair();
		publicKey = (RSAPublicKey) pair.getPublic();
		privateKey = (RSAPrivateKey) pair.getPrivate();

		Jwk jwk = mock(Jwk.class);
		given(jwk.getPublicKey()).willReturn(publicKey);
		jwkProvider = mock(JwkProvider.class);
		given(jwkProvider.get(KID)).willReturn(jwk);
	}

	private static MockServerWebExchange exchange(String method, String path, String authHeader) {
		MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.method(
				org.springframework.http.HttpMethod.valueOf(method), path);
		if (authHeader != null) {
			builder = builder.header(HttpHeaders.AUTHORIZATION, authHeader);
		}
		return MockServerWebExchange.from(builder.build());
	}

	private static String token(String owner, RSAPrivateKey signingKey, Date expiresAt) {
		return JWT.create()
			.withKeyId(KID)
			.withIssuer(ISSUER)
			.withSubject(owner)
			.withClaim("user_id", owner)
			.withExpiresAt(expiresAt)
			.sign(Algorithm.RSA256(null, signingKey));
	}

	private static Date future() {
		return new Date(System.currentTimeMillis() + 3_600_000L);
	}

	private void assertPassedThrough(MockServerWebExchange exchange) {
		GatewayFilterChain chain = mock(GatewayFilterChain.class);
		given(chain.filter(any())).willReturn(Mono.empty());

		StepVerifier.create(this.filter.filter(exchange, chain)).verifyComplete();

		then(chain).should().filter(exchange);
		assertThat(exchange.getResponse().getStatusCode())
			.as("a passed-through request must not have a rejection status set")
			.isNull();
	}

	private void assertRejected(MockServerWebExchange exchange, HttpStatus expected) {
		GatewayFilterChain chain = mock(GatewayFilterChain.class);

		StepVerifier.create(this.filter.filter(exchange, chain)).verifyComplete();

		then(chain).should(never()).filter(any());
		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(expected);
	}

	@Test
	void publicLoginPathPassesThrough() {
		assertPassedThrough(exchange("POST", "/user-service/auth/login", null));
	}

	@Test
	void trailingSlashOnPublicPathPassesThrough() {
		assertPassedThrough(exchange("POST", "/user-service/auth/register/", null));
	}

	@Test
	void swaggerUiHtmlIsNotAGatewayRouteExemption() {
		assertRejected(exchange("GET", "/swagger-ui.html", null), HttpStatus.UNAUTHORIZED);
	}

	@Test
	void perRouteApiDocsSuffixPassesThrough() {
		assertPassedThrough(exchange("GET", "/user-service/v3/api-docs", null));
	}

	@Test
	void swaggerUiResourcePrefixIsNotGatewayRouteExemption() {
		assertRejected(exchange("GET", "/swagger-ui/index.css", null), HttpStatus.UNAUTHORIZED);
	}

	@Test
	void aggregatedApiDocsPrefixIsNotGatewayRouteExemption() {
		assertRejected(exchange("GET", "/v3/api-docs/swagger-config", null), HttpStatus.UNAUTHORIZED);
	}

	@Test
	void craftedSuffixApiDocsPathIsNotDocAndRequiresAuth() {
		// Regression: a downstream path merely ENDING in /v3/api-docs must NOT bypass auth.
		// Only the exact per-service doc paths (/<service>/v3/api-docs) are exempt, not an
		// arbitrary /<service>/.../v3/api-docs suffix.
		assertRejected(exchange("GET", "/tweet-service/tweets/search/v3/api-docs", null), HttpStatus.UNAUTHORIZED);
	}

	@Test
	void missingTokenIsUnauthorized() {
		MockServerWebExchange exchange = exchange("GET", "/user-service/users/u1", null);
		assertRejected(exchange, HttpStatus.UNAUTHORIZED);
		assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
	}

	@Test
	void nonBearerAuthorizationHeaderIsUnauthorized() {
		// A present-but-non-Bearer scheme is not a token: extractJwtFromRequest returns null.
		assertRejected(exchange("GET", "/user-service/users/u1", "Basic dXNlcjpwYXNz"), HttpStatus.UNAUTHORIZED);
	}

	@Test
	void suffixLoginPathIsNotPublicAndRequiresAuth() {
		// Regression: a path merely ending in /login must NOT bypass auth.
		assertRejected(exchange("GET", "/tweet-service/tweets/search/login", null), HttpStatus.UNAUTHORIZED);
	}

	@Test
	void validTokenWithMatchingOwnerPassesThrough() {
		String jwt = token("owner-1", privateKey, future());
		assertPassedThrough(exchange("PUT", "/user-service/users/owner-1", "Bearer " + jwt));
	}

	@Test
	void validTokenWithMismatchedOwnerIsForbidden() {
		String jwt = token("owner-2", privateKey, future());
		assertRejected(exchange("PUT", "/user-service/users/owner-1", "Bearer " + jwt), HttpStatus.FORBIDDEN);
	}

}
