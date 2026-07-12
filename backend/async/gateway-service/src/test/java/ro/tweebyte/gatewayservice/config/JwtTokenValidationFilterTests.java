/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.gatewayservice.config;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/**
 * Unit tests for the gateway authentication filter, exercising {@code doFilterInternal}
 * directly against an in-memory RSA key pair. The Keycloak JWKS endpoint is stood in for
 * by a mocked {@link JwkProvider} that returns the matching public key by {@code kid}.
 * Covers the pass-through paths (public auth routes and local actuator), the
 * JWT-verification rejections (missing, malformed, rogue signature, expired) and the
 * edge-level ownership enforcement on the {@code user_id} claim (owner match passes, owner
 * mismatch is forbidden, and non-owner routes are unaffected).
 */
class JwtTokenValidationFilterTests {

	private static final String KID = "test-kid";

	private static final String ISSUER = "http://localhost:8090/realms/tweebyte";

	private static RSAPublicKey publicKey;

	private static RSAPrivateKey privateKey;

	private static RSAPrivateKey roguePrivateKey;

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
		roguePrivateKey = (RSAPrivateKey) generator.generateKeyPair().getPrivate();

		Jwk jwk = mock(Jwk.class);
		given(jwk.getPublicKey()).willReturn(publicKey);
		jwkProvider = mock(JwkProvider.class);
		given(jwkProvider.get(KID)).willReturn(jwk);
	}

	@Test
	void publicLoginPathPassesThrough() throws Exception {
		HttpServletRequest request = request("POST", "/user-service/auth/login", null);
		HttpServletResponse response = mock(HttpServletResponse.class);
		FilterChain chain = mock(FilterChain.class);

		this.filter.doFilterInternal(request, response, chain);

		then(chain).should().doFilter(request, response);
		then(response).should(never()).setStatus(anyInt());
	}

	@Test
	void actuatorPathPassesThrough() throws Exception {
		HttpServletRequest request = request("GET", "/actuator/prometheus", null);
		HttpServletResponse response = mock(HttpServletResponse.class);
		FilterChain chain = mock(FilterChain.class);

		this.filter.doFilterInternal(request, response, chain);

		then(chain).should().doFilter(request, response);
		then(response).should(never()).setStatus(anyInt());
	}

	@Test
	void corsPreflightPassesThroughWithoutToken() throws Exception {
		// A CORS preflight (OPTIONS + Origin + Access-Control-Request-Method) carries no
		// Authorization header; it must fall through to the CorsFilter rather than be rejected
		// 401 by this higher-precedence filter. Parity with the reactive gateway, where Spring
		// Cloud Gateway answers preflight before the JWT filter runs.
		HttpServletRequest request = preflightRequest("/user-service/users/u1");
		HttpServletResponse response = mock(HttpServletResponse.class);
		FilterChain chain = mock(FilterChain.class);

		this.filter.doFilterInternal(request, response, chain);

		then(chain).should().doFilter(request, response);
		then(response).should(never()).setStatus(anyInt());
	}

	@Test
	void pathEndingInLoginIsNotPublic() throws Exception {
		StringWriter body = new StringWriter();
		HttpServletRequest request = request("GET", "/tweet-service/tweets/search/login", null);
		HttpServletResponse response = response(body);
		FilterChain chain = mock(FilterChain.class);

		this.filter.doFilterInternal(request, response, chain);

		then(response).should().setStatus(HttpStatus.UNAUTHORIZED.value());
		then(chain).should(never()).doFilter(any(), any());
	}

	@Test
	void missingTokenReturnsUnauthorized() throws Exception {
		StringWriter body = new StringWriter();
		HttpServletRequest request = request("GET", "/user-service/users/u1", null);
		HttpServletResponse response = response(body);
		FilterChain chain = mock(FilterChain.class);

		this.filter.doFilterInternal(request, response, chain);

		then(response).should().setStatus(HttpStatus.UNAUTHORIZED.value());
		then(response).should().setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		then(chain).should(never()).doFilter(any(), any());
		assertThat(body.toString()).contains("Unauthorized");
	}

	@Test
	void malformedTokenReturnsUnauthorized() throws Exception {
		StringWriter body = new StringWriter();
		HttpServletRequest request = request("GET", "/user-service/users/u1", "Bearer not-a-jwt");
		HttpServletResponse response = response(body);
		FilterChain chain = mock(FilterChain.class);

		this.filter.doFilterInternal(request, response, chain);

		then(response).should().setStatus(HttpStatus.UNAUTHORIZED.value());
		then(chain).should(never()).doFilter(any(), any());
	}

	@Test
	void rogueSignatureReturnsUnauthorized() throws Exception {
		StringWriter body = new StringWriter();
		String jwt = token("u1", roguePrivateKey, future());
		HttpServletRequest request = request("GET", "/user-service/users/u1", "Bearer " + jwt);
		HttpServletResponse response = response(body);
		FilterChain chain = mock(FilterChain.class);

		this.filter.doFilterInternal(request, response, chain);

		then(response).should().setStatus(HttpStatus.UNAUTHORIZED.value());
		then(chain).should(never()).doFilter(any(), any());
	}

	@Test
	void expiredTokenReturnsUnauthorized() throws Exception {
		StringWriter body = new StringWriter();
		String jwt = token("u1", privateKey, past());
		HttpServletRequest request = request("GET", "/user-service/users/u1", "Bearer " + jwt);
		HttpServletResponse response = response(body);
		FilterChain chain = mock(FilterChain.class);

		this.filter.doFilterInternal(request, response, chain);

		then(response).should().setStatus(HttpStatus.UNAUTHORIZED.value());
		then(chain).should(never()).doFilter(any(), any());
	}

	@Test
	void validTokenWithMatchingOwnerPassesThrough() throws Exception {
		String jwt = token("u1", privateKey, future());
		HttpServletRequest request = request("PUT", "/user-service/users/u1", "Bearer " + jwt);
		HttpServletResponse response = mock(HttpServletResponse.class);
		FilterChain chain = mock(FilterChain.class);

		this.filter.doFilterInternal(request, response, chain);

		then(chain).should().doFilter(request, response);
		then(response).should(never()).setStatus(anyInt());
	}

	@Test
	void validTokenWithMismatchedOwnerReturnsForbidden() throws Exception {
		StringWriter body = new StringWriter();
		String jwt = token("u2", privateKey, future());
		HttpServletRequest request = request("PUT", "/user-service/users/u1", "Bearer " + jwt);
		HttpServletResponse response = response(body);
		FilterChain chain = mock(FilterChain.class);

		this.filter.doFilterInternal(request, response, chain);

		then(response).should().setStatus(HttpStatus.FORBIDDEN.value());
		then(response).should(never()).setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		then(chain).should(never()).doFilter(any(), any());
		assertThat(body.toString()).contains("Forbidden");
	}

	@Test
	void validTokenOnReadRoutePassesThroughRegardlessOfOwner() throws Exception {
		String jwt = token("u2", privateKey, future());
		HttpServletRequest request = request("GET", "/user-service/users/u1", "Bearer " + jwt);
		HttpServletResponse response = mock(HttpServletResponse.class);
		FilterChain chain = mock(FilterChain.class);

		this.filter.doFilterInternal(request, response, chain);

		then(chain).should().doFilter(request, response);
		then(response).should(never()).setStatus(anyInt());
	}

	@Test
	void validTokenOnNonOwnerMutationPassesThrough() throws Exception {
		String jwt = token("u2", privateKey, future());
		HttpServletRequest request = request("POST", "/user-service/media", "Bearer " + jwt);
		HttpServletResponse response = mock(HttpServletResponse.class);
		FilterChain chain = mock(FilterChain.class);

		this.filter.doFilterInternal(request, response, chain);

		then(chain).should().doFilter(request, response);
		then(response).should(never()).setStatus(anyInt());
	}

	@Test
	void swaggerUiHtmlPassesThroughUnauthenticated() throws Exception {
		HttpServletRequest request = request("GET", "/swagger-ui.html", null);
		HttpServletResponse response = mock(HttpServletResponse.class);
		FilterChain chain = mock(FilterChain.class);

		this.filter.doFilterInternal(request, response, chain);

		then(chain).should().doFilter(request, response);
		then(response).should(never()).setStatus(anyInt());
	}

	@Test
	void perRouteApiDocsSuffixPassesThroughUnauthenticated() throws Exception {
		HttpServletRequest request = request("GET", "/user-service/v3/api-docs", null);
		HttpServletResponse response = mock(HttpServletResponse.class);
		FilterChain chain = mock(FilterChain.class);

		this.filter.doFilterInternal(request, response, chain);

		then(chain).should().doFilter(request, response);
		then(response).should(never()).setStatus(anyInt());
	}

	@Test
	void swaggerUiResourcePrefixPassesThroughUnauthenticated() throws Exception {
		HttpServletRequest request = request("GET", "/swagger-ui/index.css", null);
		HttpServletResponse response = mock(HttpServletResponse.class);
		FilterChain chain = mock(FilterChain.class);

		this.filter.doFilterInternal(request, response, chain);

		then(chain).should().doFilter(request, response);
		then(response).should(never()).setStatus(anyInt());
	}

	@Test
	void aggregatedApiDocsPrefixPassesThroughUnauthenticated() throws Exception {
		HttpServletRequest request = request("GET", "/v3/api-docs/swagger-config", null);
		HttpServletResponse response = mock(HttpServletResponse.class);
		FilterChain chain = mock(FilterChain.class);

		this.filter.doFilterInternal(request, response, chain);

		then(chain).should().doFilter(request, response);
		then(response).should(never()).setStatus(anyInt());
	}

	@Test
	void craftedSuffixApiDocsPathIsNotDocAndRequiresAuth() throws Exception {
		StringWriter body = new StringWriter();
		// Regression: a downstream path merely ENDING in /v3/api-docs must NOT bypass auth.
		// Only the exact per-service doc paths (/<service>/v3/api-docs) are exempt, not an
		// arbitrary /<service>/.../v3/api-docs suffix.
		HttpServletRequest request = request("GET", "/tweet-service/tweets/search/v3/api-docs", null);
		HttpServletResponse response = response(body);
		FilterChain chain = mock(FilterChain.class);

		this.filter.doFilterInternal(request, response, chain);

		then(response).should().setStatus(HttpStatus.UNAUTHORIZED.value());
		then(chain).should(never()).doFilter(any(), any());
	}

	@Test
	void trailingSlashOnPublicPathStillPassesThrough() throws Exception {
		// isPublicPath strips a single trailing slash before the exact-match check.
		HttpServletRequest request = request("POST", "/user-service/auth/login/", null);
		HttpServletResponse response = mock(HttpServletResponse.class);
		FilterChain chain = mock(FilterChain.class);

		this.filter.doFilterInternal(request, response, chain);

		then(chain).should().doFilter(request, response);
		then(response).should(never()).setStatus(anyInt());
	}

	@Test
	void nonBearerAuthorizationHeaderIsTreatedAsMissingToken() throws Exception {
		StringWriter body = new StringWriter();
		// A present-but-non-Bearer scheme is not a token: extractJwtFromRequest returns null.
		HttpServletRequest request = request("GET", "/user-service/users/u1", "Basic dXNlcjpwYXNz");
		HttpServletResponse response = response(body);
		FilterChain chain = mock(FilterChain.class);

		this.filter.doFilterInternal(request, response, chain);

		then(response).should().setStatus(HttpStatus.UNAUTHORIZED.value());
		then(response).should().setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		then(chain).should(never()).doFilter(any(), any());
	}

	@Test
	void nullRequestUriIsNotPublicOrDocAndRequiresAuth() throws Exception {
		StringWriter body = new StringWriter();
		// Defensive null guards: a null URI is neither management, public nor a doc path,
		// so the request falls through to JWT extraction and is rejected for a missing token.
		HttpServletRequest request = request("GET", null, null);
		HttpServletResponse response = response(body);
		FilterChain chain = mock(FilterChain.class);

		this.filter.doFilterInternal(request, response, chain);

		then(response).should().setStatus(HttpStatus.UNAUTHORIZED.value());
		then(chain).should(never()).doFilter(any(), any());
	}

	private static HttpServletRequest request(String method, String uri, String authHeader) {
		HttpServletRequest request = mock(HttpServletRequest.class);
		given(request.getRequestURI()).willReturn(uri);
		given(request.getMethod()).willReturn(method);
		given(request.getHeader(HttpHeaders.AUTHORIZATION)).willReturn(authHeader);
		return request;
	}

	private static HttpServletRequest preflightRequest(String uri) {
		HttpServletRequest request = mock(HttpServletRequest.class);
		given(request.getRequestURI()).willReturn(uri);
		given(request.getMethod()).willReturn("OPTIONS");
		given(request.getHeader(HttpHeaders.ORIGIN)).willReturn("https://app.example.com");
		given(request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD)).willReturn("PUT");
		return request;
	}

	private static HttpServletResponse response(StringWriter sink) throws Exception {
		HttpServletResponse response = mock(HttpServletResponse.class);
		given(response.getWriter()).willReturn(new PrintWriter(sink));
		return response;
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

	private static Date past() {
		return new Date(System.currentTimeMillis() - 3_600_000L);
	}

}
