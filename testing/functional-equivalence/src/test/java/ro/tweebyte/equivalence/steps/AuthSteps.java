/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.equivalence.steps;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import ro.tweebyte.equivalence.support.RestApi;
import ro.tweebyte.equivalence.support.ScenarioContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Authentication steps — register, login, JWT-assertions.
 *
 * Step phrases are deliberately stack-agnostic; the same .feature file runs on async and
 * reactive without modification.
 */
public class AuthSteps {

	private static final String REGISTER_BIRTH_DATE = "1995-01-01";

	/**
	 * Convenience: register a fresh user with a known handle. The handle is used as a key
	 * in ScenarioContext.
	 */
	@Given("user {string} is registered with email {string} and password {string}")
	public void userIsRegistered(String handle, String email, String password) {
		ScenarioContext ctx = ScenarioContext.current();
		Map<String, String> form = new LinkedHashMap<>();
		form.put("userName", handle);
		form.put("email", email);
		form.put("password", password);
		form.put("birthDate", REGISTER_BIRTH_DATE);
		form.put("biography", "functional-equivalence");
		RestApi.postMultipart("/user-service/auth/register", null, form);
		assertThat(ctx.lastStatus).as("register failed: " + ctx.lastBody).isEqualTo(200);
		String token = ctx.lastJson.get("token").asText();
		ctx.jwtByHandle.put(handle, token);
		ctx.emailByHandle.put(handle, email);
		ctx.passwordByHandle.put(handle, password);
		ctx.userIdByHandle.put(handle, extractUserId(token));
	}

	@When("a client registers user {string} with email {string} and password {string}")
	public void aClientRegisters(String handle, String email, String password) {
		Map<String, String> form = new LinkedHashMap<>();
		form.put("userName", handle);
		form.put("email", email);
		form.put("password", password);
		form.put("birthDate", REGISTER_BIRTH_DATE);
		form.put("biography", "functional-equivalence");
		RestApi.postMultipart("/user-service/auth/register", null, form);
	}

	@When("a client registers user {string} with email {string} password {string} biography {string}")
	public void aClientRegistersWithBio(String handle, String email, String password, String biography) {
		Map<String, String> form = new LinkedHashMap<>();
		form.put("userName", handle);
		form.put("email", email);
		form.put("password", password);
		form.put("birthDate", REGISTER_BIRTH_DATE);
		form.put("biography", biography);
		RestApi.postMultipart("/user-service/auth/register", null, form);
	}

	/**
	 * Same as the convenience register, but flips is_private=true. Used by follow-request
	 * scenarios that need a private followee.
	 */
	@Given("private user {string} is registered with email {string} and password {string}")
	public void privateUserIsRegistered(String handle, String email, String password) {
		ScenarioContext ctx = ScenarioContext.current();
		Map<String, String> form = new LinkedHashMap<>();
		form.put("userName", handle);
		form.put("email", email);
		form.put("password", password);
		form.put("birthDate", REGISTER_BIRTH_DATE);
		form.put("biography", "functional-equivalence");
		form.put("isPrivate", "true");
		RestApi.postMultipart("/user-service/auth/register", null, form);
		assertThat(ctx.lastStatus).as("register failed: " + ctx.lastBody).isEqualTo(200);
		String token = ctx.lastJson.get("token").asText();
		ctx.jwtByHandle.put(handle, token);
		ctx.emailByHandle.put(handle, email);
		ctx.passwordByHandle.put(handle, password);
		ctx.userIdByHandle.put(handle, extractUserId(token));
	}

	@When("a client logs in with email {string} and password {string}")
	public void aClientLogsIn(String email, String password) {
		String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
		RestApi.postJson("/user-service/auth/login", null, body);
	}

	@When("a client logs in with email {string} and password {string} using token {string}")
	public void aClientLogsInWithToken(String email, String password, String token) {
		String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
		RestApi.postJson("/user-service/auth/login", token, body);
	}

	@When("user {string} logs in")
	public void userLogsIn(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		String body = "{\"email\":\"" + ctx.emailByHandle.get(handle) + "\",\"password\":\""
				+ ctx.passwordByHandle.get(handle) + "\"}";
		RestApi.postJson("/user-service/auth/login", null, body);
	}

	@Then("the response status is {int}")
	public void responseStatusIs(int expected) {
		ScenarioContext ctx = ScenarioContext.current();
		assertThat(ctx.lastStatus).as("expected " + expected + " but got " + ctx.lastStatus + " body=" + ctx.lastBody)
			.isEqualTo(expected);
	}

	@And("the response body contains a JWT token")
	public void responseHasJwt() {
		ScenarioContext ctx = ScenarioContext.current();
		assertThat(ctx.lastJson).as("response body is not JSON: " + ctx.lastBody).isNotNull();
		JsonNode tok = ctx.lastJson.get("token");
		assertThat(tok).as("no `token` field in response: " + ctx.lastBody).isNotNull();
		String s = tok.asText();
		assertThat(s != null && s.split("\\.").length == 3).as("not a JWT-shaped string: " + s).isTrue();
	}

	@And("the response body does not contain a JWT token")
	public void responseHasNoJwt() {
		ScenarioContext ctx = ScenarioContext.current();
		if (ctx.lastJson == null) {
			return;
		}
		JsonNode tok = ctx.lastJson.get("token");
		assertThat(tok == null || tok.isNull()).as("unexpected JWT in response: " + ctx.lastBody).isTrue();
	}

	// -------------------------------------------------------------------------
	// B1 coverage scenarios — AuthenticationService.register profilePictureId (AS-1)
	// -------------------------------------------------------------------------

	@When("a client registers user {string} with email {string} and password {string} and profilePictureId {string}")
	public void aClientRegistersWithPictureId(String handle, String email, String password, String pictureId) {
		Map<String, String> form = new LinkedHashMap<>();
		form.put("userName", handle);
		form.put("email", email);
		form.put("password", password);
		form.put("birthDate", REGISTER_BIRTH_DATE);
		form.put("biography", "functional-equivalence");
		form.put("profilePictureId", pictureId);
		RestApi.postMultipart("/user-service/auth/register", null, form);
	}

	@When("a client registers user {string} with email {string} and password {string} and profilePictureId from last upload")
	public void aClientRegistersWithUploadedPictureId(String handle, String email, String password) {
		ScenarioContext ctx = ScenarioContext.current();
		assertThat(ctx.lastUploadId).as("no uploaded media id tracked").isNotNull();
		Map<String, String> form = new LinkedHashMap<>();
		form.put("userName", handle);
		form.put("email", email);
		form.put("password", password);
		form.put("birthDate", REGISTER_BIRTH_DATE);
		form.put("biography", "functional-equivalence");
		form.put("profilePictureId", ctx.lastUploadId.toString());
		RestApi.postMultipart("/user-service/auth/register", null, form);
	}

	// -------------------------------------------------------------------------
	// B1 coverage scenarios — UserMapper isPrivate explicit false (UM-1)
	// -------------------------------------------------------------------------

	@When("a client registers user {string} with email {string} and password {string} and isPrivate {string}")
	public void aClientRegistersWithIsPrivate(String handle, String email, String password, String isPrivate) {
		Map<String, String> form = new LinkedHashMap<>();
		form.put("userName", handle);
		form.put("email", email);
		form.put("password", password);
		form.put("birthDate", REGISTER_BIRTH_DATE);
		form.put("biography", "functional-equivalence");
		form.put("isPrivate", isPrivate);
		RestApi.postMultipart("/user-service/auth/register", null, form);
	}

	/**
	 * Pull the `user_id` claim out of an unsigned-decoded JWT (we trust the issuer in
	 * tests).
	 */
	private static UUID extractUserId(String jwt) {
		try {
			String[] parts = jwt.split("\\.");
			String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
			JsonNode n = new ObjectMapper().readTree(payload);
			return UUID.fromString(n.get("user_id").asText());
		}
		catch (IOException | RuntimeException ex) {
			throw new RuntimeException("could not extract user_id from JWT", ex);
		}
	}

}
