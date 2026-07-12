/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.client;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import ro.tweebyte.tweetservice.exception.FollowRetrievingException;
import ro.tweebyte.tweetservice.model.ReplyDto;
import ro.tweebyte.tweetservice.model.TweetInteractionsDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class InteractionClientTests {

	private static final String BASE_URL = "http://localhost/";

	private ExecutorService executor;

	private MockRestServiceServer server;

	@SuppressWarnings("unchecked")
	private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);

	private InteractionClient interactionClient;

	@BeforeEach
	void setUp() {
		this.executor = Executors.newSingleThreadExecutor();
		RestClient.Builder builder = RestClient.builder();
		this.server = MockRestServiceServer.bindTo(builder).build();
		this.interactionClient = new InteractionClient(this.redisTemplate, new ObjectMapper(), this.executor, builder);
		ReflectionTestUtils.setField(this.interactionClient, "baseUrl", BASE_URL);
		ReflectionTestUtils.invokeMethod(this.interactionClient, "init");
	}

	@AfterEach
	void tearDown() {
		this.executor.shutdownNow();
	}

	@Test
	void getRepliesCount() throws Exception {
		UUID tweetId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "replies/tweet/" + tweetId + "/count"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("42", MediaType.APPLICATION_JSON));

		assertThat(this.interactionClient.getRepliesCount(tweetId).get()).isEqualTo(42L);
	}

	@Test
	void getLikesCount() throws Exception {
		UUID tweetId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "likes/" + tweetId + "/count"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("42", MediaType.APPLICATION_JSON));

		assertThat(this.interactionClient.getLikesCount(tweetId).get()).isEqualTo(42L);
	}

	@Test
	void getRetweetsCount() throws Exception {
		UUID tweetId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "retweets/tweet/" + tweetId + "/count"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("42", MediaType.APPLICATION_JSON));

		assertThat(this.interactionClient.getRetweetsCount(tweetId).get()).isEqualTo(42L);
	}

	@Test
	void getTopReply() throws Exception {
		UUID tweetId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "replies/tweet/" + tweetId + "/top"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		ReplyDto result = this.interactionClient.getTopReply(tweetId).get();

		assertThat(result).isNotNull();
	}

	@Test
	void getRepliesForTweet() throws Exception {
		UUID tweetId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "replies/tweet/" + tweetId))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		List<ReplyDto> result = this.interactionClient.getRepliesForTweet(tweetId).get();

		assertThat(result).isEqualTo(List.of());
	}

	@Test
	void getTweetInteractions_mapsPopulatedBodyByTweetId() throws Exception {
		UUID tweetId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "tweets/interactions"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess("[{\"tweet_id\":\"" + tweetId + "\",\"likes\":5,\"replies\":3,\"retweets\":2}]",
					MediaType.APPLICATION_JSON));

		Map<UUID, TweetInteractionsDto> result = this.interactionClient.getTweetInteractions(List.of(tweetId)).get();

		// Non-null entries → collected into the per-tweet map (the entries != null arm).
		assertThat(result).containsKey(tweetId);
		assertThat(result.get(tweetId).getLikes()).isEqualTo(5L);
		assertThat(result.get(tweetId).getReplies()).isEqualTo(3L);
	}

	@Test
	void getTweetInteractions_returnsEmptyMapOnNullBody() throws Exception {
		// A 204 No Content makes RestClient.body(...) return null → the (entries == null) arm
		// maps to an empty interactions map (matching the reactive empty-body behaviour).
		this.server.expect(requestTo(BASE_URL + "tweets/interactions"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withStatus(HttpStatus.NO_CONTENT));

		Map<UUID, TweetInteractionsDto> result = this.interactionClient
			.getTweetInteractions(List.of(UUID.randomUUID()))
			.get();

		assertThat(result).isEmpty();
	}

	@Test
	void getFollowedIds_nullBodyRaisesFollowRetrievingException() {
		// A 200 with an empty body makes RestClient.body(String.class) return null, tripping the
		// (body == null) guard's IllegalStateException, which the catch wraps as
		// FollowRetrievingException — the cache is never written.
		UUID userId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "follows/" + userId + "/followers/identifiers"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withStatus(HttpStatus.OK));

		Throwable ex = catchThrowable(() -> this.interactionClient.getFollowedIds(userId).get());

		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(FollowRetrievingException.class);
		verifyNoInteractions(this.redisTemplate);
	}

	@Test
	void getFollowedIds() throws Exception {
		UUID userId = UUID.randomUUID();
		ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
		given(this.redisTemplate.opsForValue()).willReturn(valueOps);
		this.server.expect(requestTo(BASE_URL + "follows/" + userId + "/followers/identifiers"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		List<UUID> result = this.interactionClient.getFollowedIds(userId).get();

		assertThat(result).isEqualTo(List.of());
		verify(valueOps).set("followed_cache::" + userId, "[]");
	}

	@Test
	void getRepliesCount_exceptionThrown() {
		UUID tweetId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "replies/tweet/" + tweetId + "/count")).andRespond(withServerError());

		Throwable ex = catchThrowable(() -> this.interactionClient.getRepliesCount(tweetId).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(HttpServerErrorException.class);
	}

	@Test
	void getLikesCount_exceptionThrown() {
		UUID tweetId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "likes/" + tweetId + "/count")).andRespond(withServerError());

		Throwable ex = catchThrowable(() -> this.interactionClient.getLikesCount(tweetId).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(HttpServerErrorException.class);
	}

	@Test
	void getRetweetsCount_exceptionThrown() {
		UUID tweetId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "retweets/tweet/" + tweetId + "/count")).andRespond(withServerError());

		Throwable ex = catchThrowable(() -> this.interactionClient.getRetweetsCount(tweetId).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(HttpServerErrorException.class);
	}

	@Test
	void getTopReply_exceptionThrown() {
		UUID tweetId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "replies/tweet/" + tweetId + "/top")).andRespond(withServerError());

		Throwable ex = catchThrowable(() -> this.interactionClient.getTopReply(tweetId).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(HttpServerErrorException.class);
	}

	@Test
	void getRepliesForTweet_exceptionThrown() {
		UUID tweetId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "replies/tweet/" + tweetId)).andRespond(withServerError());

		Throwable ex = catchThrowable(() -> this.interactionClient.getRepliesForTweet(tweetId).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(HttpServerErrorException.class);
	}

	@Test
	void getFollowedIds_exceptionThrown() {
		UUID userId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "follows/" + userId + "/followers/identifiers"))
			.andRespond(withServerError());

		Throwable ex = catchThrowable(() -> this.interactionClient.getFollowedIds(userId).get());
		assertThat(ex).isInstanceOf(ExecutionException.class);
		assertThat(ex.getCause()).isInstanceOf(FollowRetrievingException.class);
	}

	@Test
	void getFollowedIdsSkipsRedisCacheWhenNon200() {
		// A non-2xx response makes RestClient.retrieve() throw, so the followed-ids
		// body is never mirrored into followed_cache (redisTemplate stays untouched).
		UUID userId = UUID.randomUUID();
		this.server.expect(requestTo(BASE_URL + "follows/" + userId + "/followers/identifiers"))
			.andRespond(withServerError());

		assertThatThrownBy(() -> this.interactionClient.getFollowedIds(userId).get())
			.isInstanceOf(ExecutionException.class);
		verifyNoInteractions(this.redisTemplate);
	}

}
