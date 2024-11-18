package ro.tweebyte.userservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

@Configuration
public class ThreadConfiguration {

    @Value("${app.concurrency.auth.custom-thread-pool}")
    private Boolean authenticationEnableCustomThreadPool;

    @Value("${app.concurrency.user.custom-thread-pool}")
    private Boolean userEnableCustomThreadPool;

    @Bean(name = "authenticationExecutorService")
    public ExecutorService authenticationExecutorService() {
        return authenticationEnableCustomThreadPool ? Executors.newCachedThreadPool() : ForkJoinPool.commonPool();
    }

    @Bean(name = "userExecutorService")
    public ExecutorService userExecutorService() {
        return new DelegatingSecurityContextExecutorService(
            userEnableCustomThreadPool ? Executors.newCachedThreadPool() : ForkJoinPool.commonPool()
        );
    }

}
