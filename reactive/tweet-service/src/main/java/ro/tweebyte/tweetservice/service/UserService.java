package ro.tweebyte.tweetservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ro.tweebyte.tweetservice.client.UserClient;
import ro.tweebyte.tweetservice.model.UserDto;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String USER_ID_KEY_PREFIX = "userId:";
    private static final String USER_SUMMARY_KEY_PREFIX = "users:";

    private final UserClient userClient;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    public Mono<UUID> getUserId(String userName) {
        String key = USER_ID_KEY_PREFIX + userName;
        return redisTemplate.opsForValue().get(key)
                .cast(UUID.class)
                .switchIfEmpty(userClient.getUserSummary(userName)
                        .map(UserDto::getId)
                        .doOnNext(userId -> redisTemplate.opsForValue().set(key, userId)));
    }

    public Mono<UserDto> getUserSummary(UUID userId) {
        String key = USER_SUMMARY_KEY_PREFIX + userId;
        return redisTemplate.opsForValue().get(key)
                .cast(UserDto.class)
                .switchIfEmpty(userClient.getUserSummary(userId)
                        .doOnNext(userDto -> redisTemplate.opsForValue().set(key, userDto)));
    }

}
