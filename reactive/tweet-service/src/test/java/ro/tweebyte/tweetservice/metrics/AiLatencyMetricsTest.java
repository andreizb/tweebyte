package ro.tweebyte.tweetservice.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiLatencyMetricsTest {

    private MeterRegistry registry;
    private AiLatencyMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AiLatencyMetrics(registry);
    }

    private long count(String name, String... tags) {
        Timer t = registry.find(name).tags(tags).timer();
        return t == null ? 0 : t.count();
    }

    @Test
    void recordTtftIncrementsTtftTimer() {
        metrics.recordTtft(Duration.ofMillis(10));
        metrics.recordTtft(Duration.ofMillis(20));
        assertEquals(2, count("tweebyte.ai.ttft"));
    }

    @Test
    void recordItlIncrementsItlTimer() {
        metrics.recordItl(Duration.ofMillis(5));
        assertEquals(1, count("tweebyte.ai.itl"));
    }

    @Test
    void recordToolCallIncrementsToolTimer() {
        metrics.recordToolCall(Duration.ofMillis(50));
        assertEquals(1, count("tweebyte.ai.tool"));
    }

    @Test
    void recordSerializeIncrementsSerializeTimer() {
        metrics.recordSerialize(Duration.ofNanos(123));
        assertEquals(1, count("tweebyte.ai.serialize"));
    }

    @Test
    void recordEndToEndSuccessRoutesToSuccessTimer() {
        metrics.recordEndToEnd(Duration.ofMillis(100), AiLatencyMetrics.OUTCOME_SUCCESS);
        assertEquals(1, count("tweebyte.ai.e2e", "outcome", AiLatencyMetrics.OUTCOME_SUCCESS));
        assertEquals(0, count("tweebyte.ai.e2e", "outcome", AiLatencyMetrics.OUTCOME_ERROR));
        assertEquals(0, count("tweebyte.ai.e2e", "outcome", AiLatencyMetrics.OUTCOME_CANCEL));
    }

    @Test
    void recordEndToEndErrorRoutesToErrorTimer() {
        metrics.recordEndToEnd(Duration.ofMillis(100), AiLatencyMetrics.OUTCOME_ERROR);
        assertEquals(1, count("tweebyte.ai.e2e", "outcome", AiLatencyMetrics.OUTCOME_ERROR));
        assertEquals(0, count("tweebyte.ai.e2e", "outcome", AiLatencyMetrics.OUTCOME_SUCCESS));
    }

    @Test
    void recordEndToEndCancelRoutesToCancelTimer() {
        metrics.recordEndToEnd(Duration.ofMillis(100), AiLatencyMetrics.OUTCOME_CANCEL);
        assertEquals(1, count("tweebyte.ai.e2e", "outcome", AiLatencyMetrics.OUTCOME_CANCEL));
        assertEquals(0, count("tweebyte.ai.e2e", "outcome", AiLatencyMetrics.OUTCOME_SUCCESS));
    }

    @Test
    void recordEndToEndUnknownOutcomeRoutesToSuccessDefault() {
        metrics.recordEndToEnd(Duration.ofMillis(50), "weird-outcome");
        assertEquals(1, count("tweebyte.ai.e2e", "outcome", AiLatencyMetrics.OUTCOME_SUCCESS));
    }
}
