package ro.tweebyte.gatewayservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.NettyRoutingFilter;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit assertion that the JWT filter's order beats Spring Cloud Gateway's routing
 * filters, mirroring the async-side {@code filterOrder()} test (which asserted Zuul order=1).
 * On reactive, "lower number runs first"; the routing filter's order is exposed by the
 * {@link NettyRoutingFilter#getOrder()} contract (Integer.MAX_VALUE in SCG 4.x).
 */
class JwtTokenValidationFilterUnitTest {

    @Test
    void getOrderRunsBeforeRoutingFilter() {
        JwtTokenValidationFilter filter = new JwtTokenValidationFilter();
        int routingOrder = Integer.MAX_VALUE; // NettyRoutingFilter terminal order on SCG 4.x.
        assertTrue(filter.getOrder() < routingOrder,
                "JWT filter must execute before NettyRoutingFilter");
        assertTrue(filter.getOrder() < 0,
                "Filter order should be negative so it precedes route-mapping filters");
    }

}
