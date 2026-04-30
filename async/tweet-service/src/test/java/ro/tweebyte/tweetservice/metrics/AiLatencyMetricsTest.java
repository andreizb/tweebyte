package ro.tweebyte.tweetservice.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class AiLatencyMetricsTest {

    private MeterRegistry registry;
    private AiLatencyMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AiLatencyMetrics(registry);
    }

    @Test
    void registersExpectedTimers() {
        assertNotNull(registry.find("tweebyte.ai.ttft").timer());
        assertNotNull(registry.find("tweebyte.ai.itl").timer());
        assertNotNull(registry.find("tweebyte.ai.tool").timer());
        assertNotNull(registry.find("tweebyte.ai.serialize").timer());
        // The three pre-registered outcome variants must all be present so the
        // first prometheus scrape exposes every series.
        assertNotNull(registry.find("tweebyte.ai.e2e").tag("outcome", "success").timer());
        assertNotNull(registry.find("tweebyte.ai.e2e").tag("outcome", "error").timer());
        assertNotNull(registry.find("tweebyte.ai.e2e").tag("outcome", "cancel").timer());
    }

    @Test
    void recordTtftIncrementsTimer() {
        metrics.recordTtft(Duration.ofMillis(10));
        assertEquals(1, registry.find("tweebyte.ai.ttft").timer().count());
    }

    @Test
    void recordItlIncrementsTimer() {
        metrics.recordItl(Duration.ofMillis(5));
        assertEquals(1, registry.find("tweebyte.ai.itl").timer().count());
    }

    @Test
    void recordToolCallIncrementsTimer() {
        metrics.recordToolCall(Duration.ofMillis(20));
        assertEquals(1, registry.find("tweebyte.ai.tool").timer().count());
    }

    @Test
    void recordSerializeIncrementsTimer() {
        metrics.recordSerialize(Duration.ofMillis(1));
        assertEquals(1, registry.find("tweebyte.ai.serialize").timer().count());
    }

    @Test
    void recordEndToEndDispatchesByOutcome() {
        metrics.recordEndToEnd(Duration.ofMillis(100), AiLatencyMetrics.OUTCOME_SUCCESS);
        metrics.recordEndToEnd(Duration.ofMillis(200), AiLatencyMetrics.OUTCOME_ERROR);
        metrics.recordEndToEnd(Duration.ofMillis(300), AiLatencyMetrics.OUTCOME_CANCEL);

        assertEquals(1, registry.find("tweebyte.ai.e2e").tag("outcome", "success").timer().count());
        assertEquals(1, registry.find("tweebyte.ai.e2e").tag("outcome", "error").timer().count());
        assertEquals(1, registry.find("tweebyte.ai.e2e").tag("outcome", "cancel").timer().count());
    }

    @Test
    void recordEndToEndUnknownOutcomeFallsBackToSuccess() {
        // The switch's `default` branch routes any non-error/non-cancel value to the
        // success timer — exercise the default arm explicitly so JaCoCo records it.
        metrics.recordEndToEnd(Duration.ofMillis(50), "weird-outcome");
        assertEquals(1, registry.find("tweebyte.ai.e2e").tag("outcome", "success").timer().count());
    }
}
