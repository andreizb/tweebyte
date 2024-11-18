package ro.tweebyte.interactionservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ro.tweebyte.interactionservice.client.UserClient;
import ro.tweebyte.interactionservice.model.UserDto;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserClient userClient;

    @Cacheable(value = "users", key = "#userId", unless = "#result == null")
    public UserDto getUserSummary(UUID userId) {
        return userClient.getUserSummary(userId);
    }

}
