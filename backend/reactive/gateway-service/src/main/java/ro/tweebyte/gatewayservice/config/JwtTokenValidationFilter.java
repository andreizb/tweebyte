/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.gatewayservice.config;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Set;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Authenticates every proxied request: verifies the Keycloak-issued RS256 JWT against the
 * realm's JWKS (resolved by {@code kid} through a cached {@link JwkProvider}) and then
 * enforces edge-level resource ownership ({@link OwnershipRuleSet}). Runs as a Spring Cloud
 * Gateway {@link GlobalFilter} ahead of the routing filters, so an unauthenticated or
 * cross-owner request never reaches a downstream service through the gateway.
 *
 * @author Andrei Zbarcea
 */
@Component
public class JwtTokenValidationFilter implements GlobalFilter, Ordered {

	private static final Logger log = LoggerFactory.getLogger(JwtTokenValidationFilter.class);

	// Exact public paths (full gateway URI). Suffix matching would let any path
	// ending in /login or /register slip past auth (e.g.
	// /tweet-service/tweets/search/login).
	private static final Set<String> PUBLIC_PATHS = Set.of("/user-service/auth/login", "/user-service/auth/register");

	// The per-route specs the Swagger portal aggregates: each downstream service exposes its
	// OpenAPI doc at exactly /<service>/v3/api-docs (springdoc.swagger-ui.urls in
	// application.properties). Matched exactly — not by an endsWith suffix — so a path like
	// /tweet-service/tweets/search/v3/api-docs is NOT treated as a doc path and still requires
	// auth + ownership. Root /v3/api-docs and /swagger-ui assets are handled by WebFlux before
	// Spring Cloud Gateway routing, so this GlobalFilter only needs downstream route docs.
	private static final Set<String> SERVICE_DOC_PATHS = Set.of("/user-service/v3/api-docs",
			"/tweet-service/v3/api-docs", "/interaction-service/v3/api-docs");

	private static final String UNAUTHORIZED_BODY = "{\"error\":\"Unauthorized\"}";

	private static final String FORBIDDEN_BODY = "{\"error\":\"Forbidden\"}";

	// Ownership is keyed off the local profile UUID Keycloak emits as a custom claim
	// (realm protocol mapper), not the opaque Keycloak subject.
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
	public int getOrder() {
		return -100;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		ServerHttpRequest request = exchange.getRequest();
		String requestPath = request.getURI().getPath();

		if (isPublicPath(requestPath) || isDocPath(requestPath)) {
			return chain.filter(exchange);
		}

		String jwtToken = extractJwtFromRequest(request);
		if (jwtToken == null) {
			return reject(exchange, HttpStatus.UNAUTHORIZED, "Missing JWT Token", UNAUTHORIZED_BODY);
		}

		DecodedJWT jwt = verify(jwtToken);
		if (jwt == null) {
			return reject(exchange, HttpStatus.UNAUTHORIZED, "Invalid JWT Token", UNAUTHORIZED_BODY);
		}

		String requiredOwner = this.ownershipRules.requiredOwner(request.getMethod().name(), requestPath).orElse(null);
		if (requiredOwner != null && !requiredOwner.equals(jwt.getClaim(OWNER_CLAIM).asString())) {
			return reject(exchange, HttpStatus.FORBIDDEN, "Ownership mismatch", FORBIDDEN_BODY);
		}

		return chain.filter(exchange);
	}

	// Resolves the realm's RS256 signing key by the token's kid through the cached
	// JwkProvider, then verifies signature + issuer. A cache hit is an in-memory lookup,
	// so verification runs inline on the event loop rather than hopping to a scheduler —
	// matching the async gateway, which verifies on the servlet thread with no thread
	// switch. Only a cold cache (or key rotation) touches the network, once.
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

	private static boolean isPublicPath(String path) {
		if (path == null) {
			return false;
		}
		String p = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
		return PUBLIC_PATHS.contains(p);
	}

	private static boolean isDocPath(String path) {
		return path != null && SERVICE_DOC_PATHS.contains(path);
	}

	private static String extractJwtFromRequest(ServerHttpRequest request) {
		List<String> headers = request.getHeaders().getOrEmpty("Authorization");
		for (String header : headers) {
			if (header.startsWith("Bearer ")) {
				return header.substring(7);
			}
		}
		return null;
	}

	private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String reason, String body) {
		ServerHttpRequest request = exchange.getRequest();
		audit(status, request.getMethod().name(), request.getURI().getPath(), reason);
		ServerHttpResponse response = exchange.getResponse();
		response.setStatusCode(status);
		response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		if (status == HttpStatus.UNAUTHORIZED) {
			response.getHeaders().add(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		}
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		DataBuffer buffer = response.bufferFactory().wrap(bytes);
		return response.writeWith(Mono.just(buffer));
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
