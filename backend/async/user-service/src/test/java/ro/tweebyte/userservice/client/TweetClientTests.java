/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.client;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import ro.tweebyte.userservice.model.TweetDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TweetClientTests {

	private static final String BASE_URL = "http://localhost";

	private ExecutorService executor;

	private MockRestServiceServer server;

	private TweetClient tweetClient;

	@BeforeEach
	void setUp() {
		this.executor = Executors.newSingleThreadExecutor();
		RestClient.Builder builder = RestClient.builder();
		this.server = MockRestServiceServer.bindTo(builder).build();
		this.tweetClient = new TweetClient(this.executor, builder);
		ReflectionTestUtils.setField(this.tweetClient, "baseUrl", BASE_URL);
		ReflectionTestUtils.invokeMethod(this.tweetClient, "init");
	}

	@AfterEach
	void tearDown() {
		this.executor.shutdownNow();
	}

	@Test
	void testGetUserProfileTweetsRequestsUnenriched() throws Exception {
		UUID userId = UUID.randomUUID();
		// The profile read must request the page UN-enriched: /tweets/user/{id}?enrich=false.
		this.server.expect(requestTo(BASE_URL + "/tweets/user/" + userId + "?enrich=false"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		List<TweetDto> result = this.tweetClient.getUserProfileTweets(userId).get();

		assertThat(result).isEqualTo(List.of());
	}

	@Test
	void testGetUserProfileTweetsPropagatesError() {
		UUID userId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "/tweets/user/" + userId + "?enrich=false"))
			.andRespond(withServerError());

		Throwable ex = catchThrowable(() -> this.tweetClient.getUserProfileTweets(userId).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(HttpServerErrorException.class);
	}

	@Test
	void testGetReferencedMediaIds() throws Exception {
		UUID mediaId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "/tweets/media/referenced"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("[\"" + mediaId + "\"]", MediaType.APPLICATION_JSON));

		assertThat(this.tweetClient.getReferencedMediaIds().get()).containsExactly(mediaId);
	}

	@Test
	void testGetReferencedMediaIdsPropagatesError() {
		this.server.expect(requestTo(BASE_URL + "/tweets/media/referenced")).andRespond(withServerError());

		Throwable ex = catchThrowable(() -> this.tweetClient.getReferencedMediaIds().get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(HttpServerErrorException.class);
	}

	// --- resilience fallbacks (master-off on the benchmark path, but covered) ---

	@Test
	void getUserProfileTweetsFallbackPropagatesTheFailedFuture() {
		IllegalStateException boom = new IllegalStateException("circuit open");

		Throwable ex = catchThrowable(
				() -> this.tweetClient.getUserProfileTweetsFallback(UUID.randomUUID(), boom).get());

		assertThat(ex).isInstanceOf(ExecutionException.class).hasCause(boom);
	}

	@Test
	void getReferencedMediaIdsFallbackPropagatesTheFailedFuture() {
		IllegalStateException boom = new IllegalStateException("bulkhead full");

		Throwable ex = catchThrowable(() -> this.tweetClient.getReferencedMediaIdsFallback(boom).get());

		assertThat(ex).isInstanceOf(ExecutionException.class).hasCause(boom);
	}

}
