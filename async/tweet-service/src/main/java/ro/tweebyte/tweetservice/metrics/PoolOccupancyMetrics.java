package ro.tweebyte.tweetservice.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Micrometer gauges for the custom executor. Rejection counter lives in
 * {@link ro.tweebyte.tweetservice.config.ThreadConfiguration.CountingRejectionHandler}
 * so it actually fires when the stock reject policy runs.
 */
@Component
@RequiredArgsConstructor
public class PoolOccupancyMetrics {

    @Qualifier(value = "executorService")
    private final ExecutorService executorService;

    private final MeterRegistry registry;

    @PostConstruct
    public void register() {
        if (executorService instanceof ThreadPoolExecutor pool) {
            Gauge.builder("tweebyte.pool.queue.depth", pool, p -> p.getQueue().size())
                    .description("Current depth of the custom executor task queue")
                    .register(registry);
            Gauge.builder("tweebyte.pool.active", pool, ThreadPoolExecutor::getActiveCount)
                    .description("Active thread count of the custom executor")
                    .register(registry);
            Gauge.builder("tweebyte.pool.size", pool, p -> p.getPoolSize())
                    .description("Current pool size of the custom executor")
                    .register(registry);
            Gauge.builder("tweebyte.pool.tasks.completed", pool, ThreadPoolExecutor::getCompletedTaskCount)
                    .description("Completed task count of the custom executor")
                    .register(registry);
        }
    }
}
