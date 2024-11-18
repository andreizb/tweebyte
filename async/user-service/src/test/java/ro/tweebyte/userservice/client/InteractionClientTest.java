package ro.tweebyte.userservice.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ro.tweebyte.userservice.exception.FollowRetrievingException;
import ro.tweebyte.userservice.util.ClientUtil;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InteractionClientTest {

    @Mock
    private ClientUtil clientUtil;

    @Mock
    private HttpClient httpClient;

    private InteractionClient interactionClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        interactionClient = new InteractionClient(clientUtil);
    }

    @Test
    void testGetFollowersCount() throws ExecutionException, InterruptedException {
        UUID userId = UUID.randomUUID();
        HttpResponse<String> mockHttpResponse = mock(HttpResponse.class);
        when(httpClient.sendAsync(any(), eq(HttpResponse.BodyHandlers.ofString()))).thenReturn(CompletableFuture.completedFuture(mockHttpResponse));
        when(clientUtil.parseResponse(mockHttpResponse, Long.class)).thenReturn(10L);

        ReflectionTestUtils.setField(interactionClient, "client", httpClient);
        ReflectionTestUtils.setField(interactionClient, "BASE_URL", "http://localhost");
        CompletableFuture<Long> resultFuture = interactionClient.getFollowersCount(userId, "AUTH_TOKEN");
        Long result = resultFuture.get();

        assertEquals(10L, result);
        verify(httpClient).sendAsync(any(), any());
        verify(clientUtil).parseResponse(mockHttpResponse, Long.class);
    }

    @Test
    void testGetFollowingCount() throws ExecutionException, InterruptedException {
        UUID userId = UUID.randomUUID();
        HttpResponse<String> mockHttpResponse = mock(HttpResponse.class);
        when(httpClient.sendAsync(any(), eq(HttpResponse.BodyHandlers.ofString()))).thenReturn(CompletableFuture.completedFuture(mockHttpResponse));
        when(clientUtil.parseResponse(mockHttpResponse, Long.class)).thenReturn(10L);

        ReflectionTestUtils.setField(interactionClient, "client", httpClient);
        ReflectionTestUtils.setField(interactionClient, "BASE_URL", "http://localhost");
        CompletableFuture<Long> resultFuture = interactionClient.getFollowingCount(userId, "AUTH_TOKEN");
        Long result = resultFuture.get();

        assertEquals(10L, result);
        verify(httpClient).sendAsync(any(), any());
        verify(clientUtil).parseResponse(mockHttpResponse, Long.class);
    }

    @Test
    void testGetFollowersCountThrowsException() {
        UUID userId = UUID.randomUUID();
        ReflectionTestUtils.setField(interactionClient, "BASE_URL", "http://localhost");

        when(httpClient.sendAsync(any(), eq(HttpResponse.BodyHandlers.ofString()))).thenThrow(new RuntimeException());

        ReflectionTestUtils.setField(interactionClient, "client", httpClient);

        assertThrows(FollowRetrievingException.class, () -> {
            interactionClient.getFollowersCount(userId, "AUTH_TOKEN").join();
        });
    }

    @Test
    void testGetFollowingCountThrowsException() {
        UUID userId = UUID.randomUUID();
        ReflectionTestUtils.setField(interactionClient, "BASE_URL", "http://localhost");

        when(httpClient.sendAsync(any(), eq(HttpResponse.BodyHandlers.ofString()))).thenThrow(new RuntimeException());

        ReflectionTestUtils.setField(interactionClient, "client", httpClient);

        assertThrows(FollowRetrievingException.class, () -> {
            interactionClient.getFollowingCount(userId, "AUTH_TOKEN").join();
        });
    }

}