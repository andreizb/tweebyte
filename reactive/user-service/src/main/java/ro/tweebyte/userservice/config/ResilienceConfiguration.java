package ro.tweebyte.userservice.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ro.tweebyte.userservice.util.TransientFailureClassifier;

@Configuration
public class ResilienceConfiguration {

    @Bean
    public RateLimiter registerRateLimiter(UserServiceResilienceProperties properties) {
        UserServiceResilienceProperties.RegisterRateLimit config = properties.getRegisterRateLimit();
        return RateLimiter.of(
            "userServiceRegisterRateLimiter",
            RateLimiterConfig.custom()
                .limitForPeriod(config.getLimitForPeriod())
                .limitRefreshPeriod(config.getLimitRefreshPeriod())
                .timeoutDuration(config.getTimeoutDuration())
                .build()
        );
    }

    @Bean
    public Retry updateRetry(UserServiceResilienceProperties properties) {
        UserServiceResilienceProperties.UpdateRetry config = properties.getUpdateRetry();
        return Retry.of(
            "userServiceUpdateRetry",
            RetryConfig.custom()
                .maxAttempts(config.getMaxRetries() + 1)
                .waitDuration(config.getBackoff())
                .retryOnException(TransientFailureClassifier::isRetryable)
                .build()
        );
    }
}
