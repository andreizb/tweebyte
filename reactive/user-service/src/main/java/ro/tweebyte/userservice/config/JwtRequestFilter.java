package ro.tweebyte.userservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class JwtRequestFilter implements WebFilter {

    private final ReactiveUserDetailsService userDetailsService;
    private final JwtDecoder jwtDecoder;

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/auth/register") ||
                path.startsWith("/auth/login") ||
                path.startsWith("/error") ||
                path.startsWith("users/summary/")) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String jwtToken = authHeader.substring(7);

        return Mono.just(jwtToken)
                .flatMap(token -> {
                    try {
                        return Mono.just(jwtDecoder.decode(token));
                    } catch (JwtException e) {
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return Mono.empty();
                    }
                })
                .flatMap(jwt -> {
                    String userId = jwt.getClaimAsString("user_id");
                    if (userId == null) {
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return Mono.empty();
                    }
                    return userDetailsService.findByUsername(userId);
                })
                .map(userDetails -> new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                ))
                .flatMap(authentication ->
                        chain.filter(exchange)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
                )
                .switchIfEmpty(Mono.defer(() -> {
                    if (!exchange.getResponse().isCommitted()) {
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }
                    return Mono.empty();
                }));
    }
}
