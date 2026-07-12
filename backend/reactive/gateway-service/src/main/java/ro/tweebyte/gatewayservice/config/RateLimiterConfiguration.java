/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.gatewayservice.config;

import java.net.InetSocketAddress;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

/**
 * Supplies the {@link KeyResolver} the per-route {@code RequestRateLimiter} buckets are
 * keyed by. Authenticated callers are bucketed by their bearer-token subject so one user
 * cannot starve another; unauthenticated edge traffic (the public auth routes) falls back
 * to the remote address.
 *
 * @author Andrei Zbarcea
 */
@Configuration
public class RateLimiterConfiguration {

	private static final String ANONYMOUS_KEY = "anonymous";

	@Bean
	public KeyResolver gatewayKeyResolver() {
		return exchange -> Mono.just(resolveKey(exchange.getRequest()));
	}

	private static String resolveKey(ServerHttpRequest request) {
		String subject = subjectFromBearer(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
		if (subject != null) {
			return subject;
		}
		InetSocketAddress remote = request.getRemoteAddress();
		return (remote != null) ? remote.getAddress().getHostAddress() : ANONYMOUS_KEY;
	}

	// Bucket by the unverified token subject: the JwtTokenValidationFilter has already
	// rejected invalid tokens before the rate-limiter filter runs on the route, so a
	// present subject here belongs to an authenticated caller.
	private static String subjectFromBearer(String authorization) {
		if (authorization == null || !authorization.startsWith("Bearer ")) {
			return null;
		}
		String token = authorization.substring(7);
		String[] parts = token.split("\\.");
		if (parts.length != 3) {
			return null;
		}
		try {
			String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]),
					java.nio.charset.StandardCharsets.UTF_8);
			int subIndex = payload.indexOf("\"sub\"");
			if (subIndex < 0) {
				return null;
			}
			int colon = payload.indexOf(':', subIndex);
			int firstQuote = payload.indexOf('"', colon + 1);
			int secondQuote = payload.indexOf('"', firstQuote + 1);
			if (firstQuote < 0 || secondQuote < 0) {
				return null;
			}
			return payload.substring(firstQuote + 1, secondQuote);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

}
