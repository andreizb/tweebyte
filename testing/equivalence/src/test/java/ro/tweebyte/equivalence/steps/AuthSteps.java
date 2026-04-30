package ro.tweebyte.equivalence.steps;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import ro.tweebyte.equivalence.support.RestApi;
import ro.tweebyte.equivalence.support.ScenarioContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Authentication steps — register, login, JWT-assertions.
 *
 * Step phrases are deliberately stack-agnostic; the same .feature file runs
 * on async and reactive without modification.
 */
public class AuthSteps {

    /** Convenience: register a fresh user with a known handle. The handle is used as a key in ScenarioContext. */
    @Given("user {string} is registered with email {string} and password {string}")
    public void userIsRegistered(String handle, String email, String password) {
        ScenarioContext ctx = ScenarioContext.current();
        Map<String, String> form = new LinkedHashMap<>();
        form.put("userName", handle);
        form.put("email", email);
        form.put("password", password);
        form.put("birthDate", "01/01/1995");
        form.put("biography", "fe-test");
        RestApi.postMultipart("/user-service/auth/register", null, form);
        assertEquals(200, ctx.lastStatus, "register failed: " + ctx.lastBody);
        String token = ctx.lastJson.get("token").asText();
        ctx.jwtByHandle.put(handle, token);
        ctx.emailByHandle.put(handle, email);
        ctx.passwordByHandle.put(handle, password);
        ctx.userIdByHandle.put(handle, extractUserId(token));
        ctx.currentActor = handle;
    }

    @When("a client registers user {string} with email {string} and password {string}")
    public void aClientRegisters(String handle, String email, String password) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("userName", handle);
        form.put("email", email);
        form.put("password", password);
        form.put("birthDate", "01/01/1995");
        form.put("biography", "fe-test");
        RestApi.postMultipart("/user-service/auth/register", null, form);
    }

    @When("a client registers user {string} with email {string} password {string} biography {string}")
    public void aClientRegistersWithBio(String handle, String email, String password, String biography) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("userName", handle);
        form.put("email", email);
        form.put("password", password);
        form.put("birthDate", "01/01/1995");
        form.put("biography", biography);
        RestApi.postMultipart("/user-service/auth/register", null, form);
    }

    /** Same as the convenience register, but flips is_private=true. Used by
     *  follow-request scenarios that need a private followee. */
    @Given("private user {string} is registered with email {string} and password {string}")
    public void privateUserIsRegistered(String handle, String email, String password) {
        ScenarioContext ctx = ScenarioContext.current();
        Map<String, String> form = new LinkedHashMap<>();
        form.put("userName", handle);
        form.put("email", email);
        form.put("password", password);
        form.put("birthDate", "01/01/1995");
        form.put("biography", "fe-test");
        form.put("isPrivate", "true");
        RestApi.postMultipart("/user-service/auth/register", null, form);
        assertEquals(200, ctx.lastStatus, "register failed: " + ctx.lastBody);
        String token = ctx.lastJson.get("token").asText();
        ctx.jwtByHandle.put(handle, token);
        ctx.emailByHandle.put(handle, email);
        ctx.passwordByHandle.put(handle, password);
        ctx.userIdByHandle.put(handle, extractUserId(token));
        ctx.currentActor = handle;
    }

    @When("a client logs in with email {string} and password {string}")
    public void aClientLogsIn(String email, String password) {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        RestApi.postJson("/user-service/auth/login", null, body);
    }

    @When("user {string} logs in")
    public void userLogsIn(String handle) {
        ScenarioContext ctx = ScenarioContext.current();
        String body = "{\"email\":\"" + ctx.emailByHandle.get(handle)
                + "\",\"password\":\"" + ctx.passwordByHandle.get(handle) + "\"}";
        RestApi.postJson("/user-service/auth/login", null, body);
    }

    @Then("the response status is {int}")
    public void responseStatusIs(int expected) {
        ScenarioContext ctx = ScenarioContext.current();
        assertEquals(expected, ctx.lastStatus,
                "expected " + expected + " but got " + ctx.lastStatus + " body=" + ctx.lastBody);
    }

    @And("the response body contains a JWT token")
    public void responseHasJwt() {
        ScenarioContext ctx = ScenarioContext.current();
        assertNotNull(ctx.lastJson, "response body is not JSON: " + ctx.lastBody);
        JsonNode tok = ctx.lastJson.get("token");
        assertNotNull(tok, "no `token` field in response: " + ctx.lastBody);
        String s = tok.asText();
        assertTrue(s != null && s.split("\\.").length == 3, "not a JWT-shaped string: " + s);
    }

    @And("the response body does not contain a JWT token")
    public void responseHasNoJwt() {
        ScenarioContext ctx = ScenarioContext.current();
        if (ctx.lastJson == null) return;
        JsonNode tok = ctx.lastJson.get("token");
        assertTrue(tok == null || tok.isNull(), "unexpected JWT in response: " + ctx.lastBody);
    }

    /** Pull the `user_id` claim out of an unsigned-decoded JWT (we trust the issuer in tests). */
    private static UUID extractUserId(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            com.fasterxml.jackson.databind.JsonNode n = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload);
            return UUID.fromString(n.get("user_id").asText());
        } catch (Exception e) {
            throw new RuntimeException("could not extract user_id from JWT", e);
        }
    }
}
