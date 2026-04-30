package ro.tweebyte.interactionservice.exception;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/** Mirrors reactive/.../exception/ClientExceptionTest. */
class ClientExceptionTest {

    @SuppressWarnings("unchecked")
    @Test
    void getResponseExposesTheConstructorArgument() {
        HttpResponse<String> response = (HttpResponse<String>) Mockito.mock(HttpResponse.class);
        Mockito.when(response.statusCode()).thenReturn(404);
        ClientException ex = new ClientException(response);
        assertSame(response, ex.getResponse());
        assertEquals(404, ex.getResponse().statusCode());
    }

    @Test
    void isRuntimeException() {
        ClientException ex = new ClientException(null);
        assertTrue(ex instanceof RuntimeException);
    }
}
