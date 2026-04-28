package ro.tweebyte.userservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "app.resilience")
public class UserServiceResilienceProperties {

    private RegisterRateLimit registerRateLimit = new RegisterRateLimit();

    private UpdateRetry updateRetry = new UpdateRetry();

    @Data
    public static class RegisterRateLimit {
        private boolean enabled = true;
        private int limitForPeriod = 10;
        private Duration limitRefreshPeriod = Duration.ofSeconds(1);
        private Duration timeoutDuration = Duration.ofMillis(500);
    }

    @Data
    public static class UpdateRetry {
        private boolean enabled = true;
        private int maxRetries = 3;
        private Duration backoff = Duration.ofSeconds(1);
    }
}
