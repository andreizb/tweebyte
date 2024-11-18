package ro.tweebyte.interactionservice.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import ro.tweebyte.interactionservice.exception.ClientException;
import ro.tweebyte.interactionservice.exception.UserNotFoundException;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.util.ClientUtil;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UserClientTest {

    private UserClient userClient;

    @Mock
    private ClientUtil clientUtil;

    @Mock
    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userClient = new UserClient(clientUtil);
        ReflectionTestUtils.setField(userClient, "client", httpClient);
        ReflectionTestUtils.setField(userClient, "BASE_URL", "http://localhost");
    }

    @Test
    void testGetUserSummary() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UserDto expectedUserDto = new UserDto();

        HttpResponse<String> mockHttpResponse = Mockito.mock(HttpResponse.class);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenReturn(mockHttpResponse);

        when(clientUtil.parseResponse(any(), eq(UserDto.class))).thenReturn(expectedUserDto);

        // When
        UserDto resultUserDto = userClient.getUserSummary(userId);

        // Then
        assertEquals(expectedUserDto, resultUserDto);
    }

    @Test
    void testGetUserSummaryException() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        HttpResponse<String> mockHttpResponse = Mockito.mock(HttpResponse.class);

        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenReturn(mockHttpResponse);

        HttpResponse<String> response = Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(404);

        when(clientUtil.parseResponse(any(), eq(UserDto.class))).thenThrow(new ClientException(response));

        // When and Then
        assertThrows(UserNotFoundException.class, () -> userClient.getUserSummary(userId));
    }

}