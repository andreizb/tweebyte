package ro.tweebyte.tweetservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ro.tweebyte.tweetservice.client.UserClient;
import ro.tweebyte.tweetservice.model.UserDto;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserClient userClient;

    @Cacheable(value = "userIds", key = "#userName", unless = "#result == null")
    public UUID getUserId(String userName) {
        return userClient.getUserSummary(userName).getId();
    }

    @Cacheable(value = "users", key = "#userId", unless = "#result == null")
    public UserDto getUserSummary(UUID userId) {
        return userClient.getUserSummary(userId);
    }

}
