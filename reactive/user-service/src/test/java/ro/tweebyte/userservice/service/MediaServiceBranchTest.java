package ro.tweebyte.userservice.service;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.nio.channels.ClosedChannelException;
import java.time.Duration;

/**
 * Targets the branches that {@link MediaServiceTest} cannot easily reach:
 *  - the {@code onErrorResume} arm in {@link MediaService#download(ServerWebExchange)}
 *    when the downstream write fails with a benign vs. non-benign error
 *  - the {@code cancelled.get()} true-branch inside the generator, by
 *    cancelling the StepVerifier subscription mid-stream
 */
class MediaServiceBranchTest {

    private final MediaService mediaService = new MediaService(Schedulers.immediate());

    /** Wraps an exchange so that response.writeWith(...) propagates a fixed error. */
    private static ServerWebExchange exchangeFailingWrite(Throwable err) {
        MockServerWebExchange base = MockServerWebExchange.from(
                MockServerHttpRequest.get("/media/").build());
        ServerHttpResponse failing = new ServerHttpResponseDecorator(base.getResponse()) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                // Drain the body to release buffers, then fail.
                return super.writeWith(body)
                        .then(Mono.<Void>error(err))
                        // Ensure the error is emitted even if body drain returns empty.
                        .onErrorResume(t -> Mono.<Void>error(err));
            }
        };
        return new ServerWebExchangeDecorator(base) {
            @Override
            public ServerHttpResponse getResponse() {
                return failing;
            }
        };
    }

    @Test
    void downloadSwallowsBenignClientAbortFromWriteWith() {
        ServerWebExchange exchange = exchangeFailingWrite(new ClosedChannelException());

        // Benign abort -> onErrorResume returns Mono.empty()
        StepVerifier.create(mediaService.download(exchange))
                .verifyComplete();
    }

    @Test
    void downloadPropagatesNonBenignErrorFromWriteWith() {
        IllegalStateException boom = new IllegalStateException("downstream blew up");
        ServerWebExchange exchange = exchangeFailingWrite(boom);

        // Non-benign -> onErrorResume returns Mono.error(t)
        StepVerifier.create(mediaService.download(exchange))
                .expectErrorMatches(t -> t instanceof IllegalStateException
                        && "downstream blew up".equals(t.getMessage()))
                .verify();
    }

    @Test
    void downloadHonoursCancellationFromSubscriber() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/media/").build());

        // Cancelling mid-stream forces the cancelled.get() == true branch
        // inside the Flux.generate sink as well as the doOnCancel hook.
        StepVerifier.create(mediaService.download(exchange))
                .thenAwait(Duration.ofMillis(1))
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }
}
