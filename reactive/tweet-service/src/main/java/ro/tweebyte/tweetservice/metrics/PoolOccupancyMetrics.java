package ro.tweebyte.tweetservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.SignalType;

import java.util.concurrent.atomic.AtomicLong;

/**
 * In-flight subscription gauge + per-terminal-signal counters for AI streaming.
 *
 * <p>Decrements live in {@link #onTerminate(SignalType)} so an erroring upstream
 * (which fires neither {@code doOnComplete} nor {@code doOnCancel}) can't leak
 * the gauge under sustained load — every subscription pairs with exactly one
 * terminal signal via {@code doFinally}.
 */
@Component
@RequiredArgsConstructor
public class PoolOccupancyMetrics {

    private final MeterRegistry registry;

    private final AtomicLong inFlightStreams = new AtomicLong();
    private Counter cancelledStreams;
    private Counter completedStreams;
    private Counter erroredStreams;

    @PostConstruct
    public void register() {
        Gauge.builder("tweebyte.reactive.inflight.streams", inFlightStreams, AtomicLong::get)
                .description("Number of in-flight AI streaming subscriptions on the reactive stack")
                .register(registry);
        this.cancelledStreams = Counter.builder("tweebyte.reactive.streams.cancelled")
                .description("AI streams terminated by downstream cancellation (client disconnect)")
                .register(registry);
        this.completedStreams = Counter.builder("tweebyte.reactive.streams.completed")
                .description("AI streams that completed successfully")
                .register(registry);
        this.erroredStreams = Counter.builder("tweebyte.reactive.streams.errored")
                .description("AI streams terminated by an upstream error signal")
                .register(registry);
    }

    public void onSubscribe() {
        inFlightStreams.incrementAndGet();
    }

    public void onTerminate(SignalType signalType) {
        inFlightStreams.decrementAndGet();
        switch (signalType) {
            case ON_COMPLETE -> {
                if (completedStreams != null) completedStreams.increment();
            }
            case CANCEL -> {
                if (cancelledStreams != null) cancelledStreams.increment();
            }
            case ON_ERROR -> {
                if (erroredStreams != null) erroredStreams.increment();
            }
            default -> { /* no-op for non-terminal signals */ }
        }
    }
}
