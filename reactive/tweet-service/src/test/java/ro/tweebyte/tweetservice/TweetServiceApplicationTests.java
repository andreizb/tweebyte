package ro.tweebyte.tweetservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.ReactiveRedisTemplate;

@SpringBootTest(properties = "spring.data.redis.repositories.enabled=false")
class TweetServiceApplicationTests {

    @MockBean
    private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @Test
    void contextLoads() {
    }

}
