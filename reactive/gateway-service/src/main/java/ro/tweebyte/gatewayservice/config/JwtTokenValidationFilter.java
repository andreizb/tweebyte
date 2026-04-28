package ro.tweebyte.gatewayservice.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.interfaces.RSAPublicKey;
import java.util.List;

@Component
public class JwtTokenValidationFilter implements GlobalFilter, Ordered {

    @Autowired
    private RSAPublicKey publicKey;

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestPath = exchange.getRequest().getURI().getPath();

        if (requestPath.endsWith("/login") || requestPath.endsWith("/register")) {
            return chain.filter(exchange);
        }

        String jwtToken = extractJwtFromRequest(exchange.getRequest());

        return validateToken(jwtToken)
            .flatMap(valid -> {
                if (valid) {
                    return chain.filter(exchange);
                }

                return onError(exchange, "Invalid JWT Token");
            });
    }

    private Mono<Boolean> validateToken(String token) {
        return Mono.fromCallable(() -> {
            try {
                Algorithm algorithm = Algorithm.RSA256(publicKey, null);
                JWTVerifier verifier = JWT.require(algorithm).build();
                verifier.verify(token);
                return true; // Token is valid
            } catch (Exception e) {
                return false; // Token is invalid
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String extractJwtFromRequest(ServerHttpRequest request) {
        List<String> headers = request.getHeaders().getOrEmpty("Authorization");
        for (String header : headers) {
            if (header.startsWith("Bearer ")) {
                return header.substring(7);
            }
        }
        return null;
    }

    private Mono<Void> onError(ServerWebExchange exchange, String error) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        // Set headers, body, etc. as required
        return response.setComplete();
    }

}
