package ro.tweebyte.userservice.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import ro.tweebyte.userservice.model.AuthenticationResponse;

import java.io.IOException;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
class ClientUtilTest {

    private ClientUtil clientUtil;
    private HttpResponse<String> mockResponse;

    @BeforeEach
    void setUp() {
        clientUtil = new ClientUtil();
        mockResponse = mock(HttpResponse.class);
    }

    @Test
    void testParseResponseSuccessfully() {
        when(mockResponse.body()).thenReturn("{\"key\":\"value\"}");

        TestDto result = clientUtil.parseResponse(mockResponse, TestDto.class);

        assertNotNull(result);
        assertEquals("value", result.getKey());
    }

    @Test
    void testParseResponseThrowsException() {
        when(mockResponse.body()).thenReturn("invalid json");

        assertThrows(JsonProcessingException.class, () -> {
            clientUtil.parseResponse(mockResponse, TestDto.class);
        });
    }

    static class TestDto {
        private String key;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }
    }

}
