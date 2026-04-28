package ro.tweebyte.interactionservice.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

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
