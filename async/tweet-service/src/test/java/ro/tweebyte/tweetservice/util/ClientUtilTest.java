package ro.tweebyte.tweetservice.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import ro.tweebyte.tweetservice.exception.TweetException;

import java.io.IOException;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

    @Test
    void testParseResponseThrowsExceptionForNon200Status() {
        ClientUtil clientUtil = new ClientUtil(new ObjectMapper());
        HttpResponse<String> response = mock(HttpResponse.class);

        when(response.statusCode()).thenReturn(404); // Non-200 status
        when(response.body()).thenReturn(getJsonResponse());

        assertThrows(TweetException.class, () -> clientUtil.parseResponse(response, TestResponse.class));
    }

    @Test
    void testParseResponseTypeRef() throws IOException {
        ClientUtil clientUtil = new ClientUtil(new ObjectMapper());
        HttpResponse<String> response = mock(HttpResponse.class);

        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(getJsonResponse());

        TestResponse parsedResponse = clientUtil.parseResponse(response, new TypeReference<TestResponse>() {});

        assertEquals("Test Name", parsedResponse.getName());
        assertEquals(42, parsedResponse.getAge());
    }

    @Test
    void testParseResponseTypeRefThrowsExceptionForNon200Status() {
        ClientUtil clientUtil = new ClientUtil(new ObjectMapper());
        HttpResponse<String> response = mock(HttpResponse.class);

        when(response.statusCode()).thenReturn(400); // Non-200 status
        when(response.body()).thenReturn(getJsonResponse());

        assertThrows(TweetException.class, () -> clientUtil.parseResponse(response, new TypeReference<TestResponse>() {}));
    }

    @Test
    void testGenerateBody() throws IOException {
        ClientUtil clientUtil = new ClientUtil(new ObjectMapper());
        TestResponse testResponse = new TestResponse();
        testResponse.name = "Test Name";
        testResponse.age = 42L;

        String json = clientUtil.generateBody(testResponse);

        assertEquals("{\"name\":\"Test Name\",\"age\":42}", json);
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