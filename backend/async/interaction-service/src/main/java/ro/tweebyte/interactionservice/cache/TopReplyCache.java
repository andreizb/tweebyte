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
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

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
	// the shared TTL in ARGV[1]. EVAL runs over Lettuce's SHARED multiplexed connection —
	// unlike executePipelined, which forces a DEDICATED connection per call and churns
	// connections into ephemeral-port exhaustion under a cold-cache burst.
	private static final String FILL_MISSES = """
			for i = 1, #KEYS do
			redis.call('SETEX', KEYS[i], ARGV[1], ARGV[i + 1])
			end
			return #KEYS""";

	private final RedisTemplate<String, byte[]> redisTemplate;

	private final RedisScript<Long> fillMissesScript = new DefaultRedisScript<>(FILL_MISSES, Long.class);

	private final Duration cacheTtl;

	private final ObjectMapper objectMapper = JsonMapper.builder()
		.addModule(new JavaTimeModule())
		.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
		.build();

	public TopReplyCache(RedisTemplate<String, byte[]> redisTemplate,
			@Value("${spring.cache.redis.time-to-live}") Duration cacheTtl) {
		this.redisTemplate = redisTemplate;
		this.cacheTtl = cacheTtl;
	}

	// A cache miss runs the loader; a loader that resolves no top reply (an empty ReplyDto
	// with a null id) writes the absent marker and yields an empty ReplyDto, matching the
	// single endpoint's contract.
	public ReplyDto get(String key, Supplier<ReplyDto> databaseLoader) {
		byte[] cached = this.redisTemplate.opsForValue().get(key);
		if (cached != null) {
			return isAbsentMarker(cached) ? new ReplyDto() : decode(cached);
		}
		ReplyDto dto = databaseLoader.get();
		if (dto == null || dto.getId() == null) {
			this.redisTemplate.opsForValue().set(key, absentMarker(), this.cacheTtl);
			return new ReplyDto();
		}
		this.redisTemplate.opsForValue().set(key, encode(dto), this.cacheTtl);
		return dto;
	}

	public Map<UUID, ReplyDto> getAll(List<UUID> ids, Function<UUID, String> keyFor,
			Function<List<UUID>, Map<UUID, ReplyDto>> databaseLoader) {
		if (ids == null || ids.isEmpty()) {
			return Map.of();
		}
		List<String> keys = ids.stream().map(keyFor).toList();
		List<byte[]> cached = this.redisTemplate.opsForValue().multiGet(keys);
		Map<UUID, ReplyDto> result = new HashMap<>();
		List<UUID> misses = new ArrayList<>();
		for (int index = 0; index < ids.size(); index++) {
			byte[] bytes = (cached != null) ? cached.get(index) : null;
			if (bytes == null) {
				misses.add(ids.get(index));
			}
			else if (!isAbsentMarker(bytes)) {
				result.put(ids.get(index), decode(bytes));
			}
		}
		if (misses.isEmpty()) {
			return result;
		}
		fillMisses(result, misses, keyFor, databaseLoader.apply(misses));
		return result;
	}

	// Write back every miss: a tweet the loader resolved gets its serialised top reply, a
	// tweet it omitted (no top reply) gets the absent marker so the negative is cached too.
	private void fillMisses(Map<UUID, ReplyDto> result, List<UUID> misses, Function<UUID, String> keyFor,
			Map<UUID, ReplyDto> fromDatabase) {
		// Build parallel key/value lists (the Lua script aligns KEYS[i] with ARGV[i+1]) and
		// write them all in one EVAL round-trip — a cold page of N tweets otherwise costs N
		// sequential blocking round-trips. The script (not executePipelined) keeps the write on
		// Lettuce's shared multiplexed connection, so it never churns dedicated connections.
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
		writeBatch(keys, values);
	}

	// One EVAL over the shared connection: ARGV[1] = TTL seconds, ARGV[i+1] = value for KEYS[i].
	private void writeBatch(List<String> keys, List<byte[]> values) {
		if (keys.isEmpty()) {
			return;
		}
		Object[] args = new Object[values.size() + 1];
		args[0] = Long.toString(this.cacheTtl.toSeconds()).getBytes(StandardCharsets.UTF_8);
		for (int index = 0; index < values.size(); index++) {
			args[index + 1] = values.get(index);
		}
		this.redisTemplate.execute(this.fillMissesScript, keys, args);
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
