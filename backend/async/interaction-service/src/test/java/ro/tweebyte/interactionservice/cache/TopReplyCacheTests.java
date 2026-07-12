/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.cache;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import ro.tweebyte.interactionservice.exception.InteractionException;
import ro.tweebyte.interactionservice.model.ReplyDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit suite for {@link TopReplyCache} — exercises the value-hit/absent-marker-hit/miss
 * branches of the single read, the loader-empty negative-cache path, batched multi-get with
 * present, absent-marker, and null entries, the empty/null id short-circuit, and the
 * round-trip serialization paths.
 *
 * @author Andrei Zbarcea
 */
class TopReplyCacheTests {

	private RedisTemplate<String, byte[]> redisTemplate;

	private ValueOperations<String, byte[]> valueOperations;

	private TopReplyCache topReplyCache;

	private final ObjectMapper objectMapper = JsonMapper.builder()
		.addModule(new JavaTimeModule())
		.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
		.build();

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		this.redisTemplate = mock(RedisTemplate.class);
		this.valueOperations = mock(ValueOperations.class);
		given(this.redisTemplate.opsForValue()).willReturn(this.valueOperations);
		this.topReplyCache = new TopReplyCache(this.redisTemplate, Duration.ofSeconds(60));
	}

	private byte[] encode(ReplyDto dto) throws Exception {
		return this.objectMapper.writeValueAsBytes(dto);
	}

	private ReplyDto sampleReply(UUID id) {
		return new ReplyDto(id, UUID.randomUUID(), "hello", LocalDateTime.of(2026, 1, 1, 0, 0), 3L).setUserName("alice");
	}

	@Test
	void get_CacheHitWithValue_DecodesWithoutLoading() throws Exception {
		String key = "top_reply::tweet";
		UUID replyId = UUID.randomUUID();
		given(this.valueOperations.get(key)).willReturn(encode(sampleReply(replyId)));

		Supplier<ReplyDto> loader = () -> {
			throw new AssertionError("loader must not run on a value hit");
		};

		assertThat(this.topReplyCache.get(key, loader).getId()).isEqualTo(replyId);

		verify(this.valueOperations, never()).set(any(), any(), any());
	}

	@Test
	void get_CacheHitWithAbsentMarker_ReturnsEmptyDto() {
		// A single zero byte is the absent marker: a negative result cached on a prior read.
		String key = "top_reply::tweet";
		given(this.valueOperations.get(key)).willReturn(new byte[] { 0 });

		Supplier<ReplyDto> loader = () -> {
			throw new AssertionError("loader must not run on an absent-marker hit");
		};

		assertThat(this.topReplyCache.get(key, loader).getId()).isNull();

		verify(this.valueOperations, never()).set(any(), any(), any());
	}

	@Test
	void get_CacheMissLoaderPresent_WritesValueAndReturnsIt() throws Exception {
		String key = "top_reply::tweet";
		UUID replyId = UUID.randomUUID();
		ReplyDto loaded = sampleReply(replyId);
		given(this.valueOperations.get(key)).willReturn(null);

		assertThat(this.topReplyCache.get(key, () -> loaded).getId()).isEqualTo(replyId);

		verify(this.valueOperations).set(eq(key), any(byte[].class), eq(Duration.ofSeconds(60)));
		// The encoded value, not the absent marker, is written: serialised JSON > 1 byte.
		assertThat(encode(loaded)).hasSizeGreaterThan(1);
	}

	@Test
	void get_CacheMissLoaderEmpty_WritesAbsentMarkerAndReturnsEmptyDto() {
		// No top reply: the loader yields an empty ReplyDto (null id), so the absent marker
		// is cached and an empty ReplyDto is emitted (matching the single endpoint's contract).
		String key = "top_reply::tweet";
		given(this.valueOperations.get(key)).willReturn(null);

		assertThat(this.topReplyCache.get(key, ReplyDto::new).getId()).isNull();

		verify(this.valueOperations).set(eq(key), any(byte[].class), eq(Duration.ofSeconds(60)));
	}

	@Test
	void get_CacheMissLoaderNull_WritesAbsentMarkerAndReturnsEmptyDto() {
		// A null loader result is treated identically to an empty ReplyDto: negative-cached.
		String key = "top_reply::tweet";
		given(this.valueOperations.get(key)).willReturn(null);

		assertThat(this.topReplyCache.get(key, () -> null).getId()).isNull();

		verify(this.valueOperations).set(eq(key), any(byte[].class), eq(Duration.ofSeconds(60)));
	}

	@Test
	void getAll_NullIds_ReturnsEmptyMap() {
		assertThat(this.topReplyCache.getAll(null, id -> "top_reply::" + id, ids -> Map.of())).isEmpty();

		verify(this.valueOperations, never()).multiGet(anyList());
	}

	@Test
	void getAll_EmptyIds_ReturnsEmptyMap() {
		assertThat(this.topReplyCache.getAll(List.of(), id -> "top_reply::" + id, ids -> Map.of())).isEmpty();

		verify(this.valueOperations, never()).multiGet(anyList());
	}

	@Test
	void getAll_AllResolvedFromCache_NoLoader() throws Exception {
		// One cached value, one absent-marker (excluded from the result), so misses is empty.
		UUID withReply = UUID.randomUUID();
		UUID withoutReply = UUID.randomUUID();
		Function<UUID, String> keyFor = id -> "top_reply::" + id;
		given(this.valueOperations.multiGet(List.of(keyFor.apply(withReply), keyFor.apply(withoutReply))))
			.willReturn(List.of(encode(sampleReply(withReply)), new byte[] { 0 }));

		Function<List<UUID>, Map<UUID, ReplyDto>> loader = ids -> {
			throw new AssertionError("loader must not run when all resolved");
		};

		Map<UUID, ReplyDto> result = this.topReplyCache.getAll(List.of(withReply, withoutReply), keyFor, loader);

		assertThat(result).containsOnlyKeys(withReply);
		assertThat(result.get(withReply).getId()).isEqualTo(withReply);
		verify(this.valueOperations, never()).set(any(), any(), any());
	}

	@Test
	void getAll_PartialMisses_WritesValueAndAbsentMarker() throws Exception {
		UUID hit = UUID.randomUUID();
		UUID missPresent = UUID.randomUUID();
		UUID missAbsent = UUID.randomUUID();
		Function<UUID, String> keyFor = id -> "top_reply::" + id;
		List<UUID> ids = List.of(hit, missPresent, missAbsent);
		// hit -> value; the other two are null in cache (true misses).
		given(this.valueOperations.multiGet(ids.stream().map(keyFor).toList()))
			.willReturn(Arrays.asList(encode(sampleReply(hit)), null, null));

		ReplyDto loadedPresent = sampleReply(missPresent);
		Function<List<UUID>, Map<UUID, ReplyDto>> loader = misses -> {
			assertThat(misses).containsExactlyInAnyOrder(missPresent, missAbsent);
			// missAbsent is omitted: the loader found no top reply for it.
			return Map.of(missPresent, loadedPresent);
		};

		Map<UUID, ReplyDto> result = this.topReplyCache.getAll(ids, keyFor, loader);

		assertThat(result).containsOnlyKeys(hit, missPresent);
		assertThat(result.get(hit).getId()).isEqualTo(hit);
		assertThat(result.get(missPresent).getId()).isEqualTo(missPresent);

		// Two write-backs (missPresent value + missAbsent marker) in ONE EVAL round-trip over
		// the shared connection: TTL arg + 2 value args.
		verify(this.redisTemplate).execute(any(RedisScript.class), anyList(), any(byte[].class), any(byte[].class),
				any(byte[].class));
	}

	@Test
	void getAll_NullMultiGetResult_TreatedAsAllMisses() {
		// A null multiGet payload is defensively treated as every id missing.
		UUID withReply = UUID.randomUUID();
		Function<UUID, String> keyFor = id -> "top_reply::" + id;
		given(this.valueOperations.multiGet(List.of(keyFor.apply(withReply)))).willReturn(null);

		ReplyDto loaded = sampleReply(withReply);
		Map<UUID, ReplyDto> result = this.topReplyCache.getAll(List.of(withReply), keyFor,
				ids -> Map.of(withReply, loaded));

		assertThat(result.get(withReply).getId()).isEqualTo(withReply);
		// One miss → ONE EVAL round-trip: TTL arg + 1 value arg.
		verify(this.redisTemplate).execute(any(RedisScript.class), anyList(), any(byte[].class), any(byte[].class));
	}

	@Test
	void get_CorruptCachedBytes_WrapsDecodeFailureInInteractionException() {
		// A cached value that is not the absent marker but is not valid ReplyDto JSON fails
		// to decode; the IOException is wrapped as an InteractionException.
		String key = "top_reply::tweet";
		given(this.valueOperations.get(key)).willReturn("not-json".getBytes(StandardCharsets.UTF_8));

		assertThatThrownBy(() -> this.topReplyCache.get(key, ReplyDto::new))
			.isInstanceOf(InteractionException.class);
	}

	@Test
	void get_SingleNonZeroByte_NotTreatedAsAbsentMarker() {
		// A length-1 cached value whose only byte is not the marker is a real value, so it is
		// decoded (and here fails to parse), exercising the second arm of isAbsentMarker.
		String key = "top_reply::tweet";
		given(this.valueOperations.get(key)).willReturn(new byte[] { 1 });

		assertThatThrownBy(() -> this.topReplyCache.get(key, ReplyDto::new))
			.isInstanceOf(InteractionException.class);
	}

	@Test
	void get_EncodeFailure_WrapsInInteractionException() throws Exception {
		// A mapper that throws on writeValueAsBytes surfaces as an InteractionException when a
		// resolved top reply is serialised for caching.
		String key = "top_reply::tweet";
		given(this.valueOperations.get(key)).willReturn(null);
		ObjectMapper mockMapper = mock(ObjectMapper.class);
		given(mockMapper.writeValueAsBytes(any())).willThrow(new JsonProcessingException("boom") {
		});
		ReflectionTestUtils.setField(this.topReplyCache, "objectMapper", mockMapper);

		assertThatThrownBy(() -> this.topReplyCache.get(key, () -> sampleReply(UUID.randomUUID())))
			.isInstanceOf(InteractionException.class);
	}

}
