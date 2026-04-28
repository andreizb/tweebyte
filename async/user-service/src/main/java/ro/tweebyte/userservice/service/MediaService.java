package ro.tweebyte.userservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStream;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.LockSupport;

@Service
@RequiredArgsConstructor
public class MediaService {

    private static final long TOTAL_BYTES   = 256L * 1024L;
    private static final int  CHUNK_BYTES   = 64 * 1024;
    private static final long BYTES_PER_SEC = 6_250_000L;
    private static final String FILENAME    = "payload.bin";

    @Async
    public CompletableFuture<ResponseEntity<StreamingResponseBody>> download() {
        StreamingResponseBody srb = (OutputStream out) -> {
            long remaining = TOTAL_BYTES;
            try {
                while (remaining > 0) {
                    int n = (int) Math.min(CHUNK_BYTES, remaining);

                    byte[] buf = new byte[n];
                    for (int i = 0; i < n; i++) {
                        buf[i] = (byte) (i & 0xFF);
                    }

                    out.write(buf, 0, n);
                    remaining -= n;

                    long nanos = (long) ((n * 1_000_000_000.0) / BYTES_PER_SEC);
                    if (nanos > 0) LockSupport.parkNanos(nanos);
                }
                out.flush();
            } catch (ClosedChannelException | SocketException ignored) {
            } catch (Exception e) {
                for (Throwable c = e; c != null; c = c.getCause()) {
                    if (c.getClass().getName().endsWith("ClientAbortException")) return;
                }
                throw e;
            }
        };

        ResponseEntity<StreamingResponseBody> resp = ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + FILENAME + "\"")
            .header(HttpHeaders.CONTENT_RANGE, "bytes 0-" + (TOTAL_BYTES - 1) + "/" + TOTAL_BYTES)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(TOTAL_BYTES)
            .body(srb);

        return CompletableFuture.completedFuture(resp);
    }

}
