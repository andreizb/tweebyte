package ro.tweebyte.userservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaServiceTest {

    private final MediaService mediaService = new MediaService();

    @Test
    void downloadReturnsPartialContentResponse() throws ExecutionException, InterruptedException {
        CompletableFuture<ResponseEntity<StreamingResponseBody>> future = mediaService.download();
        ResponseEntity<StreamingResponseBody> response = future.get();

        assertNotNull(response);
        assertEquals(HttpStatus.PARTIAL_CONTENT, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, response.getHeaders().getContentType());
        assertEquals(256L * 1024L, response.getHeaders().getContentLength());
        assertNotNull(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertNotNull(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE));
        assertNotNull(response.getBody());
    }

    @Test
    void streamingBodyWritesAllBytes() throws IOException, ExecutionException, InterruptedException {
        ResponseEntity<StreamingResponseBody> resp = mediaService.download().get();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        resp.getBody().writeTo(baos);
        assertEquals(256 * 1024, baos.size());
    }

    @Test
    void streamingBodySwallowsClosedChannelException() throws IOException, ExecutionException, InterruptedException {
        ResponseEntity<StreamingResponseBody> resp = mediaService.download().get();
        OutputStream throwing = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new ClosedChannelException();
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                throw new ClosedChannelException();
            }
        };
        // Should not throw
        resp.getBody().writeTo(throwing);
    }

    @Test
    void streamingBodySwallowsSocketException() throws IOException, ExecutionException, InterruptedException {
        ResponseEntity<StreamingResponseBody> resp = mediaService.download().get();
        OutputStream throwing = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new SocketException("broken pipe");
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                throw new SocketException("broken pipe");
            }
        };
        resp.getBody().writeTo(throwing);
    }

    @Test
    void streamingBodySwallowsClientAbortException() throws Exception {
        // Build a custom exception that ends with ClientAbortException in chain
        class ClientAbortException extends IOException {
            ClientAbortException(String m) { super(m); }
        }
        ResponseEntity<StreamingResponseBody> resp = mediaService.download().get();
        IOException abort = new ClientAbortException("aborted");
        OutputStream throwing = new OutputStream() {
            @Override
            public void write(int b) throws IOException { throw abort; }
            @Override
            public void write(byte[] b, int off, int len) throws IOException { throw abort; }
        };
        // The class name ends with "ClientAbortException" so it should be swallowed
        resp.getBody().writeTo(throwing);
    }

    @Test
    void streamingBodyRethrowsUnknownIOException() throws ExecutionException, InterruptedException {
        ResponseEntity<StreamingResponseBody> resp = mediaService.download().get();
        OutputStream throwing = new OutputStream() {
            @Override
            public void write(int b) throws IOException { throw new IOException("disk full"); }
            @Override
            public void write(byte[] b, int off, int len) throws IOException { throw new IOException("disk full"); }
        };
        assertThrows(IOException.class, () -> resp.getBody().writeTo(throwing));
    }
}
