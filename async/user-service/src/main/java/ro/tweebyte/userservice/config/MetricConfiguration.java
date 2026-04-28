package ro.tweebyte.userservice.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class MetricConfiguration {

    @Bean
    public MeterBinder mvcIoExecutorQueueMetrics(
        @Qualifier("ioExecutor") ThreadPoolTaskExecutor ioExecutor) {

        return registry -> {
            ThreadPoolExecutor tpe = ioExecutor.getThreadPoolExecutor();
            BlockingQueue<Runnable> q = tpe.getQueue();

            Gauge.builder("mvc_io_queue_size", q, BlockingQueue::size)
                .description("Tasks waiting in MVC ioExecutor queue")
                .register(registry);

            Gauge.builder("mvc_io_queue_utilization", q, queue -> {
                    int size = queue.size();
                    int cap  = size + queue.remainingCapacity();
                    return cap == 0 ? 0.0 : ((double) size) / cap;
                })
                .description("Queue fill ratio for MVC ioExecutor (0..1)")
                .register(registry);

            Gauge.builder("mvc_io_thread_utilization", tpe,
                    ex -> ex.getMaximumPoolSize() == 0 ? 0.0 :
                        ((double) ex.getActiveCount()) / ex.getMaximumPoolSize())
                .description("Active threads / max threads for MVC ioExecutor")
                .register(registry);
        };
    }
}