package ro.tweebyte.tweetservice.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PoolOccupancyMetricsTest {

    private ExecutorService executor;

    @AfterEach
    void tearDown() {
        if (executor != null) executor.shutdownNow();
    }

    @Test
    void registersAllGaugesWhenExecutorIsThreadPoolExecutor() {
        executor = Executors.newFixedThreadPool(2);
        MeterRegistry registry = new SimpleMeterRegistry();
        PoolOccupancyMetrics metrics = new PoolOccupancyMetrics(executor, registry);

        metrics.register();

        assertNotNull(registry.find("tweebyte.pool.queue.depth").gauge());
        assertNotNull(registry.find("tweebyte.pool.active").gauge());
        assertNotNull(registry.find("tweebyte.pool.size").gauge());
        assertNotNull(registry.find("tweebyte.pool.tasks.completed").gauge());

        // Active threads should be 0 right after construction (no submitted tasks).
        assertEquals(0, registry.find("tweebyte.pool.active").gauge().value(), 0.0);
    }

    @Test
    void registersNothingWhenExecutorIsNotThreadPoolExecutor() {
        // The instanceof guard means a non-ThreadPoolExecutor (e.g. a forwarding
        // wrapper or work-stealing pool) leaves the registry empty for these gauges.
        ExecutorService notTpe = new ExecutorService() {
            @Override public void shutdown() {}
            @Override public java.util.List<Runnable> shutdownNow() { return java.util.List.of(); }
            @Override public boolean isShutdown() { return true; }
            @Override public boolean isTerminated() { return true; }
            @Override public boolean awaitTermination(long t, java.util.concurrent.TimeUnit u) { return true; }
            @Override public <T> java.util.concurrent.Future<T> submit(java.util.concurrent.Callable<T> task) { return null; }
            @Override public <T> java.util.concurrent.Future<T> submit(Runnable task, T result) { return null; }
            @Override public java.util.concurrent.Future<?> submit(Runnable task) { return null; }
            @Override public <T> java.util.List<java.util.concurrent.Future<T>> invokeAll(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks) { return java.util.List.of(); }
            @Override public <T> java.util.List<java.util.concurrent.Future<T>> invokeAll(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks, long t, java.util.concurrent.TimeUnit u) { return java.util.List.of(); }
            @Override public <T> T invokeAny(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks) { return null; }
            @Override public <T> T invokeAny(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks, long t, java.util.concurrent.TimeUnit u) { return null; }
            @Override public void execute(Runnable command) {}
        };

        MeterRegistry registry = new SimpleMeterRegistry();
        new PoolOccupancyMetrics(notTpe, registry).register();

        // No pool.* gauges should be registered for the non-TPE branch.
        assertEquals(null, registry.find("tweebyte.pool.queue.depth").gauge());
    }

    @Test
    void queueDepthGaugeReflectsThreadPoolExecutorState() {
        ThreadPoolExecutor tpe = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        executor = tpe;
        MeterRegistry registry = new SimpleMeterRegistry();
        new PoolOccupancyMetrics(tpe, registry).register();
        // No tasks queued initially.
        assertEquals(0.0, registry.find("tweebyte.pool.queue.depth").gauge().value(), 0.0);
        // PoolSize gauge present even before tasks run.
        assertNotNull(registry.find("tweebyte.pool.size").gauge());
    }
}
