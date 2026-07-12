/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.client;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import ro.tweebyte.userservice.exception.AuthenticationException;

/**
 * Thin facade over the Keycloak realm: issues access tokens for login/register via the
 * Resource-Owner-Password-Credentials grant, and provisions new users through the Admin
 * API using the client's service account. The realm-issued access token carries the local
 * profile UUID as the {@code user_id} claim, which the gateway and services key ownership
 * on. Gated by {@code app.keycloak.enabled} so the benchmark overlay (which seeds users via
 * SQL and never logs in) loads neither this bean nor the {@code /auth} surface.
 *
 * <p>
 * Blocking-stack mirror of the reactive {@code KeycloakClient}: the same realm contract over
 * the pooled {@code RestClient}, invoked on the {@code ioExecutor} by AuthenticationService.
 *
 * @author Andrei Zbarcea
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.keycloak", name = "enabled", havingValue = "true")
public class KeycloakClient {

	// A hung Keycloak (DNS stall, dropped SYN, half-open socket) would otherwise block the
	// ioExecutor thread on login/register/provisioning forever. The shared pooled
	// RestClient.Builder carries only a connection-request timeout (HttpClientConfiguration),
	// so this client builds its own request factory with explicit connect + read timeouts —
	// the blocking-stack mirror of the reactive WebClient .timeout(10s).
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

	private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

	// Realm field name reused across the ROPC form, the email update, and the user
	// representation (username = email in this realm).
	private static final String USERNAME = "username";

	@Value("${app.keycloak.base-url}")
	private String baseUrl;

	@Value("${app.keycloak.realm}")
	private String realm;

	@Value("${app.keycloak.client-id}")
	private String clientId;

	@Value("${app.keycloak.client-secret}")
	private String clientSecret;

	private final RestClient.Builder restClientBuilder;

	// The dedicated connect+read-timeout request factory for the realm round-trips. Built
	// once here (rather than reusing the shared pooled factory, which carries only a
	// connection-request timeout) so a hung Keycloak can never block a thread indefinitely.
	// Overridable so the MockRestServiceServer-bound unit test can null it out and keep the
	// mock's request factory on the builder (the test seam mirrors the reactive client's
	// scripted ExchangeFunction).
	private ClientHttpRequestFactory requestFactory = buildRequestFactory();

	private RestClient restClient;

	@PostConstruct
	void init() {
		RestClient.Builder builder = this.restClientBuilder.baseUrl(this.baseUrl);
		if (this.requestFactory != null) {
			builder.requestFactory(this.requestFactory);
		}
		this.restClient = builder.build();
	}

	private static ClientHttpRequestFactory buildRequestFactory() {
		return ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
			.withConnectTimeout(CONNECT_TIMEOUT)
			.withReadTimeout(READ_TIMEOUT));
	}

	// ROPC password grant: a wrong email/password yields a 401 from the token endpoint,
	// mapped to AuthenticationException (→ 401) to match the prior self-signed login shape.
	public String issueToken(String username, String password) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "password");
		form.add("client_id", this.clientId);
		form.add("client_secret", this.clientSecret);
		form.add(USERNAME, username);
		form.add("password", password);
		TokenResponse token = this.restClient.post()
			.uri("/realms/{realm}/protocol/openid-connect/token", this.realm)
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(form)
			.retrieve()
			.onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
				throw new AuthenticationException("Invalid username or password");
			})
			.body(TokenResponse.class);
		if (token == null) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak returned an empty token response");
		}
		return token.accessToken();
	}

	// Create the credential in Keycloak with user_id = the local profile UUID, so the
	// realm-issued token carries the same owner id the gateway and services enforce on.
	public void provisionUser(String email, String rawPassword, String userId) {
		String adminToken = adminToken();
		this.restClient.post()
			.uri("/admin/realms/{realm}/users", this.realm)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
			.contentType(MediaType.APPLICATION_JSON)
			.body(userRepresentation(email, rawPassword, userId))
			.retrieve()
			.onStatus(HttpStatusCode::isError, (request, response) -> {
				log.warn("Keycloak user provisioning failed for {}: status {}", email, response.getStatusCode());
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
						"Identity provider rejected user provisioning");
			})
			.toBodilessEntity();
	}

	// Credential propagation for profile updates: when the local profile's email and/or
	// password changes, mirror it onto the realm credential so the old value stops
	// authenticating. Resolve the realm user by the user_id attribute (the local profile
	// UUID that register() wrote), then PUT the user representation (email) and/or
	// reset-password (new password). Skipped entirely when the bean is absent (benchmark).
	public void updateCredentials(String userId, String newEmail, String newRawPassword) {
		if (newEmail == null && newRawPassword == null) {
			return;
		}
		String adminToken = adminToken();
		String realmId = findRealmUserId(adminToken, userId);
		if (newEmail != null) {
			applyEmail(adminToken, realmId, newEmail);
		}
		if (newRawPassword != null) {
			applyPassword(adminToken, realmId, newRawPassword);
		}
	}

	// GET /admin/realms/{realm}/users?q=user_id:{uuid} — the register() path stored the local
	// profile UUID as the user_id attribute, so this resolves the realm's internal user id.
	private String findRealmUserId(String adminToken, String userId) {
		List<RealmUser> users = this.restClient.get()
			.uri(uriBuilder -> uriBuilder.path("/admin/realms/{realm}/users")
				.queryParam("q", "user_id:" + userId)
				.build(this.realm))
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
			.retrieve()
			.onStatus(HttpStatusCode::isError, (request, response) -> {
				throw badGateway();
			})
			.body(new ParameterizedTypeReference<List<RealmUser>>() {
			});
		if (users == null || users.isEmpty()) {
			throw badGateway();
		}
		return users.get(0).id();
	}

	private void applyEmail(String adminToken, String realmId, String newEmail) {
		// Update both username and email. The realm sets editUsernameAllowed=true, so the
		// registration-time username (the old email) is replaced by the new email; with
		// loginWithEmailAllowed the new email authenticates and the old email no longer does.
		this.restClient.put()
			.uri("/admin/realms/{realm}/users/{id}", this.realm, realmId)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of(USERNAME, newEmail, "email", newEmail))
			.retrieve()
			.onStatus(HttpStatusCode::isError, (request, response) -> {
				throw badGateway();
			})
			.toBodilessEntity();
	}

	private void applyPassword(String adminToken, String realmId, String newRawPassword) {
		this.restClient.put()
			.uri("/admin/realms/{realm}/users/{id}/reset-password", this.realm, realmId)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("type", "password", "value", newRawPassword, "temporary", false))
			.retrieve()
			.onStatus(HttpStatusCode::isError, (request, response) -> {
				throw badGateway();
			})
			.toBodilessEntity();
	}

	private static ResponseStatusException badGateway() {
		return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
				"Identity provider rejected the credential update");
	}

	// client_credentials grant on the service account (realm-management manage-users) backs
	// the Admin-API provisioning call above.
	private String adminToken() {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "client_credentials");
		form.add("client_id", this.clientId);
		form.add("client_secret", this.clientSecret);
		TokenResponse token = this.restClient.post()
			.uri("/realms/{realm}/protocol/openid-connect/token", this.realm)
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(form)
			.retrieve()
			.body(TokenResponse.class);
		if (token == null) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak returned an empty admin token response");
		}
		return token.accessToken();
	}

	private Map<String, Object> userRepresentation(String email, String rawPassword, String userId) {
		return Map.of(USERNAME, email, "email", email, "enabled", true, "emailVerified", true, "attributes",
				Map.of("user_id", List.of(userId)), "credentials",
				List.of(Map.of("type", "password", "value", rawPassword, "temporary", false)));
	}

	private record TokenResponse(@JsonProperty("access_token") String accessToken) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record RealmUser(String id) {
	}

}
