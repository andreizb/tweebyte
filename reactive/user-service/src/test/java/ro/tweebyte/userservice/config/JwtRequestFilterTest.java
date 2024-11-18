package ro.tweebyte.userservice.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ro.tweebyte.userservice.controller.UserController;
import ro.tweebyte.userservice.model.CustomUserDetails;
import ro.tweebyte.userservice.model.UserDto;
import ro.tweebyte.userservice.service.UserService;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@WebFluxTest(controllers = UserController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class})
public class JwtRequestFilterTest {

	@Autowired
	private WebTestClient webTestClient;

	@MockBean
	private UserService userService;

	@MockBean
	private ReactiveUserDetailsService userDetailsService;

	@MockBean
	private NimbusJwtDecoder jwtDecoder;

	private UUID userId;
	private String userEmail;
	private String jwtToken;

	@BeforeEach
	public void setUp() {
		userId = UUID.randomUUID();
		userEmail = "test@example.com";
		jwtToken = "dummyJwtToken";
	}

	@Test
	public void testValidJwtToken() {
		Jwt jwt = mock(Jwt.class);
		when(jwt.getClaimAsString("user_id")).thenReturn(userId.toString());
		when(jwt.getClaimAsString("email")).thenReturn(userEmail);
		when(jwt.getSubject()).thenReturn(userEmail);
		when(jwt.getClaims()).thenReturn(Map.of(
				"user_id", userId.toString(),
				"email", userEmail
		));

		doReturn(jwt)
			.when(jwtDecoder)
			.decode(anyString());

		CustomUserDetails userDetails = new CustomUserDetails(
				userId,
				userEmail,
				"dummyPassword",
				Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
		);
		when(userDetailsService.findByUsername(anyString())).thenReturn(Mono.just(userDetails));

		when(userService.getUserProfile(any(), any())).thenReturn(Mono.just(UserDto.builder()
			.id(userId)
			.email(userEmail)
			.build()));

		webTestClient
			.get()
			.uri("/users/" + userId)
			.header("Authorization", "Bearer " + jwtToken)
			.exchange()
			.expectStatus().isOk()
			.expectBody(UserDto.class)
			.value(response -> {
				assertNotNull(response);
				assertEquals(userId, response.getId());
				assertEquals(userEmail, response.getEmail());
			});
	}

	@Test
	public void testInvalidJwtToken() {
		when(jwtDecoder.decode(anyString())).thenThrow(new JwtException("Invalid token"));

		webTestClient.get()
				.uri("/users/" + userId)
				.header("Authorization", "Bearer invalidJwtToken")
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	public void testNoJwtToken() {
		webTestClient.get()
				.uri("/users/" + userId)
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	public void testEmptyAuthorizationHeader() {
		webTestClient.get()
				.uri("/users/" + userId)
				.header("Authorization", "")
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	public void testNonBearerAuthorizationHeader() {
		webTestClient.get()
				.uri("/users/" + userId)
				.header("Authorization", "Basic someToken")
				.exchange()
				.expectStatus().isUnauthorized();
	}
}