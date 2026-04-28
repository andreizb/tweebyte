package ro.tweebyte.interactionservice.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BenchmarkProfilePropertiesTest {

    @Test
    void benchmarkProfileDisablesInteractionServiceBreakerAndCleanup() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("application-benchmark.properties")) {
            assertNotNull(inputStream);

            Properties properties = new Properties();
            properties.load(inputStream);

            assertEquals("false", properties.getProperty("app.resilience.followers-count-breaker.enabled"));
            assertEquals("false", properties.getProperty("app.cleanup.enabled"));
        }
    }
}
