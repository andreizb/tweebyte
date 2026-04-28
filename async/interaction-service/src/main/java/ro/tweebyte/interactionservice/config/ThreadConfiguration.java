package ro.tweebyte.interactionservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

@Configuration
public class ThreadConfiguration {

    @Value("${app.concurrency.interaction.custom-thread-pool}")
    private Boolean enableCustomThreadPool;

    @Bean(name = "executorService")
    public ExecutorService userExecutorService() {
        if (!enableCustomThreadPool) {
            return ForkJoinPool.commonPool();
        }

        int corePoolSize = 600;
        int maxPoolSize = 600;
        int queueCapacity = 15000;

        ThreadFactory tf = r -> {
            Thread t = new Thread(r);
            t.setName("async-io-" + t.getId());
            t.setDaemon(true);
            return t;
        };

        return new ThreadPoolExecutor(
            corePoolSize,
            maxPoolSize,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(queueCapacity),
            tf,
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

}
