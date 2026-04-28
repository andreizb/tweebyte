package ro.tweebyte.tweetservice.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BenchmarkProfilePropertiesTest {

    @Test
    void benchmarkProfileDisablesTweetServiceBreaker() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("application-benchmark.properties")) {
            assertNotNull(inputStream);

            Properties properties = new Properties();
            properties.load(inputStream);

            assertEquals("false", properties.getProperty("app.resilience.followed-ids-breaker.enabled"));
        }
    }
}
