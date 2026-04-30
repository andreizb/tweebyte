package ro.tweebyte.interactionservice.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ro.tweebyte.interactionservice.exception.InteractionException;
import ro.tweebyte.interactionservice.util.ClientUtil;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Branch-coverage test for UserClient — covers the generic-Exception catch
 * arm not exercised by UserClientTest.
 */
@ExtendWith(MockitoExtension.class)
class UserClientBranchTest {

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
        ReflectionTestUtils.setField(userClient, "BASE_URL", "http://localhost/");
    }

    @Test
    void getUserSummary_ioException_wrapped() throws Exception {
        UUID userId = UUID.randomUUID();
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
            .thenThrow(new IOException("net"));
        assertThrows(InteractionException.class, () -> userClient.getUserSummary(userId));
    }
}
