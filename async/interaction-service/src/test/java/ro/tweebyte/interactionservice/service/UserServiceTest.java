package ro.tweebyte.interactionservice.service;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import ro.tweebyte.interactionservice.client.UserClient;
import ro.tweebyte.interactionservice.model.UserDto;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class UserServiceTest {

    @Mock
    private UserClient userClient;

    @InjectMocks
    private UserService userService;

    @Test
    void testGetUserSummary() {
        UUID userId = UUID.randomUUID();
        UserDto expected = new UserDto();

        when(userClient.getUserSummary(eq(userId))).thenReturn(expected);

        UserDto result = userService.getUserSummary(userId);

        assertEquals(expected, result);
        verify(userClient).getUserSummary(userId);
    }

}