package ro.tweebyte.interactionservice.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ro.tweebyte.interactionservice.exception.ClientException;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Branch-coverage tests for ClientUtil — covers the non-200 arm and parse
 * failure path that ClientUtilTest's happy-path test does not reach.
 */
class ClientUtilBranchTest {

    @Test
    void parseResponse_non200_throwsClientException() {
        ClientUtil util = new ClientUtil(new ObjectMapper());
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(500);

        ClientException ex = assertThrows(ClientException.class,
            () -> util.parseResponse(response, Object.class));
        assertEquals(response, ex.getResponse());
    }

    @Test
    void parseResponse_404_throwsClientException() {
        ClientUtil util = new ClientUtil(new ObjectMapper());
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(404);

        ClientException ex = assertThrows(ClientException.class,
            () -> util.parseResponse(response, Object.class));
        assertEquals(404, ex.getResponse().statusCode());
    }

    @Test
    void parseResponse_malformedJson_propagates() {
        // 200 status but body fails to parse — @SneakyThrows propagates as-is.
        ClientUtil util = new ClientUtil(new ObjectMapper());
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{not valid json");

        assertThrows(Exception.class,
            () -> util.parseResponse(response, Object.class));
    }
}
