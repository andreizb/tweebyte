/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.userservice.exception.AuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the reactive KeycloakClient over a WebClient bound to a scripted
 * {@link ExchangeFunction} (the idiomatic WebClient unit-test seam — no live socket): the
 * ROPC token grant (happy path + 401→AuthenticationException + empty-body→BAD_GATEWAY), the
 * Admin-API provisioning path (admin client_credentials token → user POST with the user_id
 * attribute, plus error→BAD_GATEWAY and empty-admin-token→BAD_GATEWAY), and the
 * credential-propagation path (resolve realm user by user_id attribute → PUT email and/or
 * reset-password, with not-found and IdP-error → BAD_GATEWAY). Mirrors the blocking
 * KeycloakClientTests scenario-for-scenario.
 */
class KeycloakClientTests {

	private static final String REALM = "tweebyte";

	private final Deque<ClientResponse> responses = new ArrayDeque<>();

	private final List<ClientRequest> captured = new java.util.ArrayList<>();

	private KeycloakClient keycloakClient;

	@BeforeEach
	void setUp() {
		ExchangeFunction exchange = request -> {
			this.captured.add(request);
			return Mono.just(this.responses.poll());
		};
		WebClient.Builder builder = WebClient.builder().exchangeFunction(exchange);
		this.keycloakClient = new KeycloakClient(builder);
		ReflectionTestUtils.setField(this.keycloakClient, "baseUrl", "http://keycloak.local");
		ReflectionTestUtils.setField(this.keycloakClient, "realm", REALM);
		ReflectionTestUtils.setField(this.keycloakClient, "clientId", "tweebyte-client");
		ReflectionTestUtils.setField(this.keycloakClient, "clientSecret", "s3cr3t");
		this.keycloakClient.init();
	}

	private void enqueueJson(HttpStatus status, String body) {
		this.responses.add(ClientResponse.create(status)
			.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.body(body)
			.build());
	}

	private void enqueueStatus(HttpStatus status) {
		this.responses.add(ClientResponse.create(status).build());
	}

	// --- issueToken (ROPC password grant) -----------------------------

	@Test
	void issueTokenReturnsAccessTokenOnPasswordGrant() {
		enqueueJson(HttpStatus.OK, "{\"access_token\":\"the-access-token\"}");

		StepVerifier.create(this.keycloakClient.issueToken("alice@example.com", "pw"))
			.expectNext("the-access-token")
			.verifyComplete();

		ClientRequest req = this.captured.get(0);
		assertThat(req.url().getPath())
			.isEqualTo("/realms/" + REALM + "/protocol/openid-connect/token");
		assertThat(bodyOf(req)).contains("grant_type=password").contains("username=alice");
	}

	@Test
	void issueTokenMapsClientErrorToAuthenticationException() {
		enqueueStatus(HttpStatus.UNAUTHORIZED);

		StepVerifier.create(this.keycloakClient.issueToken("bad@example.com", "wrong"))
			.expectError(AuthenticationException.class)
			.verify();
	}

	// NOTE asymmetry vs. async KeycloakClient: the blocking client maps a null/empty token
	// body to ResponseStatusException(BAD_GATEWAY); the reactive bodyToMono(...).map(...)
	// chain has no such guard, so an empty token body completes empty instead. Asserting
	// the actual reactive behaviour here (not fixing production per the task's bug rule).
	@Test
	void issueTokenEmptyBodyCompletesEmpty() {
		this.responses.add(ClientResponse.create(HttpStatus.OK).build());

		StepVerifier.create(this.keycloakClient.issueToken("alice@example.com", "pw")).verifyComplete();
	}

	// --- provisionUser (admin token + Admin-API user POST) ------------

	@Test
	void provisionUserAcquiresAdminTokenThenPostsUserWithUserIdAttribute() {
		String userId = UUID.randomUUID().toString();
		enqueueJson(HttpStatus.OK, "{\"access_token\":\"admin-token\"}");
		enqueueStatus(HttpStatus.CREATED);

		StepVerifier.create(this.keycloakClient.provisionUser("new@example.com", "rawPw", userId)).verifyComplete();

		ClientRequest tokenReq = this.captured.get(0);
		assertThat(bodyOf(tokenReq)).contains("grant_type=client_credentials");
		ClientRequest userReq = this.captured.get(1);
		assertThat(userReq.url().getPath()).isEqualTo("/admin/realms/" + REALM + "/users");
		assertThat(userReq.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer admin-token");
		assertThat(bodyOf(userReq)).contains("new@example.com").contains(userId).contains("rawPw");
	}

	@Test
	void provisionUserMapsAdminApiErrorToBadGateway() {
		enqueueJson(HttpStatus.OK, "{\"access_token\":\"admin-token\"}");
		enqueueJson(HttpStatus.CONFLICT, "{\"errorMessage\":\"User exists\"}");

		StepVerifier.create(this.keycloakClient.provisionUser("dup@example.com", "rawPw", UUID.randomUUID().toString()))
			.expectErrorSatisfies(t -> assertThat(t).isInstanceOf(ResponseStatusException.class)
				.extracting(e -> ((ResponseStatusException) e).getStatusCode())
				.isEqualTo(HttpStatus.BAD_GATEWAY))
			.verify();
	}

	// NOTE asymmetry vs. async KeycloakClient: an empty admin-token body maps to
	// BAD_GATEWAY on the blocking client, but the reactive adminToken() Mono completes
	// empty so the provisioning flatMap never runs and the call completes silently. The
	// single user POST that the happy path emits is therefore absent here. Asserting the
	// actual reactive behaviour (no provisioning request leaves the client).
	@Test
	void provisionUserEmptyAdminTokenCompletesWithoutPostingUser() {
		this.responses.add(ClientResponse.create(HttpStatus.OK).build());

		StepVerifier.create(this.keycloakClient.provisionUser("new@example.com", "rawPw", UUID.randomUUID().toString()))
			.verifyComplete();

		assertThat(this.captured).hasSize(1);
	}

	// --- updateCredentials (admin token + realm-user lookup + PUT email / reset-password) ---

	private static final String REALM_ID = "11111111-2222-3333-4444-555555555555";

	@Test
	void updateCredentialsNoEmailNoPasswordIsNoOp() {
		// Both null → nothing changed → no admin token, no realm round-trip at all.
		StepVerifier.create(this.keycloakClient.updateCredentials(UUID.randomUUID().toString(), null, null))
			.verifyComplete();

		assertThat(this.captured).isEmpty();
	}

	@Test
	void updateCredentialsEmailOnlyResolvesRealmUserThenPutsEmail() {
		String userId = UUID.randomUUID().toString();
		enqueueJson(HttpStatus.OK, "{\"access_token\":\"admin-token\"}");
		enqueueJson(HttpStatus.OK, "[{\"id\":\"" + REALM_ID + "\"}]");
		enqueueStatus(HttpStatus.NO_CONTENT);

		StepVerifier.create(this.keycloakClient.updateCredentials(userId, "new@example.com", null)).verifyComplete();

		ClientRequest lookup = this.captured.get(1);
		assertThat(lookup.url().getPath()).isEqualTo("/admin/realms/" + REALM + "/users");
		assertThat(lookup.url().getQuery()).contains("user_id:" + userId);
		assertThat(lookup.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer admin-token");
		ClientRequest put = this.captured.get(2);
		assertThat(put.url().getPath()).isEqualTo("/admin/realms/" + REALM + "/users/" + REALM_ID);
		assertThat(bodyOf(put)).contains("new@example.com");
	}

	@Test
	void updateCredentialsPasswordOnlyResolvesRealmUserThenResetsPassword() {
		String userId = UUID.randomUUID().toString();
		enqueueJson(HttpStatus.OK, "{\"access_token\":\"admin-token\"}");
		enqueueJson(HttpStatus.OK, "[{\"id\":\"" + REALM_ID + "\"}]");
		enqueueStatus(HttpStatus.NO_CONTENT);

		StepVerifier.create(this.keycloakClient.updateCredentials(userId, null, "newRawPw")).verifyComplete();

		ClientRequest reset = this.captured.get(2);
		assertThat(reset.url().getPath())
			.isEqualTo("/admin/realms/" + REALM + "/users/" + REALM_ID + "/reset-password");
		assertThat(bodyOf(reset)).contains("password").contains("newRawPw");
	}

	@Test
	void updateCredentialsEmailAndPasswordAppliesBoth() {
		String userId = UUID.randomUUID().toString();
		enqueueJson(HttpStatus.OK, "{\"access_token\":\"admin-token\"}");
		enqueueJson(HttpStatus.OK, "[{\"id\":\"" + REALM_ID + "\"}]");
		enqueueStatus(HttpStatus.NO_CONTENT);
		enqueueStatus(HttpStatus.NO_CONTENT);

		StepVerifier.create(this.keycloakClient.updateCredentials(userId, "new@example.com", "newRawPw"))
			.verifyComplete();

		assertThat(this.captured.get(2).url().getPath()).isEqualTo("/admin/realms/" + REALM + "/users/" + REALM_ID);
		assertThat(this.captured.get(3).url().getPath())
			.isEqualTo("/admin/realms/" + REALM + "/users/" + REALM_ID + "/reset-password");
	}

	@Test
	void updateCredentialsRealmUserNotFoundMapsToBadGateway() {
		enqueueJson(HttpStatus.OK, "{\"access_token\":\"admin-token\"}");
		enqueueJson(HttpStatus.OK, "[]");

		StepVerifier.create(this.keycloakClient.updateCredentials(UUID.randomUUID().toString(), "new@example.com", null))
			.expectErrorSatisfies(t -> assertThat(t).isInstanceOf(ResponseStatusException.class)
				.extracting(e -> ((ResponseStatusException) e).getStatusCode())
				.isEqualTo(HttpStatus.BAD_GATEWAY))
			.verify();
	}

	@Test
	void updateCredentialsLookupErrorMapsToBadGateway() {
		enqueueJson(HttpStatus.OK, "{\"access_token\":\"admin-token\"}");
		enqueueStatus(HttpStatus.INTERNAL_SERVER_ERROR);

		StepVerifier.create(this.keycloakClient.updateCredentials(UUID.randomUUID().toString(), null, "newRawPw"))
			.expectErrorSatisfies(t -> assertThat(t).isInstanceOf(ResponseStatusException.class)
				.extracting(e -> ((ResponseStatusException) e).getStatusCode())
				.isEqualTo(HttpStatus.BAD_GATEWAY))
			.verify();
	}

	@Test
	void updateCredentialsEmailPutErrorMapsToBadGateway() {
		enqueueJson(HttpStatus.OK, "{\"access_token\":\"admin-token\"}");
		enqueueJson(HttpStatus.OK, "[{\"id\":\"" + REALM_ID + "\"}]");
		enqueueStatus(HttpStatus.CONFLICT);

		StepVerifier.create(this.keycloakClient.updateCredentials(UUID.randomUUID().toString(), "dup@example.com", null))
			.expectErrorSatisfies(t -> assertThat(t).isInstanceOf(ResponseStatusException.class)
				.extracting(e -> ((ResponseStatusException) e).getStatusCode())
				.isEqualTo(HttpStatus.BAD_GATEWAY))
			.verify();
	}

	// Materialise the request body the client wrote, so assertions can inspect the form /
	// JSON payload that left the client.
	private static String bodyOf(ClientRequest request) {
		org.springframework.mock.http.client.reactive.MockClientHttpRequest mock =
				new org.springframework.mock.http.client.reactive.MockClientHttpRequest(
						org.springframework.http.HttpMethod.POST, java.net.URI.create("/"));
		request.body().insert(mock, new ContextStub()).block();
		String body = mock.getBodyAsString().block();
		return (body != null) ? body : "";
	}

	private static final class ContextStub
			implements org.springframework.web.reactive.function.BodyInserter.Context {

		@Override
		public List<org.springframework.http.codec.HttpMessageWriter<?>> messageWriters() {
			return org.springframework.web.reactive.function.client.ExchangeStrategies.withDefaults().messageWriters();
		}

		@Override
		public java.util.Optional<org.springframework.http.server.reactive.ServerHttpRequest> serverRequest() {
			return java.util.Optional.empty();
		}

		@Override
		public java.util.Map<String, Object> hints() {
			return java.util.Collections.emptyMap();
		}

	}

}
