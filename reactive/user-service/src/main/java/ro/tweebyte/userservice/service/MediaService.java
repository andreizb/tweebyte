package ro.tweebyte.userservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SynchronousSink;
import reactor.core.scheduler.Scheduler;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

@Service
@RequiredArgsConstructor
public class MediaService {

    private static final long TOTAL_BYTES   = 256L * 1024L;
    private static final int  CHUNK_BYTES   = 64 * 1024;
    private static final long BYTES_PER_SEC = 6_250_000L;
    private static final String FILENAME    = "payload.bin";

    private final Scheduler readScheduler;

    public Mono<Void> download(ServerWebExchange exchange) {
        ServerHttpResponse resp = exchange.getResponse();

        // headers
        resp.setStatusCode(HttpStatus.PARTIAL_CONTENT);
        HttpHeaders h = resp.getHeaders();
        h.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        h.setContentLength(TOTAL_BYTES);
        h.setContentDispositionFormData("inline", FILENAME);
        h.set(HttpHeaders.CONTENT_RANGE, "bytes 0-" + (TOTAL_BYTES - 1) + "/" + TOTAL_BYTES);

        DataBufferFactory bf = resp.bufferFactory();
        AtomicLong remaining = new AtomicLong(TOTAL_BYTES);
        AtomicBoolean cancelled = new AtomicBoolean(false);

        Flux<DataBuffer> body = Flux.defer(() ->
                Flux.generate((SynchronousSink<DataBuffer> sink) -> {
                    if (cancelled.get()) { sink.complete(); return; }

                    long rem = remaining.get();
                    if (rem <= 0) { sink.complete(); return; }

                    int n = (int) Math.min(CHUNK_BYTES, rem);
                    byte[] buf = new byte[n];

                    for (int i = 0; i < n; i++) {
                        buf[i] = (byte) (i & 0xFF);
                    }

                    DataBuffer db = bf.allocateBuffer(n);
                    db.write(buf);
                    remaining.addAndGet(-n);
                    sink.next(db);

                    long nanos = (long)((n * 1_000_000_000.0) / BYTES_PER_SEC);
                    if (nanos > 0) LockSupport.parkNanos(nanos);
                })
            )
            .subscribeOn(readScheduler)
            .doOnCancel(() -> cancelled.set(true))
            .doOnTerminate(() -> cancelled.set(true))
            .doOnDiscard(DataBuffer.class, DataBufferUtils::release);

        return resp.writeWith(body)
            .onErrorResume(t -> isBenignClientAbort(t) ? Mono.empty() : Mono.error(t));
    }

    private static boolean isBenignClientAbort(Throwable t) {
        if (t == null) return false;
        if (t instanceof CancellationException)  return true;
        if (t instanceof InterruptedException)   return true;
        if (t instanceof ClosedChannelException) return true;
        if (t instanceof java.net.SocketException) return true;
        String name = t.getClass().getName();
        if (name.contains("AbortedException")) return true;

        Throwable c = t.getCause();
        while (c != null && c != t) {
            if (isBenignClientAbort(c)) return true;
            t = c; c = c.getCause();
        }
        return false;
    }

}
