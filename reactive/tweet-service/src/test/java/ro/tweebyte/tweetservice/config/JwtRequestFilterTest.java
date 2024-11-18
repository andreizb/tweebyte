package ro.tweebyte.tweetservice.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ro.tweebyte.tweetservice.model.CustomUserDetails;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtRequestFilterTest {

	@InjectMocks
	private JwtRequestFilter filter;

	@Mock
	private JwtDecoder jwtDecoder;

	@Mock
	private ServerWebExchange exchange;

	@Mock
	private WebFilterChain chain;

	private HttpHeaders headers;

	@BeforeEach
	void setup() {
		headers = new HttpHeaders();
		when(exchange.getRequest()).thenReturn(mock(ServerHttpRequest.class, invocation -> {
			if (invocation.getMethod().getName().equals("getHeaders")) {
				return headers;
			}
			return null;
		}));
	}

	@Test
	void filter_ShouldAuthenticateValidJwt() {
		String token = "validToken";
		headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);

		Jwt jwt = mock(Jwt.class);
		when(jwt.getClaimAsString("user_id")).thenReturn(UUID.randomUUID().toString());
		when(jwt.getClaimAsString("email")).thenReturn("test@example.com");
		when(jwtDecoder.decode(token)).thenReturn(jwt);
		when(chain.filter(exchange)).thenReturn(Mono.empty());

		StepVerifier.create(filter.filter(exchange, chain))
			.verifyComplete();

		verify(jwtDecoder, times(2)).decode(token);
		verify(chain).filter(exchange);
	}

	@Test
	void filter_ShouldSetAnonymousAuthentication_WhenNoToken() {
		when(chain.filter(exchange)).thenReturn(Mono.empty());

		StepVerifier.create(filter.filter(exchange, chain))
			.verifyComplete();

		verify(chain).filter(exchange);
	}

	@Test
	void filter_ShouldHandleInvalidJwt() {
		String token = "invalidToken";
		headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);

		when(jwtDecoder.decode(token)).thenThrow(new RuntimeException("Invalid token"));
		when(chain.filter(exchange)).thenReturn(Mono.empty());

		StepVerifier.create(filter.filter(exchange, chain))
			.verifyComplete();

		verify(jwtDecoder).decode(token);
		verify(chain).filter(exchange);
	}
}
