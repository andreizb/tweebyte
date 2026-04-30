package ro.tweebyte.gatewayservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts the reactive Spring Cloud Gateway exposes a {@code RouteLocator} bean.
 * Goes a little further by asserting all three downstream routes (user, tweet, interaction)
 * are wired, which on Zuul is implicit in the property loader.
 */
@SpringBootTest
class RouteLocatorTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void routeLocatorBeanIsPresent() {
        assertNotNull(routeLocator, "RouteLocator bean should be wired");
    }

    @Test
    void allDownstreamRoutesAreReachable() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        assertNotNull(routes);
        List<String> ids = routes.stream().map(Route::getId).collect(Collectors.toList());
        assertTrue(ids.contains("user-service"), "user-service route missing: " + ids);
        assertTrue(ids.contains("tweet-service"), "tweet-service route missing: " + ids);
        assertTrue(ids.contains("interaction-service"), "interaction-service route missing: " + ids);

        StepVerifier.create(routeLocator.getRoutes().count())
                .assertNext(count -> assertTrue(count >= 3, "expected >=3 routes, got " + count))
                .verifyComplete();
    }

}
