package ro.tweebyte.gatewayservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

class GatewayServiceApplicationMainTest {

    /**
     * Mirrors the async-side test that simply invokes {@code main(...)} for coverage. Spring Cloud
     * Gateway insists on a non-servlet (reactive) web environment and binds a real port by default,
     * which is undesirable for unit tests. Override the type to NONE and avoid port binding.
     */
    @Test
    void main() {
        SpringApplication app = new SpringApplication(GatewayServiceApplication.class);
        app.setAdditionalProfiles();
        try (ConfigurableApplicationContext ctx = app.run(
                "--spring.main.web-application-type=none",
                "--spring.cloud.gateway.enabled=false")) {
            // context started; close via try-with-resources
        }
    }

}
