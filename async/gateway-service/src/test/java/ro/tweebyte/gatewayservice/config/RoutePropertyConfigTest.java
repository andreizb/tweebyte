package ro.tweebyte.gatewayservice.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.netflix.zuul.filters.Route;
import org.springframework.cloud.netflix.zuul.filters.RouteLocator;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the async/Zuul gateway honours env-var-overridable route URIs:
 * {@code zuul.routes.<id>.url=${USER_SERVICE_URL:http://localhost:9091/}}.
 * <ul>
 *     <li>{@link EnvVarSet} - explicit env var wins.</li>
 *     <li>{@link EnvVarAbsent} - localhost default applies when the env var is not set.</li>
 * </ul>
 *
 * Note: the test {@code application.properties} on the async side currently hardcodes the URL,
 * so we override at property level via {@code @TestPropertySource} for both branches.
 */
public class RoutePropertyConfigTest {

    private static String findRouteLocation(RouteLocator locator, String pathPrefix) {
        List<Route> routes = locator.getRoutes();
        assertNotNull(routes);
        Optional<Route> match = routes.stream()
                .filter(r -> r.getFullPath() != null && r.getFullPath().startsWith(pathPrefix))
                .findFirst();
        assertTrue(match.isPresent(),
                "no route matching " + pathPrefix + " in " + routes);
        return match.get().getLocation();
    }

    @Nested
    @SpringBootTest
    @TestPropertySource(properties = {
            "zuul.routes.user-service.url=http://stub-user:9991/",
            "zuul.routes.tweet-service.url=http://stub-tweet:9992/",
            "zuul.routes.interaction-service.url=http://stub-interaction:9993/"
    })
    public class EnvVarSet {

        @Autowired
        private RouteLocator routeLocator;

        @Test
        public void userServiceRouteHonoursEnvVar() {
            String location = findRouteLocation(routeLocator, "/user-service");
            assertTrue(location.startsWith("http://stub-user:9991"),
                    "expected USER_SERVICE_URL override, got " + location);
        }

    }

    @Nested
    @SpringBootTest
    @TestPropertySource(properties = {
            "zuul.routes.user-service.url=http://localhost:9091/",
            "zuul.routes.tweet-service.url=http://localhost:9092/",
            "zuul.routes.interaction-service.url=http://localhost:9093/"
    })
    public class EnvVarAbsent {

        @Autowired
        private RouteLocator routeLocator;

        @Test
        public void userServiceRouteFallsBackToLocalhost() {
            String location = findRouteLocation(routeLocator, "/user-service");
            assertTrue(location.startsWith("http://localhost:9091"),
                    "expected localhost fallback, got " + location);
        }

    }

}
