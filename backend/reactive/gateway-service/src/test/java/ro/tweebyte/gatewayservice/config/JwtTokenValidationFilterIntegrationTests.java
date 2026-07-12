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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * End-to-end JWT validation behaviour through {@link JwtTokenValidationFilter}. Covers
 * the five required JWT invariants: valid/invalid/missing/expired/malformed, plus the
 * {@code /login} bypass path. Tokens are signed with a locally generated RSA key pair and
 * the {@link JwkProvider} is mocked to return the matching public key by {@code kid},
 * standing in for the Keycloak JWKS endpoint. Routes target unreachable localhost ports —
 * for the "valid token" path we only assert the response is NOT 401, which means the JWT
 * layer cleared and the request progressed to the routing/Netty layer (where it then
 * fails with 5xx).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "20000")
// Override the routes wholesale so the downstream port is unreachable - we only assert
// whether the JWT filter accepted (not-401) or rejected (401) the request.
@TestPropertySource(locations = "classpath:application-test-jwt.properties")
class JwtTokenValidationFilterIntegrationTests {

	private static final String KID = "test-kid";

	private static final String ISSUER = "http://localhost:8090/realms/tweebyte";

	private static final String OWNER = "integration-test-user";

	@Autowired
	private WebTestClient webTestClient;

	@MockBean
	private JwkProvider jwkProvider;

	private static RSAPrivateKey privateKey;

	private static RSAPublicKey publicKey;

	@BeforeAll
	static void generateKeys() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		KeyPair pair = generator.generateKeyPair();
		privateKey = (RSAPrivateKey) pair.getPrivate();
		publicKey = (RSAPublicKey) pair.getPublic();
	}

	@BeforeEach
	void stubJwks() throws Exception {
		Jwk jwk = mock(Jwk.class);
		given(jwk.getPublicKey()).willReturn(publicKey);
		given(this.jwkProvider.get(KID)).willReturn(jwk);
	}

	private String signValidToken() {
		Algorithm alg = Algorithm.RSA256(publicKey, privateKey);
		return JWT.create()
			.withKeyId(KID)
			.withIssuer(ISSUER)
			.withSubject(OWNER)
			.withClaim("user_id", OWNER)
			.withIssuedAt(new Date())
			.withExpiresAt(new Date(System.currentTimeMillis() + 60_000))
			.sign(alg);
	}

	private String signExpiredToken() {
		Algorithm alg = Algorithm.RSA256(publicKey, privateKey);
		return JWT.create()
			.withKeyId(KID)
			.withIssuer(ISSUER)
			.withSubject("expired-test-user")
			.withClaim("user_id", "expired-test-user")
			.withIssuedAt(new Date(System.currentTimeMillis() - 120_000))
			.withExpiresAt(new Date(System.currentTimeMillis() - 60_000))
			.sign(alg);
	}

	@Test
	void validTokenPassesJwtFilter() {
		String token = signValidToken();
		HttpStatusCode status = this.webTestClient.get()
			.uri("/user-service/anything")
			.header("Authorization", "Bearer " + token)
			.exchange()
			.returnResult(Void.class)
			.getStatus();
		assertThat(status).as("Valid token should not be rejected by JWT filter, got " + status)
			.isNotEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void invalidSignatureRejected() {
		// Token signed with wrong (HMAC) algorithm → RSA verifier rejects.
		String bogus = JWT.create().withSubject("attacker").sign(Algorithm.HMAC256("not-the-real-key"));
		this.webTestClient.get()
			.uri("/user-service/anything")
			.header("Authorization", "Bearer " + bogus)
			.exchange()
			.expectStatus()
			.isUnauthorized();
	}

	@Test
	void missingAuthorizationHeaderRejected() {
		this.webTestClient.get().uri("/user-service/anything").exchange().expectStatus().isUnauthorized();
	}

	@Test
	void expiredTokenRejected() {
		String expired = signExpiredToken();
		this.webTestClient.get()
			.uri("/user-service/anything")
			.header("Authorization", "Bearer " + expired)
			.exchange()
			.expectStatus()
			.isUnauthorized();
	}

	@Test
	void malformedTokenRejected() {
		this.webTestClient.get()
			.uri("/user-service/anything")
			.header("Authorization", "Bearer asdfg")
			.exchange()
			.expectStatus()
			.isUnauthorized();
	}

	@Test
	void loginPathBypassesJwtFilter() {
		// No Authorization header, but /user-service/auth/login should not be challenged.
		HttpStatusCode status = this.webTestClient.get()
			.uri("/user-service/auth/login")
			.exchange()
			.returnResult(Void.class)
			.getStatus();
		assertThat(status).as("/auth/login must bypass JWT validation, got " + status)
			.isNotEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void suffixLoginPathIsNotExempt() {
		// Regression: a path merely ending in /login must NOT bypass auth.
		this.webTestClient.get().uri("/tweet-service/tweets/search/login").exchange().expectStatus().isUnauthorized();
	}

	@Test
	void matchingOwnerPassesOwnershipGate() {
		// Token user_id claim == the {userId} segment of an owner-scoped mutating route.
		String token = signValidToken();
		HttpStatusCode status = this.webTestClient.put()
			.uri("/user-service/users/" + OWNER)
			.header("Authorization", "Bearer " + token)
			.exchange()
			.returnResult(Void.class)
			.getStatus();
		assertThat(status).as("matching owner must clear the gate, got " + status)
			.isNotEqualTo(HttpStatus.UNAUTHORIZED)
			.isNotEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void mismatchedOwnerIsForbidden() {
		// Token user_id claim != the {userId} segment → edge-level ownership rejects with 403.
		String token = signValidToken();
		this.webTestClient.put()
			.uri("/user-service/users/someone-else")
			.header("Authorization", "Bearer " + token)
			.exchange()
			.expectStatus()
			.isForbidden();
	}

}
