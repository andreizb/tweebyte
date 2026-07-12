/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.client;

import java.util.List;
import java.util.UUID;
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
import org.springframework.web.client.RestClient;

import ro.tweebyte.interactionservice.exception.InteractionException;
import ro.tweebyte.interactionservice.exception.TweetNotFoundException;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.TweetSummaryDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TweetClientTests {

	private static final String BASE_URL = "http://localhost/";

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
	void testGetTweetSummary() throws Exception {
		UUID tweetId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "tweets/" + tweetId + "/summary"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		TweetDto result = this.tweetClient.getTweetSummary(tweetId).get();

		assertThat(result).isNotNull();
	}

	@Test
	void testGetTweetSummaryNotFound() {
		UUID tweetId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "tweets/" + tweetId + "/summary"))
			.andRespond(withStatus(HttpStatus.NOT_FOUND));

		Throwable ex = catchThrowable(() -> this.tweetClient.getTweetSummary(tweetId).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(TweetNotFoundException.class);
	}

	@Test
	void testGetTweetSummaryOtherError() {
		UUID tweetId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "tweets/" + tweetId + "/summary"))
			.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

		Throwable ex = catchThrowable(() -> this.tweetClient.getTweetSummary(tweetId).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(InteractionException.class);
	}

	@Test
	void testGetTweetSummaryParseError() {
		UUID tweetId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "tweets/" + tweetId + "/summary"))
			.andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

		Throwable ex = catchThrowable(() -> this.tweetClient.getTweetSummary(tweetId).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(InteractionException.class);
	}

	@Test
	void testGetUserTweetsSummary() throws Exception {
		UUID userId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "tweets/user/" + userId + "/summary"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		List<TweetSummaryDto> result = this.tweetClient.getUserTweetsSummary(userId).get();

		assertThat(result).isEqualTo(List.of());
	}

	@Test
	void testGetUserTweetsSummaryNotFound() {
		UUID userId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "tweets/user/" + userId + "/summary"))
			.andRespond(withStatus(HttpStatus.NOT_FOUND));

		Throwable ex = catchThrowable(() -> this.tweetClient.getUserTweetsSummary(userId).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(TweetNotFoundException.class);
	}

	@Test
	void testGetUserTweetsSummaryOtherError() {
		UUID userId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "tweets/user/" + userId + "/summary"))
			.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

		Throwable ex = catchThrowable(() -> this.tweetClient.getUserTweetsSummary(userId).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(InteractionException.class);
	}

	@Test
	void testGetUserTweetsSummaryParseError() {
		// A 200 with a malformed body is not a RestClientResponseException, so it falls through
		// to the generic catch and wraps as InteractionException.
		UUID userId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "tweets/user/" + userId + "/summary"))
			.andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

		Throwable ex = catchThrowable(() -> this.tweetClient.getUserTweetsSummary(userId).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(InteractionException.class);
	}

	@Test
	void testGetPopularHashtags() throws Exception {
		this.server.expect(requestTo(BASE_URL + "tweets/hashtag/popular"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		List<TweetDto.HashtagDto> result = this.tweetClient.getPopularHashtags().get();

		assertThat(result).isEqualTo(List.of());
	}

	@Test
	void testGetPopularHashtagsError() {
		this.server.expect(requestTo(BASE_URL + "tweets/hashtag/popular"))
			.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

		Throwable ex = catchThrowable(() -> this.tweetClient.getPopularHashtags().get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(InteractionException.class);
	}

	@Test
	void testGetPopularHashtagsNotFound() {
		// 404 takes the NOT_FOUND arm, surfacing TweetNotFoundException rather than a
		// generic InteractionException.
		this.server.expect(requestTo(BASE_URL + "tweets/hashtag/popular")).andRespond(withStatus(HttpStatus.NOT_FOUND));

		Throwable ex = catchThrowable(() -> this.tweetClient.getPopularHashtags().get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(TweetNotFoundException.class);
	}

	@Test
	void testGetPopularHashtagsParseError() {
		// A 200 with a malformed body falls through to the generic catch (logged) and wraps as
		// InteractionException — distinct from the 404 TweetNotFoundException arm.
		this.server.expect(requestTo(BASE_URL + "tweets/hashtag/popular"))
			.andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

		Throwable ex = catchThrowable(() -> this.tweetClient.getPopularHashtags().get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(InteractionException.class);
	}

	@Test
	void getTweetSummaryFallback_PropagatesThrowable() {
		RuntimeException cause = new RuntimeException("circuit open");
		Throwable ex = catchThrowable(() -> this.tweetClient.getTweetSummaryFallback(UUID.randomUUID(), cause).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isEqualTo(cause);
	}

	@Test
	void getUserTweetsSummaryFallback_PropagatesThrowable() {
		RuntimeException cause = new RuntimeException("bulkhead full");
		Throwable ex = catchThrowable(
				() -> this.tweetClient.getUserTweetsSummaryFallback(UUID.randomUUID(), cause).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isEqualTo(cause);
	}

	@Test
	void getPopularHashtagsFallback_PropagatesThrowable() {
		RuntimeException cause = new RuntimeException("timeout");
		Throwable ex = catchThrowable(() -> this.tweetClient.getPopularHashtagsFallback(cause).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isEqualTo(cause);
	}

}
