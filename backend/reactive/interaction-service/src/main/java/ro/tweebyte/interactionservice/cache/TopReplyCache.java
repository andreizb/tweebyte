/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.cache;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import ro.tweebyte.interactionservice.exception.InteractionException;
import ro.tweebyte.interactionservice.model.ReplyDto;

/**
 * TTL-only read-through cache for the top reply of a tweet, shared by the single and
 * batched top-reply endpoints. A tweet with no top reply is recorded with a one-byte
 * absent marker so the negative result is cached too, instead of being re-queried every
 * read. The top reply's ranking depends on per-reply like counts, which are not tracked
 * with a delta here, so the cache is allowed to go stale and is reseeded at the TTL — no
 * precise eviction on like/unlike.
 *
 * @author Andrei Zbarcea
 */
@Component
public class TopReplyCache {

	// A serialised ReplyDto is JSON and always starts with '{', so a single zero byte can
	// never collide with a real value and unambiguously marks "this tweet has no top reply".
	private static final int ABSENT_MARKER = 0;

	// Batch the cold-miss writes into ONE round-trip: SETEX every KEYS[i] to ARGV[i+1] with
	// the shared TTL in ARGV[1]. EVAL runs over Lettuce's shared connection.
	private static final String FILL_MISSES = "for i = 1, #KEYS do\n"
			+ "  redis.call('SETEX', KEYS[i], ARGV[1], ARGV[i + 1])\n" + "end\n" + "return #KEYS";

	private final ReactiveRedisTemplate<String, byte[]> redisTemplate;

	private final RedisScript<Long> fillMissesScript = new DefaultRedisScript<>(FILL_MISSES, Long.class);

	private final Duration cacheTtl;

	private final ObjectMapper objectMapper = JsonMapper.builder()
		.addModule(new JavaTimeModule())
		.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
		.build();

	public TopReplyCache(ReactiveRedisTemplate<String, byte[]> redisTemplate,
			@Value("${spring.cache.redis.time-to-live}") Duration cacheTtl) {
		this.redisTemplate = redisTemplate;
		this.cacheTtl = cacheTtl;
	}

	// A cache miss runs the loader; an empty loader (no top reply) writes the absent
	// marker and yields an empty ReplyDto, matching the single endpoint's contract.
	public Mono<ReplyDto> get(String key, Mono<ReplyDto> databaseLoader) {
		return CacheReads.offload(this.redisTemplate.opsForValue().get(key))
			.map(bytes -> isAbsentMarker(bytes) ? new ReplyDto() : decode(bytes))
			.switchIfEmpty(Mono.defer(() -> databaseLoader
				.flatMap(dto -> this.redisTemplate.opsForValue().set(key, encode(dto), this.cacheTtl).thenReturn(dto))
				.switchIfEmpty(Mono.defer(() -> this.redisTemplate.opsForValue()
					.set(key, absentMarker(), this.cacheTtl)
					.thenReturn(new ReplyDto())))));
	}

	public Mono<Map<UUID, ReplyDto>> getAll(List<UUID> ids, Function<UUID, String> keyFor,
			Function<List<UUID>, Mono<Map<UUID, ReplyDto>>> databaseLoader) {
		if (ids == null || ids.isEmpty()) {
			return Mono.just(Map.of());
		}
		List<String> keys = ids.stream().map(keyFor).toList();
		return CacheReads.offload(this.redisTemplate.opsForValue().multiGet(keys)).flatMap(cached -> {
			Map<UUID, ReplyDto> result = new HashMap<>();
			List<UUID> misses = new ArrayList<>();
			for (int index = 0; index < ids.size(); index++) {
				byte[] bytes = cached.get(index);
				if (bytes == null) {
					misses.add(ids.get(index));
				}
				else if (!isAbsentMarker(bytes)) {
					result.put(ids.get(index), decode(bytes));
				}
			}
			if (misses.isEmpty()) {
				return Mono.just(result);
			}
			return databaseLoader.apply(misses).flatMap(fromDatabase -> fillMisses(result, misses, keyFor, fromDatabase));
		});
	}

	// Write back every miss: a tweet the loader resolved gets its serialised top reply, a
	// tweet it omitted (no top reply) gets the absent marker so the negative is cached too.
	private Mono<Map<UUID, ReplyDto>> fillMisses(Map<UUID, ReplyDto> result, List<UUID> misses,
			Function<UUID, String> keyFor, Map<UUID, ReplyDto> fromDatabase) {
		List<String> keys = new ArrayList<>(misses.size());
		List<byte[]> values = new ArrayList<>(misses.size());
		for (UUID id : misses) {
			ReplyDto dto = fromDatabase.get(id);
			if (dto != null) {
				result.put(id, dto);
				keys.add(keyFor.apply(id));
				values.add(encode(dto));
			}
			else {
				keys.add(keyFor.apply(id));
				values.add(absentMarker());
			}
		}
		return writeBatch(keys, values).thenReturn(result);
	}

	// One EVAL over the shared connection: ARGV[1] = TTL seconds, ARGV[i+1] = value for KEYS[i].
	private Mono<Void> writeBatch(List<String> keys, List<byte[]> values) {
		if (keys.isEmpty()) {
			return Mono.empty();
		}
		List<Object> args = new ArrayList<>(values.size() + 1);
		args.add(Long.toString(this.cacheTtl.toSeconds()).getBytes(StandardCharsets.UTF_8));
		for (int index = 0; index < values.size(); index++) {
			args.add(values.get(index));
		}
		return this.redisTemplate.execute(this.fillMissesScript, keys, args).then();
	}

	private static byte[] absentMarker() {
		return new byte[] { ABSENT_MARKER };
	}

	private static boolean isAbsentMarker(byte[] bytes) {
		return bytes.length == 1 && bytes[0] == ABSENT_MARKER;
	}

	private byte[] encode(ReplyDto dto) {
		try {
			return this.objectMapper.writeValueAsBytes(dto);
		}
		catch (JsonProcessingException ex) {
			throw new InteractionException(ex);
		}
	}

	private ReplyDto decode(byte[] bytes) {
		try {
			return this.objectMapper.readValue(new String(bytes, StandardCharsets.UTF_8), ReplyDto.class);
		}
		catch (IOException ex) {
			throw new InteractionException(ex);
		}
	}

}
