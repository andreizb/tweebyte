/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.UserDto;

@Configuration
@EnableCaching
public class CacheConfiguration {

	@Bean
	public RedisTemplate<String, byte[]> redisTemplate(LettuceConnectionFactory cf) {
		RedisTemplate<String, byte[]> tpl = new RedisTemplate<>();
		tpl.setConnectionFactory(cf);
		tpl.setKeySerializer(RedisSerializer.string());
		tpl.setValueSerializer(RedisSerializer.byteArray());
		tpl.setHashKeySerializer(RedisSerializer.string());
		tpl.setHashValueSerializer(RedisSerializer.byteArray());
		tpl.afterPropertiesSet();
		return tpl;
	}

	/**
	 * Per-cache serialization so every cache round-trips its OWN value type as plain JSON
	 * — matching the reactive stack, which stores each cache value with a bare
	 * {@code ObjectMapper.findAndRegisterModules()} and reads it back through an explicit
	 * {@code TypeReference} (no embedded {@code @class} type info).
	 *
	 * <p>
	 * Only the {@code users} cache holds {@link UserDto}, so each cache below is bound to
	 * its exact value type rather than sharing a single default serializer:
	 * <ul>
	 * <li>{@code users} → {@link UserDto}</li>
	 * <li>{@code tweets} → {@link TweetDto}</li>
	 * <li>{@code user_tweets} → {@code List<TweetDto>}</li>
	 * <li>{@code popular_hashtags} → {@code List<TweetDto.HashtagDto>}</li>
	 * <li>{@code follow_recommendations} → {@code List<UserDto>}</li>
	 * <li>{@code popular_users} → {@code Map<UUID,Double>}</li>
	 * </ul>
	 *
	 * <p>
	 * The {@code popular_users} binding is load-bearing: Jackson serializes map keys as
	 * strings, so a typed {@code Map<UUID,Double>} serializer is required to reinstate
	 * the UUID keys on read. {@code RecommendationService.fetchPopularUsers()} reads the
	 * map back and does {@code followedIds.contains(key)} against a {@code Set<UUID>} —
	 * String keys would never match.
	 * @param cf the Redis connection factory backing every cache
	 * @param objectMapper the shared Jackson mapper used to build the typed serializers
	 * @param ttl the time-to-live applied to all cache entries
	 * @return the cache manager with one typed configuration per named cache
	 */
	@Bean
	public RedisCacheManager cacheManager(RedisConnectionFactory cf, ObjectMapper objectMapper,
			@Value("${spring.cache.redis.time-to-live}") Duration ttl) {
		JavaType listOfTweetDto = objectMapper.getTypeFactory().constructCollectionType(List.class, TweetDto.class);
		JavaType listOfHashtagDto = objectMapper.getTypeFactory()
			.constructCollectionType(List.class, TweetDto.HashtagDto.class);
		JavaType listOfUserDto = objectMapper.getTypeFactory().constructCollectionType(List.class, UserDto.class);
		JavaType mapOfUuidToDouble = objectMapper.getTypeFactory()
			.constructMapType(LinkedHashMap.class, UUID.class, Double.class);

		RedisCacheConfiguration usersConfig = typedConfig(objectMapper,
				objectMapper.getTypeFactory().constructType(UserDto.class), ttl);
		RedisCacheConfiguration tweetsConfig = typedConfig(objectMapper,
				objectMapper.getTypeFactory().constructType(TweetDto.class), ttl);
		RedisCacheConfiguration userTweetsConfig = typedConfig(objectMapper, listOfTweetDto, ttl);
		RedisCacheConfiguration popularHashtagsConfig = typedConfig(objectMapper, listOfHashtagDto, ttl);
		RedisCacheConfiguration followRecommendationsConfig = typedConfig(objectMapper, listOfUserDto, ttl);
		RedisCacheConfiguration popularUsersConfig = typedConfig(objectMapper, mapOfUuidToDouble, ttl);

		// Default serializer for any not-yet-declared cache: UserDto, preserving
		// the prior default's type (no cache currently relies on it).
		RedisCacheConfiguration defaults = typedConfig(objectMapper,
				objectMapper.getTypeFactory().constructType(UserDto.class), ttl);

		return RedisCacheManager.builder(cf)
			.cacheDefaults(defaults)
			.withCacheConfiguration("users", usersConfig)
			.withCacheConfiguration("tweets", tweetsConfig)
			.withCacheConfiguration("user_tweets", userTweetsConfig)
			.withCacheConfiguration("popular_hashtags", popularHashtagsConfig)
			.withCacheConfiguration("follow_recommendations", followRecommendationsConfig)
			.withCacheConfiguration("popular_users", popularUsersConfig)
			.build();
	}

	private static RedisCacheConfiguration typedConfig(ObjectMapper objectMapper, JavaType type, Duration ttl) {
		Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(objectMapper, type);
		return RedisCacheConfiguration.defaultCacheConfig()
			.entryTtl(ttl)
			.serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
	}

	/**
	 * Blocking RedisTemplate callers each park a thread for a Redis round-trip, so they need
	 * dedicated pooled connections rather than the single shared native one — otherwise the
	 * ioExecutor threads serialize behind one connection's response stream. Unsharing the native
	 * connection makes ordinary RedisTemplate ops draw from the Lettuce pool
	 * (spring.data.redis.lettuce.pool.*). Reactive multiplexes and keeps shareNativeConnection on.
	 * @return a bean post-processor that unshares the native connection on the Lettuce factory
	 */
	@Bean
	static BeanPostProcessor lettuceShareNativeConnectionCustomizer() {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessBeforeInitialization(Object bean, String beanName) {
				if (bean instanceof LettuceConnectionFactory factory) {
					factory.setShareNativeConnection(false);
				}
				return bean;
			}
		};
	}

}
