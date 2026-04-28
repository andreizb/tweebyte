package ro.tweebyte.interactionservice.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
class ClientUtilTest {

    @Test
    void testParseResponse() throws IOException {
        ClientUtil clientUtil = new ClientUtil(new ObjectMapper());
        HttpResponse<String> response = mock(HttpResponse.class);

        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(getJsonResponse());

        TestResponse parsedResponse = clientUtil.parseResponse(response, TestResponse.class);

        assertEquals("Test Name", parsedResponse.getName());
        assertEquals(42, parsedResponse.getAge());
    }

    private String getJsonResponse() {
        return "{ \"name\": \"Test Name\", \"age\": 42 }";
    }

    @Getter
    private static class TestResponse {
        private String name;
        private Long age;
    }

}