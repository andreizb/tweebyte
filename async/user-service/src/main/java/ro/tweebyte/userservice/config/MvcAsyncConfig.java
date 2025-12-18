package ro.tweebyte.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcAsyncConfig implements WebMvcConfigurer {

    @Bean(name = "ioExecutor")
    public ThreadPoolTaskExecutor ioExecutor() {
        int threads = 1000;
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(threads);
        ex.setMaxPoolSize(threads);
        ex.setQueueCapacity(100_000);
        ex.setThreadNamePrefix("mvc-io-");
        ex.setAllowCoreThreadTimeOut(true);
        ex.setKeepAliveSeconds(60);
        ex.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        ex.initialize();
        return ex;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer c) {
        c.setTaskExecutor(ioExecutor());
        c.setDefaultTimeout(0);
    }

}
