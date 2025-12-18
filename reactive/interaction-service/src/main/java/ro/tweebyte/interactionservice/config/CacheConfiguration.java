package ro.tweebyte.interactionservice.config;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.boot.CommandLineRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.*;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfiguration {

    @Bean
    public ReactiveRedisTemplate<String, byte[]> reactiveRedisTemplate(LettuceConnectionFactory cf) {
        RedisSerializationContext<String, byte[]> ctx =
            RedisSerializationContext.<String, byte[]>newSerializationContext(RedisSerializer.string())
                .value(RedisSerializer.byteArray())
                .hashKey(RedisSerializer.string())
                .hashValue(RedisSerializer.byteArray())
                .build();

        return new ReactiveRedisTemplate<>(cf, ctx);
    }

}
