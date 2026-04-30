package ro.tweebyte.interactionservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ro.tweebyte.interactionservice.client.UserClient;
import ro.tweebyte.interactionservice.model.UserDto;

import java.util.UUID;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Coverage for the UserService cache-hit and cache-hit-error branches that
 * UserServiceTest never exercises (it only drives the cache-miss path).
 * Targets lines 28–32: the .flatMap(bytes -> objectMapper.readValue(...))
 * branch and its catch block.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceBranchTest {

    @Mock
    private UserClient userClient;

    @Mock
    private ReactiveRedisTemplate<String, byte[]> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, byte[]> valueOps;

    @InjectMocks
    private UserService userService;

    private final UUID userId = UUID.randomUUID();
    private final String redisKey = "users::" + userId;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // Defensive stub: switchIfEmpty(...) builds its inner pipeline eagerly,
        // so UserClient.getUserSummary is invoked at construction time even
        // when the cache hit short-circuits the outer Mono.
        lenient().when(userClient.getUserSummary(userId)).thenReturn(Mono.empty());
    }

    @Test
    void getUserSummary_CacheHit_DeserializesFromRedis() throws Exception {
        // Drives the cache-hit flatMap on getUserSummary: cached bytes
        // round-trip through the ObjectMapper and emit the UserDto without
        // ever calling UserClient.getUserSummary.
        UserDto cached = new UserDto();
        cached.setId(userId);
        cached.setUserName("cached-user");
        byte[] payload = new ObjectMapper().findAndRegisterModules().writeValueAsBytes(cached);

        when(valueOps.get(redisKey)).thenReturn(Mono.just(payload));

        StepVerifier.create(userService.getUserSummary(userId))
            .expectNextMatches(u -> userId.equals(u.getId()) && "cached-user".equals(u.getUserName()))
            .verifyComplete();
    }

    @Test
    void getUserSummary_CacheHit_CorruptBytes_ErrorsOut() {
        // Drives the catch arm on getUserSummary: malformed cached bytes
        // surface a Mono.error so the caller observes the JsonProcessingException
        // wrapped in a RuntimeException-style failure signal.
        when(valueOps.get(redisKey)).thenReturn(Mono.just("not-json".getBytes()));

        StepVerifier.create(userService.getUserSummary(userId))
            .expectError()
            .verify();
    }
}
