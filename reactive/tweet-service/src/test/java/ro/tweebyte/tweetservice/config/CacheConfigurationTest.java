package ro.tweebyte.tweetservice.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CacheConfigurationTest {

	private final CacheConfiguration configuration = new CacheConfiguration();

	@Mock
	private RedisConnectionFactory redisConnectionFactory;

	@Mock
	private ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;

	@Test
	void cacheManager_ShouldReturnRedisCacheManager() {
		var cacheManager = configuration.cacheManager(redisConnectionFactory);
		assertThat(cacheManager).isNotNull();
	}

	@Test
	void redisTemplate_ShouldReturnReactiveRedisTemplate() {
		var redisTemplate = configuration.redisTemplate(reactiveRedisConnectionFactory);
		assertThat(redisTemplate).isInstanceOf(ReactiveRedisTemplate.class);
	}
}
