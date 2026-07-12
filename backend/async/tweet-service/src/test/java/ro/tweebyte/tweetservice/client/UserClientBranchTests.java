/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.client;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Covers {@code mediaExists} (both body branches) and the Resilience4j (W6) fallback
 * methods, which {@link UserClientTests} does not exercise.
 */
class UserClientBranchTests {

	private static final String BASE_URL = "http://localhost/";

	private ExecutorService executor;

	private MockRestServiceServer server;

	private UserClient userClient;

	@BeforeEach
	void setUp() {
		this.executor = Executors.newSingleThreadExecutor();
		RestClient.Builder builder = RestClient.builder();
		this.server = MockRestServiceServer.bindTo(builder).build();
		this.userClient = new UserClient(this.executor, builder);
		ReflectionTestUtils.setField(this.userClient, "baseUrl", BASE_URL);
		ReflectionTestUtils.invokeMethod(this.userClient, "init");
	}

	@AfterEach
	void tearDown() {
		this.executor.shutdownNow();
	}

	@Test
	void mediaExists_trueWhenBodyExistsTrue() throws Exception {
		UUID mediaId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "media/" + mediaId + "/exists"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("{\"exists\":true}", MediaType.APPLICATION_JSON));

		assertThat(this.userClient.mediaExists(mediaId).get()).isTrue();
	}

	@Test
	void mediaExists_falseWhenBodyExistsFalse() throws Exception {
		UUID mediaId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "media/" + mediaId + "/exists"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("{\"exists\":false}", MediaType.APPLICATION_JSON));

		assertThat(this.userClient.mediaExists(mediaId).get()).isFalse();
	}

	@Test
	void mediaExists_falseWhenBodyMissing() throws Exception {
		// user-service never 404s for unknown ids; an empty body resolves to false
		// via the `body != null` guard in mediaExists.
		UUID mediaId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "media/" + mediaId + "/exists"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		assertThat(this.userClient.mediaExists(mediaId).get()).isFalse();
	}

	@Test
	void mediaExists_falseWhenBodyNull() throws Exception {
		// A 204 No Content makes RestClient.body(...) return null, so the `body != null`
		// guard short-circuits to false (the null-body arm distinct from the empty-{} body).
		UUID mediaId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "media/" + mediaId + "/exists"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withStatus(HttpStatus.NO_CONTENT));

		assertThat(this.userClient.mediaExists(mediaId).get()).isFalse();
	}

	@Test
	void mediaExists_exceptionThrown() {
		UUID mediaId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "media/" + mediaId + "/exists")).andRespond(withServerError());

		Throwable ex = catchThrowable(() -> this.userClient.mediaExists(mediaId).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(HttpServerErrorException.class);
	}

	// ---------- W6 Resilience4j fallback methods ----------

	@Test
	void getUserSummaryFallbackByName_propagatesThrowable() {
		RuntimeException boom = new RuntimeException("boom");
		assertFailsWith(this.userClient.getUserSummaryFallback("name", boom), boom);
	}

	@Test
	void getUserSummaryFallbackById_propagatesThrowable() {
		RuntimeException boom = new RuntimeException("boom");
		assertFailsWith(this.userClient.getUserSummaryFallback(UUID.randomUUID(), boom), boom);
	}

	@Test
	void mediaExistsFallback_propagatesThrowable() {
		RuntimeException boom = new RuntimeException("boom");
		assertFailsWith(this.userClient.mediaExistsFallback(UUID.randomUUID(), boom), boom);
	}

	private static void assertFailsWith(CompletableFuture<?> future, Throwable expected) {
		assertThat(future).isCompletedExceptionally();
		Throwable ex = catchThrowable(future::get);
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isSameAs(expected);
	}

}
