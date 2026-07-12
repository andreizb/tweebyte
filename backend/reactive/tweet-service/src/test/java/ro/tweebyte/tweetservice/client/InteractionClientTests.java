/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.client;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.tweetservice.exception.TweetException;
import ro.tweebyte.tweetservice.model.ReplyDto;
import ro.tweebyte.tweetservice.model.TweetInteractionsDto;
import ro.tweebyte.tweetservice.model.TweetInteractionsEntryDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InteractionClientTests {

	private InteractionClient interactionClient;

	@Mock
	private WebClient.Builder webClientBuilderMock;

	@Mock
	private WebClient webClientMock;

	@Mock
	private WebClient.RequestHeadersUriSpec requestHeadersUriSpecMock;

	@Mock
	private WebClient.RequestHeadersSpec requestHeadersSpecMock;

	@Mock
	private WebClient.ResponseSpec responseSpecMock;

	@Mock
	private WebClient.RequestBodyUriSpec requestBodyUriSpecMock;

	@Mock
	private WebClient.RequestBodySpec requestBodySpecMock;

	@Mock
	private ReactiveRedisTemplate<String, Object> redisTemplate;

	@Mock
	private ReactiveValueOperations<String, Object> reactiveValueOperations;

	@BeforeEach
	void setUp() {
		this.interactionClient = new InteractionClient(this.redisTemplate, this.webClientBuilderMock);
		ReflectionTestUtils.setField(this.interactionClient, "webClient", this.webClientMock);
	}

	@Test
	void testGetRepliesCount() {
		given(this.webClientMock.get()).willReturn(this.requestHeadersUriSpecMock);
		given(this.requestHeadersUriSpecMock.uri(any(Function.class))).willReturn(this.requestHeadersSpecMock);
		given(this.requestHeadersSpecMock.retrieve()).willReturn(this.responseSpecMock);

		UUID tweetId = UUID.randomUUID();
		Long expectedCount = 10L;

		given(this.responseSpecMock.bodyToMono(Long.class)).willReturn(Mono.just(expectedCount));

		StepVerifier.create(this.interactionClient.getRepliesCount(tweetId)).expectNext(expectedCount).verifyComplete();
	}

	@Test
	void testGetLikesCount() {
		given(this.webClientMock.get()).willReturn(this.requestHeadersUriSpecMock);
		given(this.requestHeadersUriSpecMock.uri(any(Function.class))).willReturn(this.requestHeadersSpecMock);
		given(this.requestHeadersSpecMock.retrieve()).willReturn(this.responseSpecMock);

		UUID tweetId = UUID.randomUUID();
		Long expectedCount = 15L;

		given(this.responseSpecMock.bodyToMono(Long.class)).willReturn(Mono.just(expectedCount));

		StepVerifier.create(this.interactionClient.getLikesCount(tweetId)).expectNext(expectedCount).verifyComplete();
	}

	@Test
	void testGetRetweetsCount() {
		given(this.webClientMock.get()).willReturn(this.requestHeadersUriSpecMock);
		given(this.requestHeadersUriSpecMock.uri(any(Function.class))).willReturn(this.requestHeadersSpecMock);
		given(this.requestHeadersSpecMock.retrieve()).willReturn(this.responseSpecMock);

		UUID tweetId = UUID.randomUUID();
		Long expectedCount = 20L;

		given(this.responseSpecMock.bodyToMono(Long.class)).willReturn(Mono.just(expectedCount));

		StepVerifier.create(this.interactionClient.getRetweetsCount(tweetId))
			.expectNext(expectedCount)
			.verifyComplete();
	}

	@Test
	void testGetTopReply() {
		given(this.webClientMock.get()).willReturn(this.requestHeadersUriSpecMock);
		given(this.requestHeadersUriSpecMock.uri(any(Function.class))).willReturn(this.requestHeadersSpecMock);
		given(this.requestHeadersSpecMock.retrieve()).willReturn(this.responseSpecMock);

		UUID tweetId = UUID.randomUUID();
		ReplyDto expectedReply = new ReplyDto();

		given(this.responseSpecMock.bodyToMono(ReplyDto.class)).willReturn(Mono.just(expectedReply));

		StepVerifier.create(this.interactionClient.getTopReply(tweetId)).expectNext(expectedReply).verifyComplete();
	}

	@Test
	void testGetRepliesForTweet() {
		given(this.webClientMock.get()).willReturn(this.requestHeadersUriSpecMock);
		given(this.requestHeadersUriSpecMock.uri(any(Function.class))).willReturn(this.requestHeadersSpecMock);
		given(this.requestHeadersSpecMock.retrieve()).willReturn(this.responseSpecMock);

		UUID tweetId = UUID.randomUUID();
		ReplyDto reply1 = new ReplyDto();
		ReplyDto reply2 = new ReplyDto();

		given(this.responseSpecMock.bodyToFlux(ReplyDto.class)).willReturn(Flux.just(reply1, reply2));

		StepVerifier.create(this.interactionClient.getRepliesForTweet(tweetId))
			.expectNext(reply1)
			.expectNext(reply2)
			.verifyComplete();
	}

	@Test
	void testGetFollowedIds() {
		given(this.webClientMock.get()).willReturn(this.requestHeadersUriSpecMock);
		given(this.requestHeadersUriSpecMock.uri(any(Function.class))).willReturn(this.requestHeadersSpecMock);
		given(this.requestHeadersSpecMock.retrieve()).willReturn(this.responseSpecMock);

		UUID userId = UUID.randomUUID();
		List<UUID> expectedFollowers = List.of(UUID.randomUUID(), UUID.randomUUID());

		given(this.responseSpecMock.bodyToMono(new ParameterizedTypeReference<List<UUID>>() {
		})).willReturn(Mono.just(expectedFollowers));
		given(this.redisTemplate.opsForValue()).willReturn(this.reactiveValueOperations);
		given(this.reactiveValueOperations.set(anyString(), eq(expectedFollowers))).willReturn(Mono.just(true));

		StepVerifier.create(this.interactionClient.getFollowedIds(userId))
			.expectNextSequence(expectedFollowers)
			.verifyComplete();

		verify(this.reactiveValueOperations).set("followed_cache::" + userId, expectedFollowers);
	}

	@Test
	void testGetTweetInteractions() {
		// One consolidated POST resolves a whole page; the body Flux is collected into a map
		// keyed by tweet id, each entry mapped through toInteractions().
		given(this.webClientMock.post()).willReturn(this.requestBodyUriSpecMock);
		given(this.requestBodyUriSpecMock.uri("/tweets/interactions")).willReturn(this.requestBodySpecMock);
		given(this.requestBodySpecMock.bodyValue(any())).willReturn(this.requestHeadersSpecMock);
		given(this.requestHeadersSpecMock.retrieve()).willReturn(this.responseSpecMock);

		UUID tweetA = UUID.randomUUID();
		UUID tweetB = UUID.randomUUID();
		ReplyDto topReply = new ReplyDto();
		TweetInteractionsEntryDto entryA = new TweetInteractionsEntryDto(tweetA, 5L, 2L, 1L, topReply);
		TweetInteractionsEntryDto entryB = new TweetInteractionsEntryDto(tweetB, 0L, 0L, 0L, null);
		given(this.responseSpecMock.bodyToFlux(TweetInteractionsEntryDto.class))
			.willReturn(Flux.just(entryA, entryB));

		StepVerifier.create(this.interactionClient.getTweetInteractions(List.of(tweetA, tweetB)))
			.assertNext(map -> {
				assertThat(map).containsOnlyKeys(tweetA, tweetB);
				TweetInteractionsDto a = map.get(tweetA);
				assertThat(a.getLikes()).isEqualTo(5L);
				assertThat(a.getReplies()).isEqualTo(2L);
				assertThat(a.getRetweets()).isEqualTo(1L);
				assertThat(a.getTopReply()).isSameAs(topReply);
				TweetInteractionsDto b = map.get(tweetB);
				assertThat(b.getLikes()).isZero();
				assertThat(b.getTopReply()).isNull();
			})
			.verifyComplete();

		verify(this.requestBodySpecMock).bodyValue(List.of(tweetA, tweetB));
	}

	@Test
	void getTweetInteractions_emptyResponse_yieldsEmptyMap() {
		given(this.webClientMock.post()).willReturn(this.requestBodyUriSpecMock);
		given(this.requestBodyUriSpecMock.uri("/tweets/interactions")).willReturn(this.requestBodySpecMock);
		given(this.requestBodySpecMock.bodyValue(any())).willReturn(this.requestHeadersSpecMock);
		given(this.requestHeadersSpecMock.retrieve()).willReturn(this.responseSpecMock);
		given(this.responseSpecMock.bodyToFlux(TweetInteractionsEntryDto.class)).willReturn(Flux.empty());

		StepVerifier.create(this.interactionClient.getTweetInteractions(List.of(UUID.randomUUID())))
			.assertNext(map -> assertThat(map).isEmpty())
			.verifyComplete();
	}

	@Test
	void getTweetInteractions_exceptionThrown() {
		given(this.webClientMock.post()).willReturn(this.requestBodyUriSpecMock);
		given(this.requestBodyUriSpecMock.uri("/tweets/interactions")).willReturn(this.requestBodySpecMock);
		given(this.requestBodySpecMock.bodyValue(any())).willReturn(this.requestHeadersSpecMock);
		given(this.requestHeadersSpecMock.retrieve()).willReturn(this.responseSpecMock);
		given(this.responseSpecMock.bodyToFlux(TweetInteractionsEntryDto.class))
			.willReturn(Flux.error(new TweetException(new RuntimeException("boom"))));

		StepVerifier.create(this.interactionClient.getTweetInteractions(List.of(UUID.randomUUID())))
			.expectError(TweetException.class)
			.verify();
	}

	@Test
	void getRepliesCount_exceptionThrown() {
		given(this.webClientMock.get()).willReturn(this.requestHeadersUriSpecMock);
		given(this.requestHeadersUriSpecMock.uri(any(Function.class))).willReturn(this.requestHeadersSpecMock);
		given(this.requestHeadersSpecMock.retrieve()).willReturn(this.responseSpecMock);

		UUID tweetId = UUID.randomUUID();
		given(this.responseSpecMock.bodyToMono(Long.class))
			.willReturn(Mono.error(new TweetException(new RuntimeException("boom"))));

		StepVerifier.create(this.interactionClient.getRepliesCount(tweetId)).expectError(TweetException.class).verify();
	}

	@Test
	void getLikesCount_exceptionThrown() {
		given(this.webClientMock.get()).willReturn(this.requestHeadersUriSpecMock);
		given(this.requestHeadersUriSpecMock.uri(any(Function.class))).willReturn(this.requestHeadersSpecMock);
		given(this.requestHeadersSpecMock.retrieve()).willReturn(this.responseSpecMock);

		UUID tweetId = UUID.randomUUID();
		given(this.responseSpecMock.bodyToMono(Long.class))
			.willReturn(Mono.error(new TweetException(new RuntimeException("boom"))));

		StepVerifier.create(this.interactionClient.getLikesCount(tweetId)).expectError(TweetException.class).verify();
	}

	@Test
	void getRetweetsCount_exceptionThrown() {
		given(this.webClientMock.get()).willReturn(this.requestHeadersUriSpecMock);
		given(this.requestHeadersUriSpecMock.uri(any(Function.class))).willReturn(this.requestHeadersSpecMock);
		given(this.requestHeadersSpecMock.retrieve()).willReturn(this.responseSpecMock);

		UUID tweetId = UUID.randomUUID();
		given(this.responseSpecMock.bodyToMono(Long.class))
			.willReturn(Mono.error(new TweetException(new RuntimeException("boom"))));

		StepVerifier.create(this.interactionClient.getRetweetsCount(tweetId))
			.expectError(TweetException.class)
			.verify();
	}

	@Test
	void getTopReply_exceptionThrown() {
		given(this.webClientMock.get()).willReturn(this.requestHeadersUriSpecMock);
		given(this.requestHeadersUriSpecMock.uri(any(Function.class))).willReturn(this.requestHeadersSpecMock);
		given(this.requestHeadersSpecMock.retrieve()).willReturn(this.responseSpecMock);

		UUID tweetId = UUID.randomUUID();
		given(this.responseSpecMock.bodyToMono(ReplyDto.class))
			.willReturn(Mono.error(new TweetException(new RuntimeException("boom"))));

		StepVerifier.create(this.interactionClient.getTopReply(tweetId)).expectError(TweetException.class).verify();
	}

	@Test
	void getRepliesForTweet_exceptionThrown() {
		given(this.webClientMock.get()).willReturn(this.requestHeadersUriSpecMock);
		given(this.requestHeadersUriSpecMock.uri(any(Function.class))).willReturn(this.requestHeadersSpecMock);
		given(this.requestHeadersSpecMock.retrieve()).willReturn(this.responseSpecMock);

		UUID tweetId = UUID.randomUUID();
		given(this.responseSpecMock.bodyToFlux(ReplyDto.class))
			.willReturn(Flux.error(new TweetException(new RuntimeException("boom"))));

		StepVerifier.create(this.interactionClient.getRepliesForTweet(tweetId))
			.expectError(TweetException.class)
			.verify();
	}

	@Test
	void getFollowedIds_exceptionThrown() {
		given(this.webClientMock.get()).willReturn(this.requestHeadersUriSpecMock);
		given(this.requestHeadersUriSpecMock.uri(any(Function.class))).willReturn(this.requestHeadersSpecMock);
		given(this.requestHeadersSpecMock.retrieve()).willReturn(this.responseSpecMock);

		UUID userId = UUID.randomUUID();
		given(this.responseSpecMock.bodyToMono(new ParameterizedTypeReference<List<UUID>>() {
		})).willReturn(Mono.error(new RuntimeException("boom")));

		StepVerifier.create(this.interactionClient.getFollowedIds(userId)).expectError(RuntimeException.class).verify();
	}

}
