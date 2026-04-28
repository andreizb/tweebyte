package ro.tweebyte.interactionservice.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

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

}
