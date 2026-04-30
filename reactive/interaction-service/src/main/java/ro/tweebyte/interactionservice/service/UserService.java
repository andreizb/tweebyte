package ro.tweebyte.interactionservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ro.tweebyte.interactionservice.client.UserClient;
import ro.tweebyte.interactionservice.model.UserDto;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String USER_SUMMARY_KEY_PREFIX = "users::";

    private final UserClient userClient;
    private final ReactiveRedisTemplate<String, byte[]> redisTemplate;
    // register JSR310 module (LocalDateTime support). See TweetService note.
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public Mono<UserDto> getUserSummary(UUID userId) {
        String key = USER_SUMMARY_KEY_PREFIX + userId;
        return redisTemplate.opsForValue().get(key)
                .flatMap(bytes -> {
                    try {
                        return Mono.just(objectMapper.readValue(bytes, UserDto.class));
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                })
                .switchIfEmpty(userClient.getUserSummary(userId)
                        .flatMap(userDto -> {
                            try {
                                byte[] bytes = objectMapper.writeValueAsBytes(userDto);
                                return redisTemplate.opsForValue().set(key, bytes)
                                        .thenReturn(userDto);
                            } catch (Exception e) {
                                return Mono.error(e);
                            }
                        }));
    }

}
