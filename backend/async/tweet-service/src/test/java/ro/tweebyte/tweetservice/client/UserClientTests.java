/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.client;

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

import ro.tweebyte.tweetservice.exception.UserNotFoundException;
import ro.tweebyte.tweetservice.model.UserDto;

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
	void getUserSummaryByUsername() throws Exception {
		UUID id = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "users/summary/name/testuser"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("{\"id\":\"" + id + "\"}", MediaType.APPLICATION_JSON));

		UserDto result = this.userClient.getUserSummary("testuser").get();

		assertThat(result.getId()).isEqualTo(id);
	}

	@Test
	void getUserSummaryByUserId() throws Exception {
		UUID userId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "users/summary/" + userId))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("{\"id\":\"" + userId + "\"}", MediaType.APPLICATION_JSON));

		UserDto result = this.userClient.getUserSummary(userId).get();

		assertThat(result.getId()).isEqualTo(userId);
	}

	@Test
	void getUserSummaryByName_userNotFound() {
		// 4xx maps to UserNotFoundException; 5xx/transport errors propagate as-is
		// (mirrors the reactive client's onStatus(is4xxClientError, ...)).
		this.server.expect(requestTo(BASE_URL + "users/summary/name/missing"))
			.andRespond(withStatus(HttpStatus.NOT_FOUND));

		Throwable ex = catchThrowable(() -> this.userClient.getUserSummary("missing").get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void getUserSummaryById_userNotFound() {
		UUID userId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "users/summary/" + userId))
			.andRespond(withStatus(HttpStatus.NOT_FOUND));

		Throwable ex = catchThrowable(() -> this.userClient.getUserSummary(userId).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void getUserSummaries_batchedPost_decodesArray() throws Exception {
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "users/summaries"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess("[{\"id\":\"" + first + "\"},{\"id\":\"" + second + "\"}]",
					MediaType.APPLICATION_JSON));

		java.util.List<UserDto> result = this.userClient.getUserSummaries(java.util.List.of(first, second)).get();

		assertThat(result).extracting(UserDto::getId).containsExactly(first, second);
	}

	@Test
	void getUserSummaries_missingIdOmittedFromArray() throws Exception {
		UUID present = UUID.randomUUID();
		UUID missing = UUID.randomUUID();
		// The batch endpoint never 404s — an unknown id is simply absent from the array.
		this.server.expect(requestTo(BASE_URL + "users/summaries"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess("[{\"id\":\"" + present + "\"}]", MediaType.APPLICATION_JSON));

		java.util.List<UserDto> result = this.userClient.getUserSummaries(java.util.List.of(present, missing)).get();

		assertThat(result).extracting(UserDto::getId).containsExactly(present);
	}

}
