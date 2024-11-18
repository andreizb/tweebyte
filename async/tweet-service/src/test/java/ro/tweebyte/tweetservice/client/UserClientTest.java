package ro.tweebyte.tweetservice.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ro.tweebyte.tweetservice.exception.UserNotFoundException;
import ro.tweebyte.tweetservice.model.UserDto;
import ro.tweebyte.tweetservice.util.ClientUtil;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserClientTest {

    @Mock
    private ClientUtil clientUtil;

    @Mock
    private HttpClient httpClient;

    private UserClient userClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userClient = new UserClient(clientUtil);
        ReflectionTestUtils.setField(userClient, "client", httpClient);
        ReflectionTestUtils.setField(userClient, "BASE_URL", "http://localhost");
    }

    @Test
    void getUserSummaryByUsername() throws ExecutionException, InterruptedException, IOException {
        String userName = "testuser";
        UserDto expectedResult = new UserDto();
        HttpResponse<String> mockHttpResponse = mock(HttpResponse.class);
        when(httpClient.send(any(), eq(HttpResponse.BodyHandlers.ofString()))).thenReturn(mockHttpResponse);
        when(clientUtil.parseResponse(any(), eq(UserDto.class))).thenReturn(expectedResult);

        UserDto result = userClient.getUserSummary(userName);

        assertEquals(expectedResult, result);
    }

    @Test
    void getUserSummaryByUserId() throws ExecutionException, InterruptedException, IOException {
        UUID userId = UUID.randomUUID();
        UserDto expectedResult = new UserDto();
        HttpResponse<String> mockHttpResponse = mock(HttpResponse.class);
        when(httpClient.send(any(), eq(HttpResponse.BodyHandlers.ofString()))).thenReturn(mockHttpResponse);
        when(clientUtil.parseResponse(any(), eq(UserDto.class))).thenReturn(expectedResult);

        UserDto result = userClient.getUserSummary(userId);

        assertEquals(expectedResult, result);
    }

    @Test
    void getUserSummaryByName_exceptionThrown() {
        String userName = "testUser";
        when(clientUtil.parseResponse(any(), eq(UserDto.class))).thenThrow(new UserNotFoundException("User not found for name: " + userName));

        assertThrows(UserNotFoundException.class, () -> userClient.getUserSummary(userName));
    }

    @Test
    void getUserSummaryById_exceptionThrown() {
        UUID userId = UUID.randomUUID();
        when(clientUtil.parseResponse(any(), eq(UserDto.class))).thenThrow(new UserNotFoundException("User not found for id: " + userId));

        assertThrows(UserNotFoundException.class, () -> userClient.getUserSummary(userId));
    }

}