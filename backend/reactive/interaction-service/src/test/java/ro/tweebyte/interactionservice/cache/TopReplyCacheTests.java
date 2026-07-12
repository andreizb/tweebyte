/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.cache;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.interactionservice.exception.InteractionException;
import ro.tweebyte.interactionservice.model.ReplyDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Unit suite for {@link TopReplyCache} — exercises the value-hit/absent-marker-hit/miss
 * branches of the single read, the loader-empty negative-cache path, batched multi-get with
 * present, absent-marker, and null entries, the empty/null id short-circuit, and the
 * round-trip serialization paths.
 */
class TopReplyCacheTests {

	private ReactiveRedisTemplate<String, byte[]> redisTemplate;

	private ReactiveValueOperations<String, byte[]> valueOperations;

	private TopReplyCache topReplyCache;

	private final ObjectMapper objectMapper = JsonMapper.builder()
		.addModule(new JavaTimeModule())
		.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
		.build();

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		this.redisTemplate = mock(ReactiveRedisTemplate.class);
		this.valueOperations = mock(ReactiveValueOperations.class);
		given(this.redisTemplate.opsForValue()).willReturn(this.valueOperations);
		this.topReplyCache = new TopReplyCache(this.redisTemplate, Duration.ofSeconds(60));
	}

	private byte[] encode(ReplyDto dto) throws Exception {
		return this.objectMapper.writeValueAsBytes(dto);
	}

	private ReplyDto sampleReply(UUID id) {
		return new ReplyDto(id, UUID.randomUUID(), "hello", LocalDateTime.of(2026, 1, 1, 0, 0), 3L)
			.setUserName("alice");
	}

	@Test
	void get_CacheHitWithValue_DecodesWithoutLoading() throws Exception {
		String key = "top_reply::tweet";
		UUID replyId = UUID.randomUUID();
		ReplyDto cached = sampleReply(replyId);
		given(this.valueOperations.get(key)).willReturn(Mono.just(encode(cached)));

		Mono<ReplyDto> loader = Mono.error(new AssertionError("loader must not run on a value hit"));

		StepVerifier.create(this.topReplyCache.get(key, loader))
			.assertNext(dto -> assertThat(dto.getId()).isEqualTo(replyId))
			.verifyComplete();

		verify(this.valueOperations, never()).set(any(), any(), any());
	}

	@Test
	void get_CacheHitWithAbsentMarker_ReturnsEmptyDto() {
		// A single zero byte is the absent marker: a negative result cached on a prior read.
		String key = "top_reply::tweet";
		given(this.valueOperations.get(key)).willReturn(Mono.just(new byte[] { 0 }));

		Mono<ReplyDto> loader = Mono.error(new AssertionError("loader must not run on an absent-marker hit"));

		StepVerifier.create(this.topReplyCache.get(key, loader))
			.assertNext(dto -> assertThat(dto.getId()).isNull())
			.verifyComplete();

		verify(this.valueOperations, never()).set(any(), any(), any());
	}

	@Test
	void get_CacheMissLoaderPresent_WritesValueAndReturnsIt() throws Exception {
		String key = "top_reply::tweet";
		UUID replyId = UUID.randomUUID();
		ReplyDto loaded = sampleReply(replyId);
		given(this.valueOperations.get(key)).willReturn(Mono.empty());
		given(this.valueOperations.set(eq(key), any(byte[].class), eq(Duration.ofSeconds(60))))
			.willReturn(Mono.just(true));

		StepVerifier.create(this.topReplyCache.get(key, Mono.just(loaded)))
			.assertNext(dto -> assertThat(dto.getId()).isEqualTo(replyId))
			.verifyComplete();

		verify(this.valueOperations).set(eq(key), any(byte[].class), eq(Duration.ofSeconds(60)));
		// The encoded value, not the absent marker, is written: serialised JSON > 1 byte.
		assertThat(encode(loaded).length).isGreaterThan(1);
	}

	@Test
	void get_CacheMissLoaderEmpty_WritesAbsentMarkerAndReturnsEmptyDto() {
		// No top reply: the loader completes empty, so the absent marker is cached and an
		// empty ReplyDto is emitted (matching the single endpoint's contract).
		String key = "top_reply::tweet";
		given(this.valueOperations.get(key)).willReturn(Mono.empty());
		given(this.valueOperations.set(eq(key), any(byte[].class), eq(Duration.ofSeconds(60))))
			.willReturn(Mono.just(true));

		StepVerifier.create(this.topReplyCache.get(key, Mono.empty()))
			.assertNext(dto -> assertThat(dto.getId()).isNull())
			.verifyComplete();

		verify(this.valueOperations).set(eq(key), any(byte[].class), eq(Duration.ofSeconds(60)));
	}

	@Test
	void getAll_NullIds_ReturnsEmptyMap() {
		StepVerifier.create(this.topReplyCache.getAll(null, id -> "top_reply::" + id, ids -> Mono.just(Map.of())))
			.expectNext(Map.of())
			.verifyComplete();

		verify(this.valueOperations, never()).multiGet(anyList());
	}

	@Test
	void getAll_EmptyIds_ReturnsEmptyMap() {
		StepVerifier
			.create(this.topReplyCache.getAll(List.of(), id -> "top_reply::" + id, ids -> Mono.just(Map.of())))
			.expectNext(Map.of())
			.verifyComplete();
	}

	@Test
	void getAll_AllResolvedFromCache_NoLoader() throws Exception {
		// One cached value, one absent-marker (excluded from the result), so misses is empty.
		UUID withReply = UUID.randomUUID();
		UUID withoutReply = UUID.randomUUID();
		Function<UUID, String> keyFor = id -> "top_reply::" + id;
		ReplyDto cached = sampleReply(withReply);
		given(this.valueOperations.multiGet(List.of(keyFor.apply(withReply), keyFor.apply(withoutReply))))
			.willReturn(Mono.just(List.of(encode(cached), new byte[] { 0 })));

		Function<List<UUID>, Mono<Map<UUID, ReplyDto>>> loader = ids -> Mono
			.error(new AssertionError("loader must not run when all resolved"));

		StepVerifier.create(this.topReplyCache.getAll(List.of(withReply, withoutReply), keyFor, loader))
			.assertNext(result -> {
				assertThat(result).containsOnlyKeys(withReply);
				assertThat(result.get(withReply).getId()).isEqualTo(withReply);
			})
			.verifyComplete();

		verify(this.valueOperations, never()).set(any(), any(), any());
	}

	@Test
	void getAll_PartialMisses_WritesValueAndAbsentMarker() throws Exception {
		UUID hit = UUID.randomUUID();
		UUID missPresent = UUID.randomUUID();
		UUID missAbsent = UUID.randomUUID();
		Function<UUID, String> keyFor = id -> "top_reply::" + id;
		List<UUID> ids = List.of(hit, missPresent, missAbsent);
		ReplyDto cachedHit = sampleReply(hit);
		// hit -> value; the other two are null in cache (true misses).
		given(this.valueOperations.multiGet(ids.stream().map(keyFor).toList()))
			.willReturn(Mono.just(Arrays.asList(encode(cachedHit), null, null)));

		ReplyDto loadedPresent = sampleReply(missPresent);
		Function<List<UUID>, Mono<Map<UUID, ReplyDto>>> loader = misses -> {
			assertThat(misses).containsExactlyInAnyOrder(missPresent, missAbsent);
			// missAbsent is omitted: the loader found no top reply for it.
			return Mono.just(Map.of(missPresent, loadedPresent));
		};

		given(this.redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
			.willReturn(Mono.just(2L).flux());

		StepVerifier.create(this.topReplyCache.getAll(ids, keyFor, loader)).assertNext(result -> {
			assertThat(result).containsOnlyKeys(hit, missPresent);
			assertThat(result.get(hit).getId()).isEqualTo(hit);
			assertThat(result.get(missPresent).getId()).isEqualTo(missPresent);
		}).verifyComplete();

		// Two write-backs in ONE EVAL round-trip: TTL arg + 2 value args.
		verify(this.redisTemplate).execute(any(RedisScript.class), anyList(), anyList());
	}

	@Test
	void get_CacheHitCorruptBytes_WrapsDecodeFailureAsInteractionException() {
		// A cached value that is neither the absent marker nor valid JSON trips decode(): the
		// IOException is wrapped as InteractionException rather than leaking the raw Jackson error.
		String key = "top_reply::tweet";
		given(this.valueOperations.get(key)).willReturn(Mono.just("not-json".getBytes()));

		StepVerifier.create(this.topReplyCache.get(key, Mono.empty())).expectError(InteractionException.class).verify();
	}

	@Test
	void get_CacheMissEncodeFails_WrapsSerializeFailureAsInteractionException() throws Exception {
		// On a miss the loaded reply is serialised for the SET write-back; a serialization failure
		// must surface as InteractionException (the encode() catch arm).
		String key = "top_reply::tweet";
		ObjectMapper throwingMapper = spy(JsonMapper.builder().addModule(new JavaTimeModule()).build());
		willThrow(new JsonProcessingException("boom") {
		}).given(throwingMapper).writeValueAsBytes(any());
		ReflectionTestUtils.setField(this.topReplyCache, "objectMapper", throwingMapper);

		given(this.valueOperations.get(key)).willReturn(Mono.empty());

		StepVerifier.create(this.topReplyCache.get(key, Mono.just(sampleReply(UUID.randomUUID()))))
			.expectError(InteractionException.class)
			.verify();
	}

}
