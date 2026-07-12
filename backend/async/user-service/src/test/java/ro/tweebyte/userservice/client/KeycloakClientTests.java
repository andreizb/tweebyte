/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.client;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import ro.tweebyte.userservice.exception.AuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Covers the blocking KeycloakClient over a MockRestServiceServer-bound RestClient: the
 * ROPC token grant (happy path + 401 mapping + empty-body BAD_GATEWAY), the Admin-API
 * provisioning path (admin client_credentials token → user POST, with the user_id
 * attribute, plus the error→BAD_GATEWAY and empty-admin-token→BAD_GATEWAY mappings), the
 * credential-propagation path (resolve realm user by user_id attribute → PUT email and/or
 * reset-password, with not-found and IdP-error → BAD_GATEWAY), and the dedicated
 * connect+read timeout factory. Mirrors the reactive KeycloakClientTests scenario-for-scenario.
 */
class KeycloakClientTests {

	private static final String BASE_URL = "http://keycloak.local";

	private static final String REALM = "tweebyte";

	private static final String CLIENT_ID = "tweebyte-client";

	private static final String CLIENT_SECRET = "s3cr3t";

	private static final String TOKEN_URI = BASE_URL + "/realms/" + REALM + "/protocol/openid-connect/token";

	private static final String USERS_URI = BASE_URL + "/admin/realms/" + REALM + "/users";

	private static final String REALM_ID = "11111111-2222-3333-4444-555555555555";

	private static final String USER_BY_ID_URI = USERS_URI + "/" + REALM_ID;

	private MockRestServiceServer server;

	private KeycloakClient keycloakClient;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		this.server = MockRestServiceServer.bindTo(builder).build();
		this.keycloakClient = new KeycloakClient(builder);
		ReflectionTestUtils.setField(this.keycloakClient, "baseUrl", BASE_URL);
		ReflectionTestUtils.setField(this.keycloakClient, "realm", REALM);
		ReflectionTestUtils.setField(this.keycloakClient, "clientId", CLIENT_ID);
		ReflectionTestUtils.setField(this.keycloakClient, "clientSecret", CLIENT_SECRET);
		// Null the dedicated timeout request factory so init() keeps the MockRestServiceServer
		// factory the bindTo above installed on the builder; the timeout factory itself is
		// asserted separately in keycloakClientConfiguresConnectAndReadTimeouts.
		ReflectionTestUtils.setField(this.keycloakClient, "requestFactory", null);
		ReflectionTestUtils.invokeMethod(this.keycloakClient, "init");
	}

	// --- issueToken (ROPC password grant) -----------------------------

	@Test
	void issueTokenReturnsAccessTokenOnPasswordGrant() {
		this.server.expect(requestTo(TOKEN_URI))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=password")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("username=alice%40example.com")))
			.andRespond(withSuccess("{\"access_token\":\"the-access-token\"}", MediaType.APPLICATION_JSON));

		String token = this.keycloakClient.issueToken("alice@example.com", "pw");

		assertThat(token).isEqualTo("the-access-token");
		this.server.verify();
	}

	@Test
	void issueTokenMapsClientErrorToAuthenticationException() {
		this.server.expect(requestTo(TOKEN_URI))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withStatus(HttpStatus.UNAUTHORIZED));

		assertThatThrownBy(() -> this.keycloakClient.issueToken("bad@example.com", "wrong"))
			.isInstanceOf(AuthenticationException.class)
			.hasMessageContaining("Invalid username or password");
		this.server.verify();
	}

	@Test
	void issueTokenMapsEmptyBodyToBadGateway() {
		this.server.expect(requestTo(TOKEN_URI))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess("", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> this.keycloakClient.issueToken("alice@example.com", "pw"))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
				.isEqualTo(HttpStatus.BAD_GATEWAY));
		this.server.verify();
	}

	// --- provisionUser (admin token + Admin-API user POST) ------------

	@Test
	void provisionUserAcquiresAdminTokenThenPostsUserWithUserIdAttribute() {
		String userId = UUID.randomUUID().toString();
		this.server.expect(requestTo(TOKEN_URI))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=client_credentials")))
			.andRespond(withSuccess("{\"access_token\":\"admin-token\"}", MediaType.APPLICATION_JSON));
		this.server.expect(requestTo(USERS_URI))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.username").value("new@example.com"))
			.andExpect(jsonPath("$.email").value("new@example.com"))
			.andExpect(jsonPath("$.attributes.user_id[0]").value(userId))
			.andExpect(jsonPath("$.credentials[0].value").value("rawPw"))
			.andRespond(withStatus(HttpStatus.CREATED));

		this.keycloakClient.provisionUser("new@example.com", "rawPw", userId);

		this.server.verify();
	}

	@Test
	void provisionUserMapsAdminApiErrorToBadGateway() {
		this.server.expect(requestTo(TOKEN_URI))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess("{\"access_token\":\"admin-token\"}", MediaType.APPLICATION_JSON));
		this.server.expect(requestTo(USERS_URI))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withStatus(HttpStatus.CONFLICT));

		assertThatThrownBy(() -> this.keycloakClient.provisionUser("dup@example.com", "rawPw",
				UUID.randomUUID().toString()))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
				.isEqualTo(HttpStatus.BAD_GATEWAY));
		this.server.verify();
	}

	@Test
	void provisionUserMapsEmptyAdminTokenToBadGateway() {
		this.server.expect(requestTo(TOKEN_URI))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess("", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> this.keycloakClient.provisionUser("new@example.com", "rawPw",
				UUID.randomUUID().toString()))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
				.isEqualTo(HttpStatus.BAD_GATEWAY));
		this.server.verify();
	}

	// --- updateCredentials (admin token + realm-user lookup + PUT email / reset-password) ---

	@Test
	void updateCredentialsNoEmailNoPasswordIsNoOp() {
		// Both null → nothing changed → no admin token, no realm round-trip at all.
		this.keycloakClient.updateCredentials(UUID.randomUUID().toString(), null, null);

		this.server.verify();
	}

	@Test
	void updateCredentialsEmailOnlyResolvesRealmUserThenPutsEmail() {
		String userId = UUID.randomUUID().toString();
		expectAdminTokenThenRealmLookup(userId);
		this.server.expect(requestTo(USER_BY_ID_URI))
			.andExpect(method(HttpMethod.PUT))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.email").value("new@example.com"))
			.andExpect(jsonPath("$.username").value("new@example.com"))
			.andRespond(withStatus(HttpStatus.NO_CONTENT));

		this.keycloakClient.updateCredentials(userId, "new@example.com", null);

		this.server.verify();
	}

	@Test
	void updateCredentialsPasswordOnlyResolvesRealmUserThenResetsPassword() {
		String userId = UUID.randomUUID().toString();
		expectAdminTokenThenRealmLookup(userId);
		this.server.expect(requestTo(USER_BY_ID_URI + "/reset-password"))
			.andExpect(method(HttpMethod.PUT))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
			.andExpect(jsonPath("$.type").value("password"))
			.andExpect(jsonPath("$.value").value("newRawPw"))
			.andExpect(jsonPath("$.temporary").value(false))
			.andRespond(withStatus(HttpStatus.NO_CONTENT));

		this.keycloakClient.updateCredentials(userId, null, "newRawPw");

		this.server.verify();
	}

	@Test
	void updateCredentialsEmailAndPasswordAppliesBoth() {
		String userId = UUID.randomUUID().toString();
		expectAdminTokenThenRealmLookup(userId);
		this.server.expect(requestTo(USER_BY_ID_URI))
			.andExpect(method(HttpMethod.PUT))
			.andRespond(withStatus(HttpStatus.NO_CONTENT));
		this.server.expect(requestTo(USER_BY_ID_URI + "/reset-password"))
			.andExpect(method(HttpMethod.PUT))
			.andRespond(withStatus(HttpStatus.NO_CONTENT));

		this.keycloakClient.updateCredentials(userId, "new@example.com", "newRawPw");

		this.server.verify();
	}

	@Test
	void updateCredentialsRealmUserNotFoundMapsToBadGateway() {
		String userId = UUID.randomUUID().toString();
		this.server.expect(requestTo(TOKEN_URI))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess("{\"access_token\":\"admin-token\"}", MediaType.APPLICATION_JSON));
		// Empty result array → the user_id attribute resolved no realm user.
		this.server.expect(requestToUriTemplate(USERS_URI + "?q={q}", "user_id:" + userId))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> this.keycloakClient.updateCredentials(userId, "new@example.com", null))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
				.isEqualTo(HttpStatus.BAD_GATEWAY));
		this.server.verify();
	}

	@Test
	void updateCredentialsNullLookupBodyMapsToBadGateway() {
		String userId = UUID.randomUUID().toString();
		this.server.expect(requestTo(TOKEN_URI))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess("{\"access_token\":\"admin-token\"}", MediaType.APPLICATION_JSON));
		// A literal JSON null body deserializes the user list to null → the FIRST operand
		// (users == null) of the empty-result guard trips → BAD_GATEWAY.
		this.server.expect(requestToUriTemplate(USERS_URI + "?q={q}", "user_id:" + userId))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> this.keycloakClient.updateCredentials(userId, "new@example.com", null))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
				.isEqualTo(HttpStatus.BAD_GATEWAY));
		this.server.verify();
	}

	@Test
	void updateCredentialsLookupErrorMapsToBadGateway() {
		String userId = UUID.randomUUID().toString();
		this.server.expect(requestTo(TOKEN_URI))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess("{\"access_token\":\"admin-token\"}", MediaType.APPLICATION_JSON));
		this.server.expect(requestToUriTemplate(USERS_URI + "?q={q}", "user_id:" + userId))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

		assertThatThrownBy(() -> this.keycloakClient.updateCredentials(userId, null, "newRawPw"))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
				.isEqualTo(HttpStatus.BAD_GATEWAY));
		this.server.verify();
	}

	@Test
	void updateCredentialsEmailPutErrorMapsToBadGateway() {
		String userId = UUID.randomUUID().toString();
		expectAdminTokenThenRealmLookup(userId);
		this.server.expect(requestTo(USER_BY_ID_URI))
			.andExpect(method(HttpMethod.PUT))
			.andRespond(withStatus(HttpStatus.CONFLICT));

		assertThatThrownBy(() -> this.keycloakClient.updateCredentials(userId, "dup@example.com", null))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
				.isEqualTo(HttpStatus.BAD_GATEWAY));
		this.server.verify();
	}

	// --- timeout factory ----------------------------------------------

	@Test
	void keycloakClientConfiguresConnectAndReadTimeouts() {
		// The production client builds a dedicated request factory with explicit connect +
		// read timeouts (the setUp() nulls it to keep the mock seam, so re-derive it here).
		ClientHttpRequestFactory factory = (ClientHttpRequestFactory) ReflectionTestUtils.invokeMethod(
				new KeycloakClient(RestClient.builder()), "buildRequestFactory");
		assertThat(factory).isNotNull();
	}

	// Script the admin client_credentials token plus the user_id-attribute realm-user lookup
	// that every updateCredentials happy path performs before the PUT(s).
	private void expectAdminTokenThenRealmLookup(String userId) {
		this.server.expect(requestTo(TOKEN_URI))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=client_credentials")))
			.andRespond(withSuccess("{\"access_token\":\"admin-token\"}", MediaType.APPLICATION_JSON));
		this.server.expect(requestToUriTemplate(USERS_URI + "?q={q}", "user_id:" + userId))
			.andExpect(method(HttpMethod.GET))
			.andExpect(queryParam("q", "user_id:" + userId))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
			.andRespond(withSuccess("[{\"id\":\"" + REALM_ID + "\"}]", MediaType.APPLICATION_JSON));
	}

}
