/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.gatewayservice.config;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Spring Cloud Gateway Server MVC routes: one proxy route per downstream service.
 * Each strips the {@code /<service>} prefix and the Cookie/Set-Cookie headers, applies an
 * in-process Bucket4j token-bucket rate limit and stamps the edge security headers, mirroring
 * the reactive SCG gateway. Downstream URIs are env-var overridable so the same image
 * runs against localhost or container hostnames; Authorization is forwarded untouched.
 *
 * @author Andrei Zbarcea
 */
@Configuration
public class GatewayRoutesConfiguration {

	// Security response headers mirrored from the reactive SecureHeaders default filter.
	private static final String HSTS = "max-age=31536000; includeSubDomains";

	private static final String CSP = "default-src 'self'; frame-ancestors 'none'";

	private static final String REFERRER_POLICY = "no-referrer";

	// Bucket key for traffic with neither a bearer subject nor a resolvable remote
	// address. Mirrors the reactive RateLimiterConfiguration so a null remote address can
	// never reach the bucket map's computeIfAbsent lookup, which rejects null keys, as a 500.
	private static final String ANONYMOUS_KEY = "anonymous";

	private final long rateLimitCapacity;

	private final long rateLimitRefillTokens;

	private final Duration rateLimitRefillPeriod;

	// One in-process token bucket per resolved caller, bounded by the active caller set.
	// The gateway is a single instance off the benchmark path, so a per-instance bucket is
	// the correct scope (the reactive stack scopes per-caller in Redis instead).
	private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

	GatewayRoutesConfiguration(@Value("${app.gateway.rate-limit.capacity}") long rateLimitCapacity,
			@Value("${app.gateway.rate-limit.refill-tokens}") long rateLimitRefillTokens,
			@Value("${app.gateway.rate-limit.refill-period-seconds}") long rateLimitRefillPeriodSeconds) {
		this.rateLimitCapacity = rateLimitCapacity;
		this.rateLimitRefillTokens = rateLimitRefillTokens;
		this.rateLimitRefillPeriod = Duration.ofSeconds(rateLimitRefillPeriodSeconds);
	}

	@Bean
	public RouterFunction<ServerResponse> userServiceRoute(@Value("${app.gateway.user-service-uri}") String uri) {
		return proxyRoute("user-service", "/user-service/**", uri);
	}

	@Bean
	public RouterFunction<ServerResponse> tweetServiceRoute(@Value("${app.gateway.tweet-service-uri}") String uri) {
		return proxyRoute("tweet-service", "/tweet-service/**", uri);
	}

	@Bean
	public RouterFunction<ServerResponse> interactionServiceRoute(
			@Value("${app.gateway.interaction-service-uri}") String uri) {
		return proxyRoute("interaction-service", "/interaction-service/**", uri);
	}

	private RouterFunction<ServerResponse> proxyRoute(String routeId, String pathPattern, String uri) {
		return GatewayRouterFunctions.route(routeId)
			.route(RequestPredicates.path(pathPattern), HandlerFunctions.http(stripTrailingSlash(uri)))
			.before(stripServicePrefix())
			.before(BeforeFilterFunctions.removeRequestHeader(HttpHeaders.COOKIE))
			.filter(rateLimit())
			.after(AfterFilterFunctions.removeResponseHeader(HttpHeaders.SET_COOKIE))
			.after(AfterFilterFunctions.setResponseHeader("Strict-Transport-Security", HSTS))
			.after(AfterFilterFunctions.setResponseHeader("Content-Security-Policy", CSP))
			.after(AfterFilterFunctions.setResponseHeader("X-Content-Type-Options", "nosniff"))
			.after(AfterFilterFunctions.setResponseHeader("X-Frame-Options", "DENY"))
			.after(AfterFilterFunctions.setResponseHeader("Referrer-Policy", REFERRER_POLICY))
			.build();
	}

	// Strips the leading /<service> segment, preserving the raw (percent-encoded) path.
	// The framework BeforeFilterFunctions.stripPrefix rebuilds the URI with the default
	// UriComponentsBuilder.build() (encoded=false), which re-encodes the rejoined raw
	// segments and so double-encodes any %-escape (e.g. a unicode path /café → /caf%C3%A9
	// becomes /caf%25C3%25A9), yielding a 400 downstream. Tokenising the raw path and
	// rebuilding with build(true) keeps the existing encoding intact, matching the reactive
	// gateway (which strips the prefix on the raw path without re-encoding).
	static Function<ServerRequest, ServerRequest> stripServicePrefix() {
		return request -> {
			URI uri = request.uri();
			String[] segments = StringUtils.tokenizeToStringArray(uri.getRawPath(), "/");
			StringBuilder newRawPath = new StringBuilder();
			for (int i = 1; i < segments.length; i++) {
				newRawPath.append('/').append(segments[i]);
			}
			URI rewritten = UriComponentsBuilder.fromUri(uri)
				.replacePath(newRawPath.toString())
				.build(true)
				.toUri();
			return ServerRequest.from(request).uri(rewritten).build();
		};
	}

	// In-process token-bucket rate limit. SCG-MVC's Bucket4jFilterFunctions is built around a
	// distributed bucket4j AsyncProxyManager (Redis/JCache) that this single-instance gateway
	// does not run, so the limit is enforced directly with bucket4j-core: one Bucket per
	// resolved caller, a 429 when the caller's bucket is exhausted. Behaviour mirrors the
	// reactive RedisRateLimiter (same capacity / refill, keyed by caller).
	private HandlerFilterFunction<ServerResponse, ServerResponse> rateLimit() {
		Function<ServerRequest, String> keyResolver = rateLimitKeyResolver();
		return (request, next) -> {
			Bucket bucket = this.buckets.computeIfAbsent(keyResolver.apply(request),
					ignored -> Bucket.builder().addLimit(bandwidth()).build());
			if (bucket.tryConsume(1)) {
				return next.handle(request);
			}
			return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS).build();
		};
	}

	// Decouple burst (capacity) from steady rate (refillGreedy), mirroring the reactive
	// RedisRateLimiter's burst-capacity / replenish-rate split.
	private Bandwidth bandwidth() {
		return Bandwidth.builder()
			.capacity(this.rateLimitCapacity)
			.refillGreedy(this.rateLimitRefillTokens, this.rateLimitRefillPeriod)
			.build();
	}

	// Bucket by JWT subject so one caller cannot starve another; fall back to the remote
	// address for unauthenticated edge traffic (the public auth routes).
	private static Function<ServerRequest, String> rateLimitKeyResolver() {
		return request -> {
			String subject = subjectFromBearer(request.headers().firstHeader(HttpHeaders.AUTHORIZATION));
			if (subject != null) {
				return subject;
			}
			String remote = request.servletRequest().getRemoteAddr();
			return (remote != null) ? remote : ANONYMOUS_KEY;
		};
	}

	// The JwtTokenValidationFilter runs ahead of routing and has already rejected invalid
	// tokens, so a present subject here belongs to an authenticated caller.
	private static String subjectFromBearer(String authorization) {
		if (authorization == null || !authorization.startsWith("Bearer ")) {
			return null;
		}
		String[] parts = authorization.substring(7).split("\\.");
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

	// The proxy appends the (prefix-stripped) request path to the route URI's authority,
	// so a trailing slash on the configured base would double the separator.
	private static String stripTrailingSlash(String uri) {
		return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
	}

}
