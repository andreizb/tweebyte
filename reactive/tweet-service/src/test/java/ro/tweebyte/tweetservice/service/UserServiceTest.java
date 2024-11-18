package ro.tweebyte.tweetservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ro.tweebyte.tweetservice.client.UserClient;
import ro.tweebyte.tweetservice.model.UserDto;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserClient userClient;

    @Mock
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, Object> valueOperations;

    private UUID userId;
    private UserDto userDto;

    @BeforeEach
    void setup() {
        userId = UUID.randomUUID();
        userDto = new UserDto();
        userDto.setId(userId);
        userDto.setUserName("sampleUser");
    }

    @Test
    void getUserId_CacheMiss() {
        String key = "userId:sampleUser";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any())).thenReturn(Mono.empty());
        when(userClient.getUserSummary("sampleUser")).thenReturn(Mono.just(userDto));
        when(valueOperations.set(key, userId)).thenReturn(Mono.empty());

        StepVerifier.create(userService.getUserId("sampleUser"))
            .expectNext(userId)
            .verifyComplete();

        verify(valueOperations).get(key);
        verify(userClient).getUserSummary("sampleUser");
        verify(valueOperations).set(key, userId);
    }

    @Test
    void getUserSummary_CacheMiss() {
        String key = "users:" + userId;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn(Mono.empty());
        when(userClient.getUserSummary(userId)).thenReturn(Mono.just(userDto));
        when(valueOperations.set(key, userDto)).thenReturn(Mono.empty());

        StepVerifier.create(userService.getUserSummary(userId))
            .expectNext(userDto)
            .verifyComplete();

        verify(valueOperations).get(key);
        verify(userClient).getUserSummary(userId);
        verify(valueOperations).set(key, userDto);
    }
}