/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for the reactive CacheConfiguration. Verifies that reactiveRedisTemplate is
 * created and uses the expected key/value serializers by round-tripping data through the
 * serialization context, without requiring a live Redis connection.
 */
class CacheConfigurationTests {

	private final CacheConfiguration config = new CacheConfiguration();

	@Test
	void reactiveRedisTemplate_isNotNull() {
		LettuceConnectionFactory cf = mock(LettuceConnectionFactory.class);
		ReactiveRedisTemplate<String, byte[]> template = this.config.reactiveRedisTemplate(cf);
		assertThat(template).isNotNull();
	}

	@Test
	void reactiveRedisTemplate_serializationContext_isNotNull() {
		LettuceConnectionFactory cf = mock(LettuceConnectionFactory.class);
		ReactiveRedisTemplate<String, byte[]> template = this.config.reactiveRedisTemplate(cf);
		RedisSerializationContext<String, byte[]> ctx = template.getSerializationContext();
		assertThat(ctx).isNotNull();
		assertThat(ctx.getKeySerializationPair()).isNotNull();
		assertThat(ctx.getValueSerializationPair()).isNotNull();
		assertThat(ctx.getHashKeySerializationPair()).isNotNull();
		assertThat(ctx.getHashValueSerializationPair()).isNotNull();
	}

	@Test
	void reactiveRedisTemplate_keySerializer_roundTripsString() {
		LettuceConnectionFactory cf = mock(LettuceConnectionFactory.class);
		ReactiveRedisTemplate<String, byte[]> template = this.config.reactiveRedisTemplate(cf);
		RedisSerializationContext<String, byte[]> ctx = template.getSerializationContext();

		// Key serializer should behave like StringRedisSerializer
		String key = "my-cache-key";
		java.nio.ByteBuffer written = ctx.getKeySerializationPair().write(key);
		String read = ctx.getKeySerializationPair().read(written);
		assertThat(read).isEqualTo(key);
	}

	@Test
	void reactiveRedisTemplate_valueSerializer_roundTripsByteArray() {
		LettuceConnectionFactory cf = mock(LettuceConnectionFactory.class);
		ReactiveRedisTemplate<String, byte[]> template = this.config.reactiveRedisTemplate(cf);
		RedisSerializationContext<String, byte[]> ctx = template.getSerializationContext();

		// Value serializer should be ByteArrayRedisSerializer: round-trip a byte array
		byte[] original = new byte[] { 1, 2, 3, 4, 5 };
		java.nio.ByteBuffer written = ctx.getValueSerializationPair().write(original);
		byte[] read = ctx.getValueSerializationPair().read(written);
		assertThat(read).isEqualTo(original);
	}

	@Test
	void reactiveRedisTemplate_hashValueSerializer_roundTripsByteArray() {
		LettuceConnectionFactory cf = mock(LettuceConnectionFactory.class);
		ReactiveRedisTemplate<String, byte[]> template = this.config.reactiveRedisTemplate(cf);
		RedisSerializationContext<String, byte[]> ctx = template.getSerializationContext();

		byte[] hash = new byte[] { 10, 20 };
		// getHashValueSerializationPair() returns SerializationPair<Object>; use raw type
		@SuppressWarnings({ "unchecked", "rawtypes" })
		RedisSerializationContext.SerializationPair raw =
				(RedisSerializationContext.SerializationPair) ctx.getHashValueSerializationPair();
		java.nio.ByteBuffer written = raw.write(hash);
		byte[] read = (byte[]) raw.read(written);
		assertThat(read).isEqualTo(hash);
	}

}
