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
import ro.tweebyte.interactionservice.exception.UserNotFoundException;
import ro.tweebyte.interactionservice.model.UserDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UserClientTests {

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
	void testGetUserSummary() throws Exception {
		UUID userId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "users/summary/" + userId))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		UserDto result = this.userClient.getUserSummary(userId).get();

		assertThat(result).isNotNull();
	}

	@Test
	void testGetUserSummaryNotFound() {
		UUID userId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "users/summary/" + userId)).andRespond(withStatus(HttpStatus.NOT_FOUND));

		Throwable ex = catchThrowable(() -> this.userClient.getUserSummary(userId).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void testGetUserSummaryOtherError() {
		UUID userId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "users/summary/" + userId))
			.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

		Throwable ex = catchThrowable(() -> this.userClient.getUserSummary(userId).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(InteractionException.class);
	}

	@Test
	void testGetUserSummaryParseError() {
		UUID userId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "users/summary/" + userId))
			.andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

		Throwable ex = catchThrowable(() -> this.userClient.getUserSummary(userId).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(InteractionException.class);
	}

	@Test
	void testGetUserSummariesBatch() throws Exception {
		// The batched POST resolves a whole page of cold users in one call; the response is
		// decoded as an array of rows.
		UUID firstId = UUID.randomUUID();
		UUID secondId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "users/summaries"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess("[{\"id\":\"" + firstId + "\",\"user_name\":\"alice\"},{\"id\":\"" + secondId
					+ "\",\"user_name\":\"bob\"}]", MediaType.APPLICATION_JSON));

		List<UserDto> result = this.userClient.getUserSummaries(List.of(firstId, secondId)).get();

		assertThat(result).hasSize(2);
		assertThat(result.get(0).getId()).isEqualTo(firstId);
		assertThat(result.get(1).getUserName()).isEqualTo("bob");
	}

	@Test
	void testGetUserSummariesError() {
		// The batch endpoint never 404s — any transport failure wraps uniformly as
		// InteractionException (there is no per-id not-found case to translate).
		UUID userId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "users/summaries"))
			.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

		Throwable ex = catchThrowable(() -> this.userClient.getUserSummaries(List.of(userId)).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(InteractionException.class);
	}

	@Test
	void getUserSummariesFallback_PropagatesThrowable() {
		RuntimeException cause = new RuntimeException("circuit open");
		Throwable ex = catchThrowable(
				() -> this.userClient.getUserSummariesFallback(List.of(UUID.randomUUID()), cause).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isEqualTo(cause);
	}

	@Test
	void testMediaExistsTrue() throws Exception {
		UUID mediaId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "media/" + mediaId + "/exists"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("{\"exists\":true}", MediaType.APPLICATION_JSON));

		assertThat(this.userClient.mediaExists(mediaId).get()).isTrue();
	}

	@Test
	void testMediaExistsFalse() throws Exception {
		// user-service answers 200 {"exists":false} for unknown ids, so the answer rides
		// on the body — the false arm of Boolean.TRUE.equals.
		UUID mediaId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "media/" + mediaId + "/exists"))
			.andRespond(withSuccess("{\"exists\":false}", MediaType.APPLICATION_JSON));

		assertThat(this.userClient.mediaExists(mediaId).get()).isFalse();
	}

	@Test
	void testMediaExistsError() {
		UUID mediaId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "media/" + mediaId + "/exists"))
			.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

		Throwable ex = catchThrowable(() -> this.userClient.mediaExists(mediaId).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(InteractionException.class);
	}

	@Test
	void getUserSummaryFallback_PropagatesThrowable() {
		RuntimeException cause = new RuntimeException("circuit open");
		Throwable ex = catchThrowable(() -> this.userClient.getUserSummaryFallback(UUID.randomUUID(), cause).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isEqualTo(cause);
	}

	@Test
	void mediaExistsFallback_PropagatesThrowable() {
		RuntimeException cause = new RuntimeException("bulkhead full");
		Throwable ex = catchThrowable(() -> this.userClient.mediaExistsFallback(UUID.randomUUID(), cause).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isEqualTo(cause);
	}

}
