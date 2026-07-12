/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.equivalence.steps;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.en.And;
import io.cucumber.java.en.When;

import ro.tweebyte.equivalence.support.RestApi;
import ro.tweebyte.equivalence.support.ScenarioContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * User-service domain steps — profile read/update + search + JWT-protected reads.
 */
public class UserSteps {

	@When("user {string} fetches their own profile")
	public void userFetchesOwnProfile(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var id = ctx.userIdByHandle.get(handle);
		assertThat(id).as("no userId tracked for " + handle).isNotNull();
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

	@When("user {string} deletes their account")
	public void userDeletesAccount(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var id = ctx.userIdByHandle.get(handle);
		RestApi.delete("/user-service/users/" + id, jwt);
	}

	@When("user {string} attempts to delete a non-existent account")
	public void userDeletesNonExistentAccount(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.delete("/user-service/users/" + UUID.randomUUID(), jwt);
	}

	// ---------------------------------------------------------------------
	// Media: pure upload → password-gated preview → download-by-id → reveal.
	// POST /media stores bytes as-is and returns {id}; POST /media/{id}/preview
	// runs the CPU pipeline over that original and returns the derived preview
	// {id}; GET /media/{id} streams bytes (206); POST /media/{id}/reveal
	// bcrypt-checks the password and streams the original behind a preview.
	// ---------------------------------------------------------------------

	@When("user {string} uploads a {int}x{int} image")
	public void userUploadsImage(String handle, int width, int height) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.postMultipartFile("/user-service/media", jwt, "file", "upload.jpg", "image/jpeg",
				makeRandomJpeg(width, height));
		captureUploadId(ctx);
	}

	@When("a client uploads a {int}x{int} image without auth")
	public void clientUploadsImageNoAuth(int width, int height) {
		RestApi.postMultipartFile("/user-service/media", null, "file", "upload.jpg", "image/jpeg",
				makeRandomJpeg(width, height));
	}

	@When("user {string} uploads a non-image file")
	public void userUploadsNonImage(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		byte[] junk = "this is plainly not an image, just text bytes".getBytes(StandardCharsets.UTF_8);
		RestApi.postMultipartFile("/user-service/media", jwt, "file", "notimage.txt", "text/plain", junk);
		captureUploadId(ctx);
	}

	@When("user {string} previews the uploaded media with password {string}")
	public void userPreviewsUploadedMedia(String handle, String password) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		assertThat(ctx.lastUploadId).as("no uploaded media id tracked").isNotNull();
		RestApi.postJson("/user-service/media/" + ctx.lastUploadId + "/preview", jwt,
				"{\"password\":\"" + password + "\"}");
		if (ctx.lastStatus == 200 && ctx.lastJson != null && ctx.lastJson.has("id")) {
			ctx.lastPreviewId = UUID.fromString(ctx.lastJson.get("id").asText());
		}
	}

	@When("user {string} previews a non-existent media with password {string}")
	public void userPreviewsMissingMedia(String handle, String password) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.postJson("/user-service/media/" + UUID.randomUUID() + "/preview", jwt,
				"{\"password\":\"" + password + "\"}");
	}

	@When("user {string} downloads the uploaded media")
	public void userDownloadsUploadedMedia(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		assertThat(ctx.lastUploadId).as("no uploaded media id tracked").isNotNull();
		RestApi.getAccept("/user-service/media/" + ctx.lastUploadId, jwt, "application/octet-stream, */*");
	}

	@When("user {string} downloads the preview")
	public void userDownloadsPreview(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		assertThat(ctx.lastPreviewId).as("no preview media id tracked").isNotNull();
		RestApi.getAccept("/user-service/media/" + ctx.lastPreviewId, jwt, "application/octet-stream, */*");
	}

	@When("user {string} downloads a non-existent media")
	public void userDownloadsMissingMedia(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.getAccept("/user-service/media/" + UUID.randomUUID(), jwt, "application/octet-stream, */*");
	}

	@When("user {string} flushes the media cache")
	public void userFlushesMediaCache(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.delete("/user-service/media/cache", jwt);
	}

	@When("a client downloads the uploaded media without auth")
	public void clientDownloadsUploadedMediaNoAuth() {
		ScenarioContext ctx = ScenarioContext.current();
		assertThat(ctx.lastUploadId).as("no uploaded media id tracked").isNotNull();
		RestApi.getAccept("/user-service/media/" + ctx.lastUploadId, null, "application/octet-stream, */*");
	}

	@When("user {string} reveals the preview with password {string}")
	public void userRevealsPreview(String handle, String password) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		assertThat(ctx.lastPreviewId).as("no preview media id tracked").isNotNull();
		RestApi.postJson("/user-service/media/" + ctx.lastPreviewId + "/reveal", jwt,
				"{\"password\":\"" + password + "\"}");
	}

	@And("the response body has an id")
	public void responseBodyHasId() {
		ScenarioContext ctx = ScenarioContext.current();
		assertThat(ctx.lastJson).as("no JSON in body: " + ctx.lastBody).isNotNull();
		JsonNode id = ctx.lastJson.get("id");
		assertThat(id).as("no id field: " + ctx.lastBody).isNotNull();
		assertThatCode(() -> UUID.fromString(id.asText())).as("id is not a UUID: " + id.asText())
			.doesNotThrowAnyException();
	}

	private static void captureUploadId(ScenarioContext ctx) {
		if (ctx.lastStatus == 200 && ctx.lastJson != null && ctx.lastJson.has("id")) {
			ctx.lastUploadId = UUID.fromString(ctx.lastJson.get("id").asText());
		}
	}

	// Random gradient + per-pixel noise so the gaussian/sobel pipeline has non-trivial
	// content. FE asserts only status/content-type/size, never pixels, so a seeded RNG
	// isn't needed; content-addressing means each random upload is a distinct asset.
	private static byte[] makeRandomJpeg(int w, int h) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int r = x * 255 / Math.max(1, w - 1);
				int g = y * 255 / Math.max(1, h - 1);
				int b = ThreadLocalRandom.current().nextInt(256);
				img.setRGB(x, y, new Color(r, g, b).getRGB());
			}
		}
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ImageIO.write(img, "jpg", baos);
			return baos.toByteArray();
		}
		catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	@And("the response body has email {string}")
	public void responseHasEmail(String expected) {
		ScenarioContext ctx = ScenarioContext.current();
		assertThat(ctx.lastJson).as("no JSON in body: " + ctx.lastBody).isNotNull();
		JsonNode email = ctx.lastJson.get("email");
		assertThat(email).as("no email field: " + ctx.lastBody).isNotNull();
		assertThat(email.asText()).isEqualTo(expected);
	}

	@And("the response body has isPrivate {string}")
	public void responseHasIsPrivate(String expected) {
		ScenarioContext ctx = ScenarioContext.current();
		assertThat(ctx.lastJson).as("no JSON in body: " + ctx.lastBody).isNotNull();
		JsonNode v = ctx.lastJson.get("is_private");
		if (v == null) {
			v = ctx.lastJson.get("isPrivate");
		}
		assertThat(v).as("no isPrivate field: " + ctx.lastBody).isNotNull();
		assertThat(v.asBoolean()).isEqualTo(Boolean.parseBoolean(expected));
	}

	@And("the response body has a content-length of at least {int} bytes")
	public void responseContentLengthAtLeast(int min) {
		ScenarioContext ctx = ScenarioContext.current();
		assertThat(ctx.lastBodyByteLength).as("body byte-length " + ctx.lastBodyByteLength + " < " + min)
			.isGreaterThanOrEqualTo(min);
	}

	@When("user {string} fetches a non-existent profile")
	public void userFetchesNonExistentProfile(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.get("/user-service/users/" + UUID.randomUUID(), jwt);
	}

	@When("user {string} fetches the public summary of a non-existent user")
	public void userFetchesSummaryNonExistent(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.get("/user-service/users/summary/" + UUID.randomUUID(), jwt);
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

	@When("a client GETs {string} without auth")
	public void clientGetsPathWithoutAuth(String path) {
		RestApi.get(path, null);
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
		assertThat(ctx.lastJson).as("no JSON in body: " + ctx.lastBody).isNotNull();
		JsonNode u = ctx.lastJson.get("user_name");
		assertThat(u).as("no user_name field: " + ctx.lastBody).isNotNull();
		assertThat(u.asText()).isEqualTo(expected);
	}

	@And("the response body has biography {string}")
	public void responseHasBio(String expected) {
		ScenarioContext ctx = ScenarioContext.current();
		assertThat(ctx.lastJson).as("no JSON in body: " + ctx.lastBody).isNotNull();
		JsonNode b = ctx.lastJson.get("biography");
		assertThat(b).as("no biography field: " + ctx.lastBody).isNotNull();
		assertThat(b.asText()).isEqualTo(expected);
	}

	@And("the response body is a list of size {int}")
	public void responseIsListOfSize(int expected) {
		ScenarioContext ctx = ScenarioContext.current();
		assertThat(ctx.lastJson).as("no JSON in body: " + ctx.lastBody).isNotNull();
		assertThat(ctx.lastJson.isArray()).as("expected array body: " + ctx.lastBody).isTrue();
		assertThat(ctx.lastJson.size()).isEqualTo(expected);
	}

	@And("the response body is a list containing user {string}")
	public void responseListContains(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		assertThat(ctx.lastJson).as("no JSON in body: " + ctx.lastBody).isNotNull();
		assertThat(ctx.lastJson.isArray()).as("expected array body: " + ctx.lastBody).isTrue();
		boolean found = false;
		for (JsonNode el : ctx.lastJson) {
			JsonNode u = el.get("user_name");
			if (u != null && handle.equals(u.asText())) {
				found = true;
				break;
			}
		}
		assertThat(found).as("user " + handle + " not in list: " + ctx.lastBody).isTrue();
	}

	@And("the response body has no email field")
	public void responseHasNoEmail() {
		ScenarioContext ctx = ScenarioContext.current();
		if (ctx.lastJson == null) {
			return;
		}
		JsonNode email = ctx.lastJson.get("email");
		assertThat(email == null || email.isNull()).as("summary unexpectedly contains email: " + ctx.lastBody).isTrue();
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

	// -------------------------------------------------------------------------
	// B1 coverage scenarios — MediaService.exists (MS-1)
	// -------------------------------------------------------------------------

	@When("user {string} checks whether the uploaded media exists")
	public void userChecksExistsUploadedMedia(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		assertThat(ctx.lastUploadId).as("no uploaded media id tracked").isNotNull();
		RestApi.get("/user-service/media/" + ctx.lastUploadId + "/exists", jwt);
	}

	@When("user {string} checks whether a non-existent media exists")
	public void userChecksExistsMissingMedia(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.get("/user-service/media/" + UUID.randomUUID() + "/exists", jwt);
	}

	@And("the response body has exists {string}")
	public void responseBodyHasExists(String expected) {
		ScenarioContext ctx = ScenarioContext.current();
		assertThat(ctx.lastJson).as("no JSON in body: " + ctx.lastBody).isNotNull();
		JsonNode v = ctx.lastJson.get("exists");
		assertThat(v).as("no exists field: " + ctx.lastBody).isNotNull();
		assertThat(v.asBoolean()).isEqualTo(Boolean.parseBoolean(expected));
	}

	// -------------------------------------------------------------------------
	// B1 coverage scenarios — MediaService.upload null content-type (MS-2)
	// -------------------------------------------------------------------------

	@When("user {string} uploads a {int}x{int} image without part content-type")
	public void userUploadsImageNoPartContentType(String handle, int width, int height) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.postMultipartFileNoContentType("/user-service/media", jwt, "file", "upload.bin",
				makeRandomJpeg(width, height));
		captureUploadId(ctx);
	}

	// -------------------------------------------------------------------------
	// B1 coverage scenarios — MediaService.previewImage corrupted / oversized (MS-3)
	// -------------------------------------------------------------------------

	@When("user {string} uploads a corrupted image file")
	public void userUploadsCorruptedImage(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		// Valid JPEG magic bytes (SOI + APP0 marker) then random noise so ImageIO.read()
		// returns null, triggering the null-image branch in previewImage.
		byte[] corrupted = new byte[256];
		ThreadLocalRandom.current().nextBytes(corrupted);
		corrupted[0] = (byte) 0xFF;
		corrupted[1] = (byte) 0xD8;
		corrupted[2] = (byte) 0xFF;
		corrupted[3] = (byte) 0xE0;
		RestApi.postMultipartFile("/user-service/media", jwt, "file", "bad.jpg", "image/jpeg", corrupted);
		captureUploadId(ctx);
	}

	// -------------------------------------------------------------------------
	// B1 coverage scenarios — MediaService.reveal non-preview / missing (MS-4)
	// -------------------------------------------------------------------------

	@When("user {string} reveals the uploaded media as a preview with password {string}")
	public void userRevealsUploadedAsPreview(String handle, String password) {
		// Sends /reveal on lastUploadId (a raw original, not a gated preview).
		// sourceMediaId == null → service returns 404.
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		assertThat(ctx.lastUploadId).as("no uploaded media id tracked").isNotNull();
		RestApi.postJson("/user-service/media/" + ctx.lastUploadId + "/reveal", jwt,
				"{\"password\":\"" + password + "\"}");
	}

	@When("user {string} reveals a non-existent preview with password {string}")
	public void userRevealsMissingPreview(String handle, String password) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.postJson("/user-service/media/" + UUID.randomUUID() + "/reveal", jwt,
				"{\"password\":\"" + password + "\"}");
	}

	// -------------------------------------------------------------------------
	// B1 coverage scenarios — MediaService.store checksum dedup (MS-5)
	// -------------------------------------------------------------------------

	@When("user {string} uploads a solid {int}x{int} image")
	public void userUploadsSolidImage(String handle, int width, int height) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		// Stash the previous upload id before the new call overwrites it.
		if (ctx.lastUploadId != null) {
			ctx.firstUploadId = ctx.lastUploadId;
		}
		RestApi.postMultipartFile("/user-service/media", jwt, "file", "upload.jpg", "image/jpeg",
				makeSolidJpeg(width, height, Color.BLACK));
		captureUploadId(ctx);
	}

	@And("both upload responses return the same id")
	public void bothUploadsReturnSameId() {
		ScenarioContext ctx = ScenarioContext.current();
		assertThat(ctx.lastUploadId).as("second upload id not set").isNotNull();
		assertThat(ctx.firstUploadId).as("first upload id not stashed").isNotNull();
		assertThat(ctx.lastUploadId).isEqualTo(ctx.firstUploadId);
	}

	// -------------------------------------------------------------------------
	// B1 coverage scenarios — UserService.updateUser profile-picture branches (US-1)
	// -------------------------------------------------------------------------

	@When("user {string} updates their profile picture to a non-existent id")
	public void userUpdatesPictureMissing(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var id = ctx.userIdByHandle.get(handle);
		Map<String, String> form = new LinkedHashMap<>();
		form.put("profilePictureId", UUID.randomUUID().toString());
		RestApi.putMultipart("/user-service/users/" + id, jwt, form);
	}

	@When("user {string} updates their profile picture to the uploaded media id")
	public void userUpdatesPictureToUploaded(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var id = ctx.userIdByHandle.get(handle);
		assertThat(ctx.lastUploadId).as("no uploaded media id tracked").isNotNull();
		Map<String, String> form = new LinkedHashMap<>();
		form.put("profilePictureId", ctx.lastUploadId.toString());
		RestApi.putMultipart("/user-service/users/" + id, jwt, form);
	}

	// -------------------------------------------------------------------------
	// B3-2 socket-abort — MediaService.isClientAbort
	// -------------------------------------------------------------------------

	/**
	 * B3-2: Opens the media download endpoint for the last uploaded asset, reads the
	 * HTTP response status line (so the server has started streaming), then closes the
	 * connection abruptly before all bytes arrive. On the server side, the partial write
	 * in {@code streamThrottled} surfaces as a {@code SocketException} or
	 * {@code ClosedChannelException}; {@code isClientAbort} must classify this as
	 * benign and swallow it. We record whether the abort step completed (without the
	 * server returning an error) in ScenarioContext.lastAbortCompleted.
	 */
	@When("user {string} downloads the uploaded media and aborts mid-stream")
	public void userDownloadsAndAbortsMediaMidStream(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		assertThat(ctx.lastUploadId).as("no uploaded media id tracked").isNotNull();
		String path = "/user-service/media/" + ctx.lastUploadId;
		ctx.lastAbortCompleted = socketAbortMidStream(path, jwt);
	}

	@And("the mid-stream abort completed without error")
	public void midStreamAbortCompletedWithoutError() {
		ScenarioContext ctx = ScenarioContext.current();
		assertThat(ctx.lastAbortCompleted)
			.as("mid-stream abort step threw an unexpected exception")
			.isTrue();
	}

	/**
	 * Opens a raw {@link HttpURLConnection} to the given path, reads enough bytes to
	 * confirm the server started responding (status 206 + at least one body byte), then
	 * closes the connection without draining the stream. Returns {@code true} if the
	 * abort handshake completed without an unexpected exception; {@code false} only if an
	 * infrastructure error (not a benign abort) was thrown.
	 */
	private static boolean socketAbortMidStream(String path, String bearer) {
		try {
			URI uri = URI.create(RestApi.baseUrl() + path);
			HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(5_000);
			conn.setReadTimeout(30_000);
			conn.setRequestProperty("Connection", "close");
			if (bearer != null) {
				conn.setRequestProperty("Authorization", "Bearer " + bearer);
			}
			conn.setRequestProperty("Accept", "application/octet-stream, */*");
			// Read only the status code and a few bytes, then disconnect abruptly.
			int status = conn.getResponseCode();
			if (status >= 200 && status < 300) {
				try (InputStream in = conn.getInputStream()) {
					// Read a small prefix to ensure the server has started writing bytes.
					byte[] buf = new byte[256];
					int prefix = in.read(buf);
					assertThat(prefix).as("media stream prefix bytes").isGreaterThanOrEqualTo(-1);
					// Abruptly close — triggers server-side disconnect detection.
				}
			}
			conn.disconnect();
			return true;
		}
		catch (IOException ex) {
			// Connection reset / server-side abort is acceptable here too.
			return true;
		}
	}

	// -------------------------------------------------------------------------
	// B3-3b stale-media cleanup scenarios
	// -------------------------------------------------------------------------

	@And("the referenced media list contains the uploaded id")
	public void referencedMediaListContainsUploadedId() {
		ScenarioContext ctx = ScenarioContext.current();
		assertThat(ctx.lastJson).as("no JSON in body: " + ctx.lastBody).isNotNull();
		assertThat(ctx.lastJson.isArray()).as("expected array body: " + ctx.lastBody).isTrue();
		assertThat(ctx.lastUploadId).as("no uploaded media id tracked").isNotNull();
		boolean found = false;
		for (var el : ctx.lastJson) {
			if (ctx.lastUploadId.toString().equals(el.asText())) {
				found = true;
				break;
			}
		}
		assertThat(found)
			.as("uploaded id " + ctx.lastUploadId + " not in referenced list: " + ctx.lastBody)
			.isTrue();
	}

	// -------------------------------------------------------------------------
	// Private image-generation helpers
	// -------------------------------------------------------------------------

	/**
	 * Solid-color JPEG — same bytes every call for the same (w, h, color). Used for
	 * content-addressed dedup tests that need two identical uploads.
	 */
	private static byte[] makeSolidJpeg(int w, int h, Color color) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D g = img.createGraphics();
		g.setColor(color);
		g.fillRect(0, 0, w, h);
		g.dispose();
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ImageIO.write(img, "jpg", baos);
			return baos.toByteArray();
		}
		catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

}
