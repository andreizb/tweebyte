package ro.tweebyte.equivalence.steps;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import ro.tweebyte.equivalence.support.RestApi;
import ro.tweebyte.equivalence.support.ScenarioContext;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * User-service domain steps — profile read/update + search + JWT-protected reads.
 */
public class UserSteps {

    @When("user {string} fetches their own profile")
    public void userFetchesOwnProfile(String handle) {
        ScenarioContext ctx = ScenarioContext.current();
        var id = ctx.userIdByHandle.get(handle);
        assertNotNull(id, "no userId tracked for " + handle);
        var jwt = ctx.jwtByHandle.get(handle);
        RestApi.get("/user-service/users/" + id, jwt);
    }

    @When("user {string} fetches profile of {string}")
    public void userFetchesOtherProfile(String requester, String target) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(requester);
        var targetId = ctx.userIdByHandle.get(target);
        RestApi.get("/user-service/users/" + targetId, jwt);
    }

    @When("a client fetches the public summary of {string}")
    public void clientFetchesPublicSummary(String target) {
        ScenarioContext ctx = ScenarioContext.current();
        var targetId = ctx.userIdByHandle.get(target);
        RestApi.get("/user-service/users/summary/" + targetId, null);
    }

    @When("user {string} fetches the public summary of {string}")
    public void userFetchesPublicSummary(String requester, String target) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(requester);
        var targetId = ctx.userIdByHandle.get(target);
        RestApi.get("/user-service/users/summary/" + targetId, jwt);
    }

    @When("a client fetches the public summary of {string} by name")
    public void clientFetchesPublicSummaryByName(String target) {
        RestApi.get("/user-service/users/summary/name/" + RestApi.segment(target), null);
    }

    @When("user {string} fetches the public summary of {string} by name")
    public void userFetchesPublicSummaryByName(String requester, String target) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(requester);
        RestApi.get("/user-service/users/summary/name/" + RestApi.segment(target), jwt);
    }

    @When("a client searches users for {string}")
    public void clientSearchesUsers(String term) {
        RestApi.get("/user-service/users/search/" + RestApi.segment(term), null);
    }

    @When("user {string} searches users for {string}")
    public void userSearchesUsers(String requester, String term) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(requester);
        RestApi.get("/user-service/users/search/" + RestApi.segment(term), jwt);
    }

    @When("user {string} updates their biography to {string}")
    public void userUpdatesBio(String handle, String bio) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var id = ctx.userIdByHandle.get(handle);
        Map<String, String> form = new LinkedHashMap<>();
        form.put("biography", bio);
        RestApi.putMultipart("/user-service/users/" + id, jwt, form);
    }

    @When("user {string} updates their userName to {string}")
    public void userUpdatesUserName(String handle, String userName) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var id = ctx.userIdByHandle.get(handle);
        Map<String, String> form = new LinkedHashMap<>();
        form.put("userName", userName);
        RestApi.putMultipart("/user-service/users/" + id, jwt, form);
    }

    @When("user {string} updates their email to {string}")
    public void userUpdatesEmail(String handle, String email) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var id = ctx.userIdByHandle.get(handle);
        Map<String, String> form = new LinkedHashMap<>();
        form.put("email", email);
        RestApi.putMultipart("/user-service/users/" + id, jwt, form);
    }

    @When("user {string} updates their isPrivate flag to {string}")
    public void userUpdatesIsPrivate(String handle, String value) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var id = ctx.userIdByHandle.get(handle);
        Map<String, String> form = new LinkedHashMap<>();
        form.put("isPrivate", value);
        RestApi.putMultipart("/user-service/users/" + id, jwt, form);
    }

    @When("user {string} updates their birthDate to {string}")
    public void userUpdatesBirthDate(String handle, String birthDate) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var id = ctx.userIdByHandle.get(handle);
        Map<String, String> form = new LinkedHashMap<>();
        form.put("birthDate", birthDate);
        RestApi.putMultipart("/user-service/users/" + id, jwt, form);
    }

    @When("user {string} updates their password to {string}")
    public void userUpdatesPassword(String handle, String password) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var id = ctx.userIdByHandle.get(handle);
        Map<String, String> form = new LinkedHashMap<>();
        form.put("password", password);
        RestApi.putMultipart("/user-service/users/" + id, jwt, form);
        // also update the local cache so re-login uses the new password
        ctx.passwordByHandle.put(handle, password);
    }

    @When("user {string} updates multiple fields userName {string} biography {string} isPrivate {string}")
    public void userUpdatesMultipleFields(String handle, String userName, String bio, String isPrivate) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var id = ctx.userIdByHandle.get(handle);
        Map<String, String> form = new LinkedHashMap<>();
        form.put("userName", userName);
        form.put("biography", bio);
        form.put("isPrivate", isPrivate);
        RestApi.putMultipart("/user-service/users/" + id, jwt, form);
    }

    @When("user {string} sends an empty profile update")
    public void userSendsEmptyUpdate(String handle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var id = ctx.userIdByHandle.get(handle);
        RestApi.putMultipart("/user-service/users/" + id, jwt, new LinkedHashMap<>());
    }

    @When("user {string} downloads media")
    public void userDownloadsMedia(String handle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        RestApi.getAccept("/user-service/media/", jwt, "application/octet-stream, */*");
    }

    @When("a client downloads media without auth")
    public void clientDownloadsMediaNoAuth() {
        RestApi.getAccept("/user-service/media/", null, "application/octet-stream, */*");
    }

    @And("the response body has email {string}")
    public void responseHasEmail(String expected) {
        ScenarioContext ctx = ScenarioContext.current();
        assertNotNull(ctx.lastJson, "no JSON in body: " + ctx.lastBody);
        JsonNode e = ctx.lastJson.get("email");
        assertNotNull(e, "no email field: " + ctx.lastBody);
        assertEquals(expected, e.asText());
    }

    @And("the response body has isPrivate {string}")
    public void responseHasIsPrivate(String expected) {
        ScenarioContext ctx = ScenarioContext.current();
        assertNotNull(ctx.lastJson, "no JSON in body: " + ctx.lastBody);
        JsonNode v = ctx.lastJson.get("is_private");
        if (v == null) v = ctx.lastJson.get("isPrivate");
        assertNotNull(v, "no isPrivate field: " + ctx.lastBody);
        assertEquals(Boolean.parseBoolean(expected), v.asBoolean());
    }

    @And("the response body has a content-length of at least {int} bytes")
    public void responseContentLengthAtLeast(int min) {
        ScenarioContext ctx = ScenarioContext.current();
        assertNotNull(ctx.lastBody, "no body recorded");
        assertTrue(ctx.lastBody.length() >= min,
                "body length " + ctx.lastBody.length() + " < " + min);
    }

    @When("user {string} fetches a non-existent profile")
    public void userFetchesNonExistentProfile(String handle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        RestApi.get("/user-service/users/" + java.util.UUID.randomUUID(), jwt);
    }

    @When("user {string} fetches the public summary of a non-existent user")
    public void userFetchesSummaryNonExistent(String handle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        RestApi.get("/user-service/users/summary/" + java.util.UUID.randomUUID(), jwt);
    }

    @When("user {string} fetches the public summary by name {string}")
    public void userFetchesSummaryByName(String handle, String name) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        RestApi.get("/user-service/users/summary/name/" + RestApi.segment(name), jwt);
    }

    @When("a client fetches the profile of {string} without auth")
    public void clientFetchesWithoutAuth(String target) {
        ScenarioContext ctx = ScenarioContext.current();
        var targetId = ctx.userIdByHandle.get(target);
        RestApi.get("/user-service/users/" + targetId, null);
    }

    @When("a client fetches the profile of {string} with token {string}")
    public void clientFetchesWithBadToken(String target, String token) {
        ScenarioContext ctx = ScenarioContext.current();
        var targetId = ctx.userIdByHandle.get(target);
        RestApi.get("/user-service/users/" + targetId, token);
    }

    @And("the response body has user_name {string}")
    public void responseHasUsername(String expected) {
        ScenarioContext ctx = ScenarioContext.current();
        assertNotNull(ctx.lastJson, "no JSON in body: " + ctx.lastBody);
        JsonNode u = ctx.lastJson.get("user_name");
        assertNotNull(u, "no user_name field: " + ctx.lastBody);
        assertEquals(expected, u.asText());
    }

    @And("the response body has biography {string}")
    public void responseHasBio(String expected) {
        ScenarioContext ctx = ScenarioContext.current();
        assertNotNull(ctx.lastJson, "no JSON in body: " + ctx.lastBody);
        JsonNode b = ctx.lastJson.get("biography");
        assertNotNull(b, "no biography field: " + ctx.lastBody);
        assertEquals(expected, b.asText());
    }

    @And("the response body is a list of size {int}")
    public void responseIsListOfSize(int expected) {
        ScenarioContext ctx = ScenarioContext.current();
        assertNotNull(ctx.lastJson, "no JSON in body: " + ctx.lastBody);
        assertTrue(ctx.lastJson.isArray(), "expected array body: " + ctx.lastBody);
        assertEquals(expected, ctx.lastJson.size());
    }

    @And("the response body is a list containing user {string}")
    public void responseListContains(String handle) {
        ScenarioContext ctx = ScenarioContext.current();
        assertNotNull(ctx.lastJson, "no JSON in body: " + ctx.lastBody);
        assertTrue(ctx.lastJson.isArray(), "expected array body: " + ctx.lastBody);
        boolean found = false;
        for (JsonNode el : ctx.lastJson) {
            JsonNode u = el.get("user_name");
            if (u != null && handle.equals(u.asText())) { found = true; break; }
        }
        assertTrue(found, "user " + handle + " not in list: " + ctx.lastBody);
    }

    @And("the response body has no email field")
    public void responseHasNoEmail() {
        ScenarioContext ctx = ScenarioContext.current();
        if (ctx.lastJson == null) return;
        JsonNode e = ctx.lastJson.get("email");
        assertTrue(e == null || e.isNull(), "summary unexpectedly contains email: " + ctx.lastBody);
    }

    // ---------------------------------------------------------------------
    // Negative-path and edge-case steps.
    // ---------------------------------------------------------------------

    @When("user {string} attempts to update their email to {string}")
    public void userAttemptsUpdateEmail(String handle, String email) {
        // Same as updates-their-email but worded for negative-path scenarios.
        userUpdatesEmail(handle, email);
    }

    @When("user {string} attempts to update their userName to {string}")
    public void userAttemptsUpdateUserName(String handle, String userName) {
        userUpdatesUserName(handle, userName);
    }

    @When("user {string} updates their profile sending a malformed birthDate {string}")
    public void userUpdatesMalformedBirthDate(String handle, String value) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var id = ctx.userIdByHandle.get(handle);
        Map<String, String> form = new LinkedHashMap<>();
        form.put("birthDate", value);
        RestApi.putMultipart("/user-service/users/" + id, jwt, form);
    }

    @When("user {string} fetches a profile with malformed id {string}")
    public void userFetchesMalformedProfile(String handle, String malformed) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        RestApi.get("/user-service/users/" + malformed, jwt);
    }

    @When("user {string} fetches the public summary with malformed id {string}")
    public void userFetchesSummaryMalformed(String handle, String malformed) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        RestApi.get("/user-service/users/summary/" + malformed, jwt);
    }
}
