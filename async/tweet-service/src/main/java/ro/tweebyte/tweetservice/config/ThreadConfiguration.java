package ro.tweebyte.tweetservice.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class ThreadConfiguration {

    @Value("${app.concurrency.tweet.custom-thread-pool}")
    private Boolean enableCustomThreadPool;

    @Value("${app.concurrency.tweet.pool-size:200}")
    private int poolSize;

    @Value("${app.concurrency.tweet.queue-capacity:0}")
    private int queueCapacity;

    @Value("${app.concurrency.tweet.reject-policy:caller-runs}")
    private String rejectPolicy;

    @Bean(name = "executorService")
    public ExecutorService userExecutorService(MeterRegistry registry) {
        if (!enableCustomThreadPool) {
            return ForkJoinPool.commonPool();
        }
        int effectiveQueue = queueCapacity > 0 ? queueCapacity : poolSize * 10;
        RejectedExecutionHandler baseHandler = switch (rejectPolicy) {
            case "abort" -> new ThreadPoolExecutor.AbortPolicy();
            case "discard" -> new ThreadPoolExecutor.DiscardPolicy();
            case "discard-oldest" -> new ThreadPoolExecutor.DiscardOldestPolicy();
            default -> new ThreadPoolExecutor.CallerRunsPolicy();
        };
        // SSE compatibility caveat: discard / discard-oldest silently drop the
        // submitted Runnable, but AiController has already returned an open
        // SseEmitter to the client. A dropped task means the emitter is never
        // completed and the client hangs until its read timeout — which makes
        // these policies useless for AI-endpoint admission control. The H1
        // benchmark profile defaults to `abort` (clean 5xx); `caller-runs` is
        // safe for dev because the request thread executes the task. Keep the
        // option exposed for non-AI sweeps but warn loudly so a misconfigured
        // AI run is visible in the boot log.
        if ("discard".equals(rejectPolicy) || "discard-oldest".equals(rejectPolicy)) {
            log.warn("WARNING: APP_CONCURRENCY_TWEET_REJECT_POLICY={} silently drops rejected tasks. " +
                    "AI streaming endpoints (/tweets/ai/*) return an open SseEmitter BEFORE the worker " +
                    "starts, so a dropped task leaves the SSE response open until the client times out " +
                    "— not a clean rejection signal. Use 'abort' (default) or 'caller-runs' for AI sweeps.",
                    rejectPolicy);
        }
        Counter rejectionCounter = Counter.builder("tweebyte.pool.rejections")
                .description("Count of tasks rejected by the custom executor (delegates to the configured reject policy)")
                .register(registry);
        RejectedExecutionHandler countingHandler = new CountingRejectionHandler(baseHandler, rejectionCounter);
        return new ThreadPoolExecutor(
                poolSize, poolSize,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(effectiveQueue),
                countingHandler);
    }

    /**
     * Delegating {@link RejectedExecutionHandler} that increments a Micrometer counter
     * before forwarding to the configured base policy. Without this wrapper the stock
     * policies (Abort/Discard/DiscardOldest/CallerRuns) never touch our metric, and
     * the primary "queue overflowed" observable stays stuck at zero during sweeps.
     */
    static final class CountingRejectionHandler implements RejectedExecutionHandler {
        private final RejectedExecutionHandler delegate;
        private final Counter counter;

        CountingRejectionHandler(RejectedExecutionHandler delegate, Counter counter) {
            this.delegate = delegate;
            this.counter = counter;
        }

        @Override
        public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
            counter.increment();
            delegate.rejectedExecution(runnable, executor);
        }
    }

}
