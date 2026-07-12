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
import org.springframework.web.client.RestClient;

import ro.tweebyte.userservice.exception.FollowRetrievingException;
import ro.tweebyte.userservice.model.ProfileInteractionsDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class InteractionClientTests {

	private static final String BASE_URL = "http://localhost";

	private ExecutorService executor;

	private MockRestServiceServer server;

	private InteractionClient interactionClient;

	@BeforeEach
	void setUp() {
		this.executor = Executors.newSingleThreadExecutor();
		RestClient.Builder builder = RestClient.builder();
		this.server = MockRestServiceServer.bindTo(builder).build();
		this.interactionClient = new InteractionClient(this.executor, builder);
		ReflectionTestUtils.setField(this.interactionClient, "baseUrl", BASE_URL);
		ReflectionTestUtils.invokeMethod(this.interactionClient, "init");
	}

	@AfterEach
	void tearDown() {
		this.executor.shutdownNow();
	}

	@Test
	void testGetReferencedMediaIds() throws Exception {
		UUID mediaId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "/media/referenced"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("[\"" + mediaId + "\"]", MediaType.APPLICATION_JSON));

		assertThat(this.interactionClient.getReferencedMediaIds().get()).containsExactly(mediaId);
	}

	@Test
	void testGetProfileInteractionsPostsToCombinedEndpoint() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID tweetId = UUID.randomUUID();
		String body = "{\"follow_counts\":{\"followers\":20,\"following\":10},"
				+ "\"tweet_interactions\":[{\"tweet_id\":\"" + tweetId
				+ "\",\"likes\":5,\"replies\":3,\"retweets\":2,\"top_reply\":null}]}";
		// POST /follows/{userId}/profile-interactions with the tweet-id list as the body.
		this.server.expect(requestTo(BASE_URL + "/follows/" + userId + "/profile-interactions"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().json("[\"" + tweetId + "\"]"))
			.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

		ProfileInteractionsDto result = this.interactionClient.getProfileInteractions(userId, List.of(tweetId)).get();

		assertThat(result.getFollowCounts().getFollowers()).isEqualTo(20L);
		assertThat(result.getFollowCounts().getFollowing()).isEqualTo(10L);
		assertThat(result.getTweetInteractions()).hasSize(1);
		assertThat(result.getTweetInteractions().get(0).getTweetId()).isEqualTo(tweetId);
		assertThat(result.getTweetInteractions().get(0).getLikes()).isEqualTo(5L);
	}

	@Test
	void testGetProfileInteractionsWrapsErrorAsFollowRetrieving() {
		UUID userId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "/follows/" + userId + "/profile-interactions"))
			.andRespond(withServerError());

		Throwable ex = catchThrowable(() -> this.interactionClient.getProfileInteractions(userId, List.of()).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(FollowRetrievingException.class);
	}

	@Test
	void testGetReferencedMediaIdsPropagatesError() {
		this.server.expect(requestTo(BASE_URL + "/media/referenced")).andRespond(withServerError());

		Throwable ex = catchThrowable(() -> this.interactionClient.getReferencedMediaIds().get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
	}

	// --- resilience fallbacks (master-off on the benchmark path, but covered) ---

	@Test
	void getProfileInteractionsFallbackPropagatesTheFailedFuture() {
		IllegalStateException boom = new IllegalStateException("circuit open");

		Throwable ex = catchThrowable(
				() -> this.interactionClient.getProfileInteractionsFallback(UUID.randomUUID(), List.of(), boom).get());

		assertThat(ex).isInstanceOf(ExecutionException.class).hasCause(boom);
	}

	@Test
	void getReferencedMediaIdsFallbackPropagatesTheFailedFuture() {
		IllegalStateException boom = new IllegalStateException("bulkhead full");

		Throwable ex = catchThrowable(() -> this.interactionClient.getReferencedMediaIdsFallback(boom).get());

		assertThat(ex).isInstanceOf(ExecutionException.class).hasCause(boom);
	}

}
