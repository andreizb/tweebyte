/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.gatewayservice.config;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the {@link GatewayRoutesConfiguration} proxy routes through {@link MockMvc} to
 * exercise the in-process Bucket4j rate-limit filter, the JWT-subject / remote-address key
 * resolver and the {@code subjectFromBearer} parser end-to-end. Requests target the public
 * {@code /user-service/auth/login} route so they clear the upstream JWT filter without a
 * verifiable token, yet still flow through the route's rate-limit filter; the downstream
 * URI is an unreachable loopback port, so an admitted request runs the filter's
 * {@code next.handle(...)} path and then fails the proxy I/O (surfacing as a
 * {@link ServletException}), while an exhausted bucket short-circuits with a clean 429.
 */
class GatewayRoutesConfigurationTests {

	private static final String LOGIN_PATH = "/user-service/auth/login";

	// Hand-crafted, unsigned JWT-shaped strings (header.payloadBase64url.sig). The login
	// route bypasses JWT verification, so the rate-limit key resolver still parses these to
	// drive every branch of subjectFromBearer; the value only has to be a distinct bucket key.
	private static String bearerWithPayload(String json) {
		String payload = Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(json.getBytes(StandardCharsets.UTF_8));
		return "Bearer header." + payload + ".signature";
	}

	// True when the request cleared the rate-limit filter: either the filter admitted it and
	// the unreachable downstream failed (ServletException), or a (non-429) status came back.
	// A 429 means the bucket was exhausted, so the request never reached next.handle.
	private static boolean clearedRateLimitFilter(MockMvc mockMvc, RequestBuilder request) throws Exception {
		try {
			int status = mockMvc.perform(request).andReturn().getResponse().getStatus();
			return status != HttpStatus.TOO_MANY_REQUESTS.value();
		}
		catch (ServletException ex) {
			// next.handle reached the downstream proxy and the loopback port refused.
			return true;
		}
	}

	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
	@AutoConfigureMockMvc
	class RouteBeanWiring {

		@Autowired
		private ApplicationContext context;

		@Test
		void registersOneProxyRoutePerDownstreamService() {
			Map<String, RouterFunction> routes = this.context.getBeansOfType(RouterFunction.class);
			assertThat(routes).containsKeys("userServiceRoute", "tweetServiceRoute", "interactionServiceRoute");
		}

	}

	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
	@AutoConfigureMockMvc
	@TestPropertySource(properties = { "app.gateway.rate-limit.capacity=2",
			"app.gateway.rate-limit.refill-tokens=1", "app.gateway.rate-limit.refill-period-seconds=3600" })
	class RateLimitEnforcement {

		@Autowired
		private MockMvc mockMvc;

		@Test
		void allowsUpToCapacityThenReturnsTooManyRequests() throws Exception {
			String bearer = bearerWithPayload("{\"sub\":\"capacity-caller\"}");

			// capacity=2: the first two requests are admitted (run next.handle, then fail the
			// unreachable downstream), the third finds the bucket empty and is throttled.
			for (int i = 0; i < 2; i++) {
				assertThat(clearedRateLimitFilter(this.mockMvc, post(LOGIN_PATH).header(HttpHeaders.AUTHORIZATION,
						bearer)))
					.as("request " + i + " within capacity must clear the rate-limit filter")
					.isTrue();
			}

			this.mockMvc.perform(post(LOGIN_PATH).header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isTooManyRequests());
		}

		@Test
		void bucketsAreScopedPerResolvedCaller() throws Exception {
			String caller = bearerWithPayload("{\"sub\":\"caller-a\"}");
			// Exhaust caller-a's bucket (capacity=2): two admitted then a third throttled.
			clearedRateLimitFilter(this.mockMvc, post(LOGIN_PATH).header(HttpHeaders.AUTHORIZATION, caller));
			clearedRateLimitFilter(this.mockMvc, post(LOGIN_PATH).header(HttpHeaders.AUTHORIZATION, caller));
			this.mockMvc.perform(post(LOGIN_PATH).header(HttpHeaders.AUTHORIZATION, caller))
				.andExpect(status().isTooManyRequests());

			// A different subject gets its own fresh bucket and is not throttled.
			String other = bearerWithPayload("{\"sub\":\"caller-b\"}");
			assertThat(clearedRateLimitFilter(this.mockMvc, post(LOGIN_PATH).header(HttpHeaders.AUTHORIZATION, other)))
				.as("a distinct caller must not share caller-a's exhausted bucket")
				.isTrue();
		}

	}

	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
	@AutoConfigureMockMvc
	class KeyResolverBranches {

		@Autowired
		private MockMvc mockMvc;

		private void expectCleared(RequestBuilder request) throws Exception {
			assertThat(clearedRateLimitFilter(this.mockMvc, request))
				.as("first request for a fresh key must pass the rate-limit filter")
				.isTrue();
		}

		@Test
		void resolvesKeyFromBearerSubject() throws Exception {
			// Valid 3-part token carrying "sub": resolver buckets by the subject.
			expectCleared(get(LOGIN_PATH).header(HttpHeaders.AUTHORIZATION,
					bearerWithPayload("{\"iss\":\"x\",\"sub\":\"resolved-subject\"}")));
		}

		@Test
		void fallsBackToRemoteAddressWhenNoAuthorizationHeader() throws Exception {
			// No Authorization header → resolver falls back to the remote address.
			expectCleared(get(LOGIN_PATH));
		}

		@Test
		void fallsBackToAnonymousKeyWhenRemoteAddressIsNull() throws Exception {
			// No Authorization header and a null remote address (mirrors reactive's
			// ANONYMOUS_KEY guard): the resolver must yield a non-null key so the bucket map's
			// computeIfAbsent does not NPE, and the request still clears the filter.
			expectCleared(get(LOGIN_PATH).with(request -> {
				request.setRemoteAddr(null);
				return request;
			}));
		}

		@Test
		void fallsBackWhenAuthorizationIsNotBearer() throws Exception {
			// Non-Bearer scheme → subjectFromBearer returns null → remote-address fallback.
			expectCleared(get(LOGIN_PATH).header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"));
		}

		@Test
		void fallsBackWhenTokenIsNotThreeParts() throws Exception {
			// Token with the wrong number of dot-separated parts → null subject.
			expectCleared(get(LOGIN_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer onlyonepart"));
		}

		@Test
		void fallsBackWhenSubClaimIsAbsent() throws Exception {
			// Valid 3-part token whose payload has no "sub" claim → null subject.
			expectCleared(get(LOGIN_PATH).header(HttpHeaders.AUTHORIZATION,
					bearerWithPayload("{\"iss\":\"x\",\"aud\":\"y\"}")));
		}

		@Test
		void fallsBackWhenPayloadIsNotValidBase64() throws Exception {
			// Middle segment is not valid base64url → IllegalArgumentException → null subject.
			expectCleared(get(LOGIN_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer header.@@@not-base64@@@.signature"));
		}

		@Test
		void fallsBackWhenSubValueHasNoOpeningQuote() throws Exception {
			// "sub" present but a non-string value: no opening quote after the colon → null.
			expectCleared(get(LOGIN_PATH).header(HttpHeaders.AUTHORIZATION,
					bearerWithPayload("{\"sub\":123}")));
		}

		@Test
		void fallsBackWhenSubValueHasNoClosingQuote() throws Exception {
			// "sub" present with an opening quote but no closing quote → null.
			expectCleared(get(LOGIN_PATH).header(HttpHeaders.AUTHORIZATION,
					bearerWithPayload("{\"sub\":\"unterminated")));
		}

	}

	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
	@AutoConfigureMockMvc
	class AllRoutesProxyThroughTheFilter {

		// Doc paths bypass the JWT filter (unified Swagger portal) yet still match their
		// /<service>/** route, so each downstream route bean's rate-limit filter is exercised.
		private static final String TWEET_DOC_PATH = "/tweet-service/v3/api-docs";

		private static final String INTERACTION_DOC_PATH = "/interaction-service/v3/api-docs";

		@Autowired
		private MockMvc mockMvc;

		@Test
		void tweetServiceRouteProxiesThroughTheRateLimitFilter() throws Exception {
			assertThat(clearedRateLimitFilter(this.mockMvc, get(TWEET_DOC_PATH)))
				.as("the tweet-service route must proxy through its rate-limit filter")
				.isTrue();
		}

		@Test
		void interactionServiceRouteProxiesThroughTheRateLimitFilter() throws Exception {
			assertThat(clearedRateLimitFilter(this.mockMvc, get(INTERACTION_DOC_PATH)))
				.as("the interaction-service route must proxy through its rate-limit filter")
				.isTrue();
		}

	}

	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
	@AutoConfigureMockMvc
	@TestPropertySource(properties = { "app.gateway.rate-limit.capacity=2",
			"app.gateway.rate-limit.refill-tokens=1", "app.gateway.rate-limit.refill-period-seconds=3600" })
	class BucketIsKeyedPerCallerNotPerRoute {

		@Autowired
		private MockMvc mockMvc;

		@Test
		void sameCallerSharesOneBucketAcrossDifferentRoutes() throws Exception {
			// One bucket per resolved caller (the design's stated scope), not per route: the
			// same subject hitting three different routes draws from a single capacity-2
			// bucket. Doc paths are used so the requests bypass JWT verification yet still
			// match their /<service>/** route and run that route's rate-limit filter.
			String bearer = bearerWithPayload("{\"sub\":\"cross-route-caller\"}");
			clearedRateLimitFilter(this.mockMvc,
					get("/user-service/v3/api-docs").header(HttpHeaders.AUTHORIZATION, bearer));
			clearedRateLimitFilter(this.mockMvc,
					get("/tweet-service/v3/api-docs").header(HttpHeaders.AUTHORIZATION, bearer));

			// Third request for the same caller (a third distinct route) is throttled.
			this.mockMvc.perform(get("/interaction-service/v3/api-docs").header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isTooManyRequests());
		}

	}

	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
	@AutoConfigureMockMvc
	@TestPropertySource(properties = "app.gateway.user-service-uri=http://127.0.0.1:65530")
	class RouteUriWithoutTrailingSlash {

		@Autowired
		private MockMvc mockMvc;

		@Test
		void buildsRouteWhenDownstreamUriHasNoTrailingSlash() throws Exception {
			// stripTrailingSlash leaves a slash-less base untouched; the route still proxies
			// (and fails against the unreachable port), so the request clears the filter.
			assertThat(clearedRateLimitFilter(this.mockMvc, get(LOGIN_PATH)))
				.as("a route built from a slash-less downstream URI must still proxy")
				.isTrue();
		}

	}

	@Nested
	class StripServicePrefixPreservesEncoding {

		private static ServerRequest requestFor(String rawUri) {
			URI uri = URI.create("http://localhost:8080" + rawUri);
			MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", uri.getRawPath());
			servletRequest.setScheme("http");
			servletRequest.setServerName("localhost");
			servletRequest.setServerPort(8080);
			servletRequest.setQueryString(uri.getRawQuery());
			return ServerRequest.create(servletRequest, List.of());
		}

		@Test
		void stripsLeadingServiceSegmentWithoutDoubleEncodingUnicode() {
			// A percent-encoded unicode segment (é → %C3%A9) must survive prefix stripping
			// unchanged; the framework stripPrefix would re-encode it to %25C3%25A9 (400).
			ServerRequest stripped = GatewayRoutesConfiguration.stripServicePrefix()
				.apply(requestFor("/tweet-service/tweets/caf%C3%A9"));

			assertThat(stripped.uri().getRawPath())
				.as("the percent-encoded path must be preserved, not double-encoded")
				.isEqualTo("/tweets/caf%C3%A9");
		}

		@Test
		void stripsLeadingServiceSegmentForPlainPath() {
			ServerRequest stripped = GatewayRoutesConfiguration.stripServicePrefix()
				.apply(requestFor("/user-service/users/u1"));

			assertThat(stripped.uri().getRawPath()).isEqualTo("/users/u1");
		}

		@Test
		void preservesAlreadyEncodedQueryAlongsidePath() {
			ServerRequest stripped = GatewayRoutesConfiguration.stripServicePrefix()
				.apply(requestFor("/tweet-service/tweets/search?q=a%20b"));

			assertThat(stripped.uri().getRawPath()).isEqualTo("/tweets/search");
			assertThat(stripped.uri().getRawQuery()).isEqualTo("q=a%20b");
		}

	}

}
