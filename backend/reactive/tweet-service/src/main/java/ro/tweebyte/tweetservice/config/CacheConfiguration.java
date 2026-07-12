/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.config;

import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableCaching
public class CacheConfiguration {

	@Bean
	public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
		RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(5));

		return RedisCacheManager.builder(connectionFactory).cacheDefaults(config).build();
	}

	@Bean
	public ReactiveRedisTemplate<String, Object> redisTemplate(ReactiveRedisConnectionFactory factory) {
		// findAndAddModules() registers every Jackson module on the classpath (incl. JSR-310) so
		// the value serializer round-trips whatever the cache stores. Current writers store a
		// java.time-free shape — UserService caches a username->userId UUID and InteractionClient
		// caches a followed-ids List<UUID> — but registering the modules keeps the serializer
		// correct for any time-bearing value a future cache writer adds, matching the async stack's
		// SerializationUtil.
		ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
		Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);
		RedisSerializationContext<String, Object> serializationContext = RedisSerializationContext
			.<String, Object>newSerializationContext(StringRedisSerializer.UTF_8)
			.value(serializer)
			.build();
		return new ReactiveRedisTemplate<>(factory, serializationContext);
	}

}
