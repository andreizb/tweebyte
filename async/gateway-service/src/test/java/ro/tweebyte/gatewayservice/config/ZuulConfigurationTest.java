package ro.tweebyte.gatewayservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.netflix.zuul.filters.RouteLocator;
import org.springframework.cloud.netflix.zuul.web.ZuulHandlerMapping;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@AutoConfigureMockMvc
public class ZuulConfigurationTest {

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private ZuulHandlerMapping zuulHandlerMapping;

    @Test
    public void testZuulHandlerMappingBeanIsEnhanced() {
        assertNotNull(zuulHandlerMapping);
    }

    @Test
    public void testRouteLocatorBeanIsPresent() {
        assertNotNull(routeLocator);
    }
}