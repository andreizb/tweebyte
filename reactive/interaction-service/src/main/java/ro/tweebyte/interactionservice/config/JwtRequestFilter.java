package ro.tweebyte.interactionservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import ro.tweebyte.interactionservice.model.CustomUserDetails;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class JwtRequestFilter implements WebFilter {

    private final ReactiveUserDetailsService userDetailsService;

    private final JwtDecoder jwtDecoder;

    @Override
    @NonNull
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst("Authorization"))
            .filter(header -> header.startsWith("Bearer "))
            .map(header -> header.substring(7))
            .flatMap(this::authenticate)
            .defaultIfEmpty(new AnonymousAuthenticationToken("key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")))
            .flatMap(authentication -> chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
            );
    }

    private Mono<Authentication> authenticate(String jwtToken) {
        try {
            UUID userId = UUID.fromString(jwtDecoder.decode(jwtToken).getClaimAsString("user_id"));
            String userEmail = jwtDecoder.decode(jwtToken).getClaimAsString("email");
            if (userId != null && userEmail != null) {
                List<GrantedAuthority> dummyAuthorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
                String dummyPassword = "dummyPassword";

                CustomUserDetails userDetails = new CustomUserDetails(userId, userEmail);
                Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, dummyPassword, dummyAuthorities);
                return Mono.just(authentication);
            }
        } catch (Exception ignored) {
        }
        return Mono.empty();
    }

}
