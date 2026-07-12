/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.client;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;

/**
 * Covers the Resilience4j (W6) fallback methods, which the primary
 * {@link InteractionClientTests} does not exercise. Each fallback must surface the
 * originating throwable through a failed {@link java.util.concurrent.CompletableFuture}.
 */
class InteractionClientBranchTests {

	private static final String BASE_URL = "http://localhost/";

	private ExecutorService executor;

	@SuppressWarnings("unchecked")
	private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);

	private InteractionClient interactionClient;

	@BeforeEach
	void setUp() {
		this.executor = Executors.newSingleThreadExecutor();
		RestClient.Builder builder = RestClient.builder();
		this.interactionClient = new InteractionClient(this.redisTemplate, new ObjectMapper(), this.executor, builder);
		ReflectionTestUtils.setField(this.interactionClient, "baseUrl", BASE_URL);
		ReflectionTestUtils.invokeMethod(this.interactionClient, "init");
	}

	@AfterEach
	void tearDown() {
		this.executor.shutdownNow();
	}

	// ---------- W6 Resilience4j fallback methods: each forwards the throwable ----------

	@Test
	void getRepliesCountFallback_propagatesThrowable() {
		RuntimeException boom = new RuntimeException("boom");
		assertFailsWith(this.interactionClient.getRepliesCountFallback(UUID.randomUUID(), boom), boom);
	}

	@Test
	void getLikesCountFallback_propagatesThrowable() {
		RuntimeException boom = new RuntimeException("boom");
		assertFailsWith(this.interactionClient.getLikesCountFallback(UUID.randomUUID(), boom), boom);
	}

	@Test
	void getRetweetsCountFallback_propagatesThrowable() {
		RuntimeException boom = new RuntimeException("boom");
		assertFailsWith(this.interactionClient.getRetweetsCountFallback(UUID.randomUUID(), boom), boom);
	}

	@Test
	void getTopReplyFallback_propagatesThrowable() {
		RuntimeException boom = new RuntimeException("boom");
		assertFailsWith(this.interactionClient.getTopReplyFallback(UUID.randomUUID(), boom), boom);
	}

	@Test
	void getRepliesForTweetFallback_propagatesThrowable() {
		RuntimeException boom = new RuntimeException("boom");
		assertFailsWith(this.interactionClient.getRepliesForTweetFallback(UUID.randomUUID(), boom), boom);
	}

	@Test
	void getFollowedIdsFallback_propagatesThrowable() {
		RuntimeException boom = new RuntimeException("boom");
		assertFailsWith(this.interactionClient.getFollowedIdsFallback(UUID.randomUUID(), boom), boom);
	}

	private static void assertFailsWith(java.util.concurrent.CompletableFuture<?> future, Throwable expected) {
		assertThat(future).isCompletedExceptionally();
		Throwable ex = catchThrowable(future::get);
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isSameAs(expected);
	}

}
