package ro.tweebyte.tweetservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.SignalType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PoolOccupancyMetricsTest {

    private MeterRegistry registry;
    private PoolOccupancyMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new PoolOccupancyMetrics(registry);
        metrics.register();
    }

    private double inFlight() {
        Gauge g = registry.find("tweebyte.reactive.inflight.streams").gauge();
        assertNotNull(g);
        return g.value();
    }

    private double counter(String name) {
        Counter c = registry.find(name).counter();
        assertNotNull(c);
        return c.count();
    }

    @Test
    void registerCreatesInFlightGaugeAndCounters() {
        assertEquals(0.0, inFlight());
        assertEquals(0.0, counter("tweebyte.reactive.streams.completed"));
        assertEquals(0.0, counter("tweebyte.reactive.streams.cancelled"));
        assertEquals(0.0, counter("tweebyte.reactive.streams.errored"));
    }

    @Test
    void onSubscribeIncrementsInFlight() {
        metrics.onSubscribe();
        metrics.onSubscribe();
        assertEquals(2.0, inFlight());
    }

    @Test
    void onTerminateCompleteIncrementsCompletedCounter() {
        metrics.onSubscribe();
        metrics.onTerminate(SignalType.ON_COMPLETE);
        assertEquals(0.0, inFlight());
        assertEquals(1.0, counter("tweebyte.reactive.streams.completed"));
        assertEquals(0.0, counter("tweebyte.reactive.streams.cancelled"));
        assertEquals(0.0, counter("tweebyte.reactive.streams.errored"));
    }

    @Test
    void onTerminateCancelIncrementsCancelledCounter() {
        metrics.onSubscribe();
        metrics.onTerminate(SignalType.CANCEL);
        assertEquals(0.0, inFlight());
        assertEquals(1.0, counter("tweebyte.reactive.streams.cancelled"));
        assertEquals(0.0, counter("tweebyte.reactive.streams.completed"));
    }

    @Test
    void onTerminateErrorIncrementsErroredCounter() {
        metrics.onSubscribe();
        metrics.onTerminate(SignalType.ON_ERROR);
        assertEquals(0.0, inFlight());
        assertEquals(1.0, counter("tweebyte.reactive.streams.errored"));
    }

    @Test
    void onTerminateNonTerminalSignalDecrementsButTouchesNoCounter() {
        metrics.onSubscribe();
        metrics.onTerminate(SignalType.ON_NEXT);
        assertEquals(0.0, inFlight());
        assertEquals(0.0, counter("tweebyte.reactive.streams.completed"));
        assertEquals(0.0, counter("tweebyte.reactive.streams.cancelled"));
        assertEquals(0.0, counter("tweebyte.reactive.streams.errored"));
    }

    @Test
    void onTerminateBeforeRegisterDoesNotNpe() {
        // Construct a fresh metrics instance and call onTerminate without
        // calling register() first — counters are null but the null guards
        // in the switch arms must keep this safe.
        PoolOccupancyMetrics fresh = new PoolOccupancyMetrics(new SimpleMeterRegistry());
        fresh.onSubscribe();
        fresh.onTerminate(SignalType.ON_COMPLETE);
        fresh.onTerminate(SignalType.CANCEL);
        fresh.onTerminate(SignalType.ON_ERROR);
        // No exception — pass.
    }

    @Test
    void multipleSubscribesAndMixedTerminations() {
        metrics.onSubscribe();
        metrics.onSubscribe();
        metrics.onSubscribe();
        metrics.onTerminate(SignalType.ON_COMPLETE);
        metrics.onTerminate(SignalType.CANCEL);
        metrics.onTerminate(SignalType.ON_ERROR);
        assertEquals(0.0, inFlight());
        assertEquals(1.0, counter("tweebyte.reactive.streams.completed"));
        assertEquals(1.0, counter("tweebyte.reactive.streams.cancelled"));
        assertEquals(1.0, counter("tweebyte.reactive.streams.errored"));
    }
}
