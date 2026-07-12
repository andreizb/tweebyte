/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.gatewayservice.config;

import java.io.IOException;
import java.security.interfaces.RSAPublicKey;
import java.util.Set;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates every proxied request: verifies the RSA-signed JWT and then enforces
 * edge-level resource ownership ({@link OwnershipRuleSet}). Runs as a servlet filter
 * ahead of the Spring Cloud Gateway Server MVC routing, so an unauthenticated or
 * cross-owner request never reaches a downstream service through the gateway.
 *
 * @author Andrei Zbarcea
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JwtTokenValidationFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(JwtTokenValidationFilter.class);

	// Exact public paths (full gateway URI). Suffix matching would let any path
	// ending in /login or /register slip past auth (e.g.
	// /tweet-service/tweets/search/login).
	private static final Set<String> PUBLIC_PATHS = Set.of("/user-service/auth/login", "/user-service/auth/register");

	// Unified Swagger portal at the edge: the gateway's own springdoc resources
	// (aggregated /v3/api-docs and the /swagger-ui assets) are served unauthenticated under
	// these root prefixes. Constrained to root-level prefixes — never a /<service>/... path —
	// so a crafted downstream path cannot reach a doc route (see isDocPath).
	private static final Set<String> DOC_PATH_PREFIXES = Set.of("/v3/api-docs", "/swagger-ui");

	// The per-route specs the Swagger portal aggregates: each downstream service exposes its
	// OpenAPI doc at exactly /<service>/v3/api-docs (springdoc.swagger-ui.urls in
	// application.properties). Matched exactly — not by an endsWith suffix — so a path like
	// /tweet-service/tweets/search/v3/api-docs is NOT treated as a doc path and still
	// requires auth + ownership, mirroring the exact-match hardening on PUBLIC_PATHS.
	private static final Set<String> SERVICE_DOC_PATHS = Set.of("/user-service/v3/api-docs",
			"/tweet-service/v3/api-docs", "/interaction-service/v3/api-docs");

	private static final String SWAGGER_UI_HTML = "/swagger-ui.html";

	private static final String UNAUTHORIZED_BODY = "{\"error\":\"Unauthorized\"}";

	private static final String FORBIDDEN_BODY = "{\"error\":\"Forbidden\"}";

	// Keycloak emits the local profile UUID under this claim via a realm protocol mapper.
	// Edge ownership is enforced against it, not the token subject (the Keycloak user id).
	private static final String OWNER_CLAIM = "user_id";

	private final JwkProvider jwkProvider;

	private final OwnershipRuleSet ownershipRules;

	private final String issuer;

	JwtTokenValidationFilter(JwkProvider jwkProvider, OwnershipRuleSet ownershipRules,
			@Value("${app.keycloak.issuer-uri}") String issuer) {
		this.jwkProvider = jwkProvider;
		this.ownershipRules = ownershipRules;
		this.issuer = issuer;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String path = request.getRequestURI();

		// CORS preflight (OPTIONS + Origin + Access-Control-Request-Method) carries no
		// Authorization header by spec; let it fall through to the CorsFilter, which answers
		// it. This filter runs at HIGHEST_PRECEDENCE, ahead of the unordered CorsFilter, so
		// without this carve-out a preflight would be rejected 401 and the browser would block
		// the real cross-origin call. isPreFlightRequest matches only genuine preflights, never
		// a GET/POST/PUT/DELETE, so no real verb skips auth. The reactive gateway answers
		// preflight in Spring Cloud Gateway's CORS handling before the JWT filter runs, so this
		// keeps both stacks behaving identically on preflight.
		if (CorsUtils.isPreFlightRequest(request)) {
			filterChain.doFilter(request, response);
			return;
		}

		// Local management endpoints (actuator, scraped by Prometheus), the unified Swagger
		// portal and public auth routes carry no JWT; everything proxied downstream must.
		if (isLocalManagementPath(path) || isPublicPath(path) || isDocPath(path)) {
			filterChain.doFilter(request, response);
			return;
		}

		String token = extractJwtFromRequest(request);
		if (token == null) {
			reject(request, response, HttpStatus.UNAUTHORIZED, "Missing JWT Token", UNAUTHORIZED_BODY);
			return;
		}

		DecodedJWT jwt = verify(token);
		if (jwt == null) {
			reject(request, response, HttpStatus.UNAUTHORIZED, "Invalid JWT Token", UNAUTHORIZED_BODY);
			return;
		}

		String requiredOwner = this.ownershipRules.requiredOwner(request.getMethod(), path).orElse(null);
		if (requiredOwner != null && !requiredOwner.equals(jwt.getClaim(OWNER_CLAIM).asString())) {
			reject(request, response, HttpStatus.FORBIDDEN, "Ownership mismatch", FORBIDDEN_BODY);
			return;
		}

		filterChain.doFilter(request, response);
	}

	private DecodedJWT verify(String token) {
		try {
			DecodedJWT decoded = JWT.decode(token);
			Jwk jwk = this.jwkProvider.get(decoded.getKeyId());
			Algorithm algorithm = Algorithm.RSA256((RSAPublicKey) jwk.getPublicKey(), null);
			JWTVerifier verifier = JWT.require(algorithm).withIssuer(this.issuer).build();
			return verifier.verify(token);
		}
		catch (JwkException | RuntimeException ex) {
			log.warn("JWT verification failed: {}", ex.getMessage());
			return null;
		}
	}

	private static boolean isLocalManagementPath(String uri) {
		return uri != null && uri.startsWith("/actuator");
	}

	private static boolean isPublicPath(String uri) {
		if (uri == null) {
			return false;
		}
		String path = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
		return PUBLIC_PATHS.contains(path);
	}

	private static boolean isDocPath(String uri) {
		if (uri == null) {
			return false;
		}
		String path = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
		if (SWAGGER_UI_HTML.equals(path) || SERVICE_DOC_PATHS.contains(path)) {
			return true;
		}
		for (String prefix : DOC_PATH_PREFIXES) {
			if (path.equals(prefix) || path.startsWith(prefix + "/")) {
				return true;
			}
		}
		return false;
	}

	private static String extractJwtFromRequest(HttpServletRequest request) {
		String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
			return bearerToken.substring(7);
		}
		return null;
	}

	private static void reject(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
			String reason, String body) throws IOException {
		audit(status, request.getMethod(), request.getRequestURI(), reason);
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		if (status == HttpStatus.UNAUTHORIZED) {
			response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		}
		response.getWriter().write(body);
	}

	// Structured security-audit event. MDC keys are serialised as top-level JSON fields
	// by the LogstashEncoder so auth failures and 403 ownership denials are queryable.
	private static void audit(HttpStatus status, String method, String path, String reason) {
		String event = (status == HttpStatus.FORBIDDEN) ? "ownership-denied" : "auth-failed";
		MDC.put("event", event);
		MDC.put("outcome", String.valueOf(status.value()));
		MDC.put("method", method);
		MDC.put("path", path);
		MDC.put("reason", reason);
		try {
			log.warn("Edge security audit: {} {} rejected ({}) — {}", method, path, status.value(), reason);
		}
		finally {
			MDC.remove("event");
			MDC.remove("outcome");
			MDC.remove("method");
			MDC.remove("path");
			MDC.remove("reason");
		}
	}

}
