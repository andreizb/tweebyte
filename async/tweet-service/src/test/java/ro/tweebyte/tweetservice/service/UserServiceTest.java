package ro.tweebyte.tweetservice.service;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import ro.tweebyte.tweetservice.client.UserClient;
import ro.tweebyte.tweetservice.model.UserDto;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
public class UserServiceTest {

    @Mock
    private UserClient userClient;

    @InjectMocks
    private UserService userService;

    @Test
    void testGetUserId() {
        String userName = "testUser";
        UUID userId = UUID.randomUUID();
        UserDto userDto = new UserDto();
        userDto.setId(userId);

        when(userClient.getUserSummary(any(String.class))).thenReturn(userDto);

        UUID result = userService.getUserId(userName);

        assertEquals(userId, result);
    }

    @Test
    void testGetUserSummary() {
        UUID userId = UUID.randomUUID();
        UserDto userDto = new UserDto();
        userDto.setId(userId);

        when(userClient.getUserSummary(any(UUID.class))).thenReturn(userDto);

        UserDto result = userService.getUserSummary(userId);

        assertEquals(userDto, result);
    }
}