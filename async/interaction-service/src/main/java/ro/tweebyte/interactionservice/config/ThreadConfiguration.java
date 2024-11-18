package ro.tweebyte.interactionservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

@Configuration
public class ThreadConfiguration {

    @Value("${app.concurrency.interaction.custom-thread-pool}")
    private Boolean enableCustomThreadPool;

    @Bean(name = "executorService")
    public ExecutorService userExecutorService() {
        return enableCustomThreadPool ? Executors.newCachedThreadPool() : ForkJoinPool.commonPool();
    }

}
