package ro.tweebyte.tweetservice.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Reactive twin of the async latency metrics — kept in shape-parity so the
 * paper can scrape `tweebyte_ai_*_seconds{outcome="..."}` symmetrically across
 * stacks. See the async stack's `AiLatencyMetrics` for outcome semantics.
 */
@Component
public class AiLatencyMetrics {

    public static final String OUTCOME_SUCCESS = "success";
    public static final String OUTCOME_ERROR = "error";
    public static final String OUTCOME_CANCEL = "cancel";

    private final MeterRegistry registry;
    private final Timer ttftTimer;
    private final Timer itlTimer;
    private final Timer toolCallTimer;
    private final Timer serializeTimer;
    private final Timer endToEndSuccess;
    private final Timer endToEndError;
    private final Timer endToEndCancel;

    public AiLatencyMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.ttftTimer = Timer.builder("tweebyte.ai.ttft")
                .description("Time to first token on AI streaming responses")
                .publishPercentiles(0.5, 0.95, 0.99, 0.999)
                .publishPercentileHistogram()
                .register(registry);
        this.itlTimer = Timer.builder("tweebyte.ai.itl")
                .description("Inter-token latency on AI streaming responses")
                .publishPercentiles(0.5, 0.95, 0.99, 0.999)
                .publishPercentileHistogram()
                .register(registry);
        this.toolCallTimer = Timer.builder("tweebyte.ai.tool")
                .description("Tool sub-call duration during AI generation")
                .publishPercentiles(0.5, 0.95, 0.99, 0.999)
                .publishPercentileHistogram()
                .register(registry);
        this.serializeTimer = Timer.builder("tweebyte.ai.serialize")
                .description("Per-event SSE serialization + flush duration")
                .publishPercentiles(0.5, 0.95, 0.99, 0.999)
                .publishPercentileHistogram()
                .register(registry);
        this.endToEndSuccess = e2eTimer(OUTCOME_SUCCESS);
        this.endToEndError = e2eTimer(OUTCOME_ERROR);
        this.endToEndCancel = e2eTimer(OUTCOME_CANCEL);
    }

    private Timer e2eTimer(String outcome) {
        return Timer.builder("tweebyte.ai.e2e")
                .description("End-to-end AI streaming request duration, tagged by outcome")
                .tag("outcome", outcome)
                .publishPercentiles(0.5, 0.95, 0.99, 0.999)
                .publishPercentileHistogram()
                .register(registry);
    }

    public void recordTtft(Duration d) {
        ttftTimer.record(d);
    }

    public void recordItl(Duration d) {
        itlTimer.record(d);
    }

    public void recordToolCall(Duration d) {
        toolCallTimer.record(d);
    }

    public void recordSerialize(Duration d) {
        serializeTimer.record(d);
    }

    public void recordEndToEnd(Duration d, String outcome) {
        Timer t = switch (outcome) {
            case OUTCOME_ERROR -> endToEndError;
            case OUTCOME_CANCEL -> endToEndCancel;
            default -> endToEndSuccess;
        };
        t.record(d);
    }
}
