/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.config;

import java.time.Duration;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableCaching
public class CacheConfiguration {

	@Bean
	public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
		RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(5));

		return RedisCacheManager.builder(connectionFactory).cacheDefaults(config).build();
	}

	@Bean(name = "redisTemplate")
	public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
		RedisTemplate<String, Object> template = new RedisTemplate<>();
		template.setConnectionFactory(redisConnectionFactory);
		template.setKeySerializer(new StringRedisSerializer());
		template.setValueSerializer(new StringRedisSerializer());
		return template;
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
