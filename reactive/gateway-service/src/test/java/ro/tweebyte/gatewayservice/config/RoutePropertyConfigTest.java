package ro.tweebyte.gatewayservice.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the reactive Spring Cloud Gateway honours env-var-overridable route URIs:
 * {@code ${USER_SERVICE_URL:http://localhost:9091/}}.
 * <ul>
 *     <li>{@link EnvVarSet} — when the env var is provided, the route URI honours it.</li>
 *     <li>{@link EnvVarAbsent} — when the env var is not provided, the localhost default applies.</li>
 * </ul>
 */
class RoutePropertyConfigTest {

    private static String findRouteUri(RouteLocator locator, String id) {
        List<Route> routes = locator.getRoutes().collectList().block();
        assertTrue(routes != null && !routes.isEmpty(), "no routes loaded");
        Optional<Route> route = routes.stream().filter(r -> id.equals(r.getId())).findFirst();
        assertTrue(route.isPresent(), id + " route not found");
        return route.get().getUri().toString();
    }

    @Nested
    @SpringBootTest
    @TestPropertySource(properties = {
            "USER_SERVICE_URL=http://stub-user:9991/",
            "TWEET_SERVICE_URL=http://stub-tweet:9992/",
            "INTERACTION_SERVICE_URL=http://stub-interaction:9993/"
    })
    class EnvVarSet {

        @Autowired
        private RouteLocator routeLocator;

        @Test
        void userServiceRouteHonoursEnvVar() {
            String uri = findRouteUri(routeLocator, "user-service");
            // SCG normalises trailing slash off URIs.
            assertTrue(uri.startsWith("http://stub-user:9991"),
                    "expected USER_SERVICE_URL override, got " + uri);
        }

    }

    @Nested
    @SpringBootTest
    class EnvVarAbsent {

        @Autowired
        private RouteLocator routeLocator;

        @Test
        void userServiceRouteFallsBackToLocalhost() {
            String uri = findRouteUri(routeLocator, "user-service");
            assertTrue(uri.startsWith("http://localhost:9091"),
                    "expected localhost fallback, got " + uri);
            assertEquals("user-service",
                    routeLocator.getRoutes()
                            .filter(r -> "user-service".equals(r.getId()))
                            .blockFirst()
                            .getId());
        }

    }

}
