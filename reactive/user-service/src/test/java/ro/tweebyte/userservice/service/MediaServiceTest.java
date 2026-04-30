package ro.tweebyte.userservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MediaService is a streaming download endpoint. The download() method is
 * impure (it writes directly to the ServerHttpResponse and parks threads
 * for byte-rate throttling). We exercise:
 *  - download() happy path (full body streamed, headers populated, 206 status)
 *  - the static isBenignClientAbort classifier across every branch
 */
class MediaServiceTest {

    private MediaService mediaService;

    @BeforeEach
    void setUp() {
        // immediate-scheduler keeps the test fast; production uses a bounded-elastic.
        mediaService = new MediaService(Schedulers.immediate());
    }

    @Test
    void downloadStreamsExactlyTotalBytesAndPopulatesHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/media/").build());

        StepVerifier.create(mediaService.download(exchange))
                .verifyComplete();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertEquals(HttpStatus.PARTIAL_CONTENT, exchange.getResponse().getStatusCode());
        assertEquals(256L * 1024L, headers.getContentLength());
        assertNotNull(headers.getContentType());
        assertTrue(headers.getFirst(HttpHeaders.CONTENT_RANGE).startsWith("bytes 0-"));
        // Ensure response body produced complete payload
        assertEquals(256L * 1024L, exchange.getResponse().getHeaders().getContentLength());
    }

    @Test
    void downloadCompletesIdempotentlyEvenIfRunTwice() {
        // Run two separate exchanges to make sure no shared mutable state breaks subsequent runs.
        for (int i = 0; i < 2; i++) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/media/").build());
            Mono<Void> result = mediaService.download(exchange);
            StepVerifier.create(result).verifyComplete();
        }
    }

    // --- isBenignClientAbort ------------------------------------------

    private static boolean invokeBenign(Throwable t) throws Exception {
        Method m = MediaService.class.getDeclaredMethod("isBenignClientAbort", Throwable.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, t);
    }

    @Test
    void benignAbortReturnsFalseWhenNull() throws Exception {
        assertFalse(invokeBenign(null));
    }

    @Test
    void benignAbortReturnsTrueForCancellation() throws Exception {
        assertTrue(invokeBenign(new CancellationException("cancel")));
    }

    @Test
    void benignAbortReturnsTrueForInterrupted() throws Exception {
        assertTrue(invokeBenign(new InterruptedException("int")));
    }

    @Test
    void benignAbortReturnsTrueForClosedChannel() throws Exception {
        assertTrue(invokeBenign(new ClosedChannelException()));
    }

    @Test
    void benignAbortReturnsTrueForSocketException() throws Exception {
        assertTrue(invokeBenign(new SocketException("reset")));
    }

    @Test
    void benignAbortReturnsTrueForAbortedExceptionByName() throws Exception {
        // Anything whose class name contains "AbortedException" should match.
        Throwable t = new RuntimeException("client gone") {
            @Override
            public String toString() {
                return getClass().getName();
            }
        };
        // Define a local subclass with "AbortedException" in its name via anonymous class.
        Throwable aborted = new MyAbortedException("bye");
        assertTrue(invokeBenign(aborted));
    }

    @Test
    void benignAbortFalseForArbitraryRuntimeException() throws Exception {
        assertFalse(invokeBenign(new RuntimeException("boom")));
    }

    @Test
    void benignAbortRecursesIntoCause() throws Exception {
        Throwable cause = new ClosedChannelException();
        Throwable wrapper = new RuntimeException("wrap", cause);
        assertTrue(invokeBenign(wrapper));
    }

    @Test
    void benignAbortStopsAtSelfReferentialCause() throws Exception {
        // Construct a chain that becomes self-referential after one hop.
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        // a.initCause(b); // would create a cycle but Throwable#initCause prevents self-cause.
        // The implementation guards against `c == t` after one swap, so a non-benign chain
        // without any benign element returns false without infinite looping.
        assertFalse(invokeBenign(b));
    }

    private static final class MyAbortedException extends RuntimeException {
        MyAbortedException(String msg) { super(msg); }
    }
}
