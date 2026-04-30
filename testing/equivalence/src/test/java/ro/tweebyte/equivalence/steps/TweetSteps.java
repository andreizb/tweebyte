package ro.tweebyte.equivalence.steps;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import ro.tweebyte.equivalence.support.RestApi;
import ro.tweebyte.equivalence.support.ScenarioContext;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tweet-service domain steps — CRUD, search, hashtags, AI streaming.
 *
 * Tweets are tracked by handle in ScenarioContext via a side channel: when
 * "user X posts tweet 'foo'" succeeds, the response's tweet UUID is stored
 * under the tweet handle (the body content used as a key).
 */
public class TweetSteps {

    @When("user {string} posts tweet {string}")
    public void userPostsTweet(String handle, String content) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var userId = ctx.userIdByHandle.get(handle);
        String body = "{\"content\":\"" + escape(content) + "\"}";
        RestApi.postJson("/tweet-service/tweets/" + userId, jwt, body);
        if (ctx.lastStatus == 200 && ctx.lastJson != null && ctx.lastJson.has("id")) {
            ctx.lastTweetIdByContent.put(content, UUID.fromString(ctx.lastJson.get("id").asText()));
            ctx.lastTweetIdByHandle.put(handle, UUID.fromString(ctx.lastJson.get("id").asText()));
        }
    }

    @When("user {string} fetches tweet {string}")
    public void userFetchesTweet(String handle, String tweetKey) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var tid = ctx.lastTweetIdByContent.get(tweetKey);
        assertNotNull(tid, "no tweet tracked for key '" + tweetKey + "'");
        RestApi.get("/tweet-service/tweets/" + tid, jwt);
    }

    @When("user {string} fetches a non-existent tweet")
    public void userFetchesMissingTweet(String handle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        RestApi.get("/tweet-service/tweets/" + UUID.randomUUID(), jwt);
    }

    @When("user {string} lists their own tweets")
    public void userListsOwnTweets(String handle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var userId = ctx.userIdByHandle.get(handle);
        RestApi.get("/tweet-service/tweets/user/" + userId, jwt);
    }

    @When("user {string} lists tweets of {string}")
    public void userListsTweetsOf(String requester, String target) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(requester);
        var targetId = ctx.userIdByHandle.get(target);
        RestApi.get("/tweet-service/tweets/user/" + targetId, jwt);
    }

    @When("user {string} updates tweet {string} content to {string}")
    public void userUpdatesTweet(String handle, String tweetKey, String newContent) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var tid = ctx.lastTweetIdByContent.get(tweetKey);
        assertNotNull(tid, "no tweet tracked for key '" + tweetKey + "'");
        String body = "{\"content\":\"" + escape(newContent) + "\"}";
        RestApi.putJson("/tweet-service/tweets/" + tid, jwt, body);
    }

    @When("user {string} deletes tweet {string}")
    public void userDeletesTweet(String handle, String tweetKey) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var tid = ctx.lastTweetIdByContent.get(tweetKey);
        assertNotNull(tid, "no tweet tracked for key '" + tweetKey + "'");
        RestApi.delete("/tweet-service/tweets/" + tid, jwt);
    }

    @When("user {string} searches tweets for {string}")
    public void userSearchesTweets(String handle, String term) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        RestApi.get("/tweet-service/tweets/search/" + RestApi.segment(term), jwt);
    }

    @When("user {string} searches tweets by hashtag {string}")
    public void userSearchesByHashtag(String handle, String tag) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        RestApi.get("/tweet-service/tweets/search/hashtag/" + RestApi.segment(tag), jwt);
    }

    @When("user {string} fetches popular hashtags")
    public void userFetchesPopular(String handle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        RestApi.get("/tweet-service/tweets/hashtag/popular", jwt);
    }

    @When("user {string} fetches their feed")
    public void userFetchesFeed(String handle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var uid = ctx.userIdByHandle.get(handle);
        RestApi.get("/tweet-service/tweets/" + uid + "/feed", jwt);
    }

    @When("user {string} fetches summary of tweet {string}")
    public void userFetchesTweetSummary(String handle, String tweetKey) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var tid = ctx.lastTweetIdByContent.get(tweetKey);
        assertNotNull(tid, "no tweet tracked for key '" + tweetKey + "'");
        RestApi.get("/tweet-service/tweets/" + tid + "/summary", jwt);
    }

    @When("user {string} fetches tweets summary of {string}")
    public void userFetchesUserTweetsSummary(String handle, String target) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var tid = ctx.userIdByHandle.get(target);
        RestApi.get("/tweet-service/tweets/user/" + tid + "/summary", jwt);
    }

    @When("user {string} updates a non-existent tweet")
    public void userUpdatesNonExistentTweet(String handle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        String body = "{\"content\":\"updated content body here\"}";
        RestApi.putJson("/tweet-service/tweets/" + UUID.randomUUID(), jwt, body);
    }

    @When("user {string} deletes a non-existent tweet")
    public void userDeletesNonExistentTweet(String handle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        RestApi.delete("/tweet-service/tweets/" + UUID.randomUUID(), jwt);
    }

    @When("user {string} updates tweet {string} content to empty string")
    public void userUpdatesTweetEmpty(String handle, String tweetKey) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var tid = ctx.lastTweetIdByContent.get(tweetKey);
        String body = "{\"content\":\"\"}";
        RestApi.putJson("/tweet-service/tweets/" + tid, jwt, body);
    }

    @When("user {string} fetches summary of a non-existent tweet")
    public void userFetchesSummaryNonExistent(String handle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        RestApi.get("/tweet-service/tweets/" + UUID.randomUUID() + "/summary", jwt);
    }

    @When("user {string} fetches the AI mock-stream")
    public void userFetchesMockStream(String handle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        sseGet("/tweet-service/tweets/ai/mock-stream", jwt);
    }

    @When("user {string} fetches the AI mock-stream with {int} tokens and {int} ms ITL")
    public void userFetchesMockStreamWithParams(String handle, int tokens, int itlMs) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        sseGet("/tweet-service/tweets/ai/mock-stream?tokens=" + tokens + "&itlMs=" + itlMs, jwt);
    }

    @When("user {string} uploads a {int}x{int} image to the media filter")
    public void userUploadsImageToMediaFilter(String handle, int width, int height) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        byte[] jpegBytes = makeRandomJpeg(width, height);
        RestApi.postMultipartFile("/tweet-service/media/filter", jwt, "file",
                "test.jpg", "image/jpeg", jpegBytes);
    }

    @When("a client uploads a {int}x{int} image to the media filter without auth")
    public void clientUploadsImageToMediaFilterNoAuth(int width, int height) {
        byte[] jpegBytes = makeRandomJpeg(width, height);
        RestApi.postMultipartFile("/tweet-service/media/filter", null, "file",
                "test.jpg", "image/jpeg", jpegBytes);
    }

    @When("user {string} uploads a non-image file to the media filter")
    public void userUploadsNonImage(String handle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        byte[] junk = "this is plainly not an image, just text bytes".getBytes(StandardCharsets.UTF_8);
        RestApi.postMultipartFile("/tweet-service/media/filter", jwt, "file",
                "notimage.txt", "text/plain", junk);
    }

    private static byte[] makeRandomJpeg(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        // Random gradient + noise to ensure non-trivial image content for the
        // gaussian + sobel pipeline. Deterministic seeded RNG would be better
        // for reproducibility but the FE asserts only status, not bytes.
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = (x * 255 / Math.max(1, w - 1));
                int gr = (y * 255 / Math.max(1, h - 1));
                int b = (ThreadLocalRandom.current().nextInt(256));
                img.setRGB(x, y, new Color(r, gr, b).getRGB());
            }
        }
        g.dispose();
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "jpg", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @When("user {string} requests AI summarize for prompt {string}")
    public void userRequestsAiSummarize(String handle, String prompt) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        String body = "{\"prompt\":\"" + escape(prompt) + "\"}";
        ssePost("/tweet-service/tweets/ai/summarize", jwt, body);
    }

    @When("user {string} requests AI buffered for prompt {string}")
    public void userRequestsAiBuffered(String handle, String prompt) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        String body = "{\"prompt\":\"" + escape(prompt) + "\"}";
        RestApi.postJson("/tweet-service/tweets/ai/buffered", jwt, body);
    }

    @When("user {string} requests AI summarize-with-tool for prompt {string}")
    public void userRequestsAiTool(String handle, String prompt) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var userId = ctx.userIdByHandle.get(handle);
        String body = "{\"prompt\":\"" + escape(prompt) + "\"}";
        ssePost("/tweet-service/tweets/ai/summarize-with-tool?userId=" + userId, jwt, body);
    }

    @And("the SSE response yielded at least {int} chunks")
    public void sseChunks(int min) {
        ScenarioContext ctx = ScenarioContext.current();
        int chunks = (int) ctx.lastBody.lines().filter(l -> l.startsWith("data:")).count();
        assertTrue(chunks >= min, "expected ≥" + min + " SSE chunks, got " + chunks
                + " — body length: " + ctx.lastBody.length());
    }

    @And("the response content-type is {string}")
    public void responseContentType(String expected) {
        ScenarioContext ctx = ScenarioContext.current();
        assertNotNull(ctx.lastContentType, "no content-type captured");
        assertTrue(ctx.lastContentType.toLowerCase().startsWith(expected.toLowerCase()),
                "expected content-type starts with " + expected + " but got " + ctx.lastContentType);
    }

    @And("the response body contains hashtag {string}")
    public void responseHasHashtag(String tag) {
        ScenarioContext ctx = ScenarioContext.current();
        assertNotNull(ctx.lastJson, "no JSON: " + ctx.lastBody);
        JsonNode hashtags = ctx.lastJson.get("hashtags");
        assertNotNull(hashtags, "no hashtags field: " + ctx.lastBody);
        assertTrue(hashtags.isArray() || hashtags.isObject(), "hashtags is not array/object");
        boolean found = false;
        for (JsonNode h : hashtags) {
            JsonNode v = h.get("value");
            if (v == null) v = h.get("name");
            if (v == null) v = h.get("text");
            if (v != null && tag.equalsIgnoreCase(v.asText())) { found = true; break; }
        }
        assertTrue(found, "hashtag '" + tag + "' not in body: " + ctx.lastBody);
    }

    @And("the response body has content {string}")
    public void responseBodyHasContent(String expected) {
        ScenarioContext ctx = ScenarioContext.current();
        assertNotNull(ctx.lastJson, "no JSON in body: " + ctx.lastBody);
        JsonNode c = ctx.lastJson.get("content");
        assertNotNull(c, "no content field: " + ctx.lastBody);
        assertEquals(expected, c.asText());
    }

    @When("we wait {int} ms for async background tasks")
    public void weWait(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------------------------------------------------------------------
    // Negative-path and edge-case steps.
    // ---------------------------------------------------------------------

    @When("user {string} attempts to update tweet of {string} with content {string}")
    public void userAttemptsUpdateOthersTweet(String handle, String otherHandle, String newContent) {
        // The other user's tweet id is in ctx.lastTweetIdByHandle from a prior post.
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        UUID tid = ctx.lastTweetIdByHandle.get(otherHandle);
        assertNotNull(tid, "no tweet tracked for handle " + otherHandle);
        String body = "{\"content\":\"" + escape(newContent) + "\"}";
        RestApi.putJson("/tweet-service/tweets/" + tid, jwt, body);
    }

    @When("user {string} attempts to delete tweet of {string}")
    public void userAttemptsDeleteOthersTweet(String handle, String otherHandle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        UUID tid = ctx.lastTweetIdByHandle.get(otherHandle);
        assertNotNull(tid, "no tweet tracked for handle " + otherHandle);
        RestApi.delete("/tweet-service/tweets/" + tid, jwt);
    }

    @When("user {string} fetches tweet by malformed id {string}")
    public void userFetchesTweetMalformed(String handle, String malformed) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        RestApi.get("/tweet-service/tweets/" + malformed, jwt);
    }

    @When("user {string} searches tweets for empty term")
    public void userSearchesTweetsEmpty(String handle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        RestApi.get("/tweet-service/tweets/search/", jwt);
    }

    @When("user {string} searches tweets by hashtag with no matches")
    public void userSearchesHashtagNoMatch(String handle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        RestApi.get("/tweet-service/tweets/search/hashtag/zzznoexistnoexist", jwt);
    }

    @When("user {string} fetches feed of a non-existent user")
    public void userFetchesFeedNonExistent(String handle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        RestApi.get("/tweet-service/tweets/" + UUID.randomUUID() + "/feed", jwt);
    }

    @When("user {string} lists tweets of a non-existent user")
    public void userListsTweetsNonExistent(String handle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        RestApi.get("/tweet-service/tweets/user/" + UUID.randomUUID(), jwt);
    }

    @When("user {string} fetches user tweets summary of a non-existent user")
    public void userFetchesUserSummaryNonExistent(String handle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        RestApi.get("/tweet-service/tweets/user/" + UUID.randomUUID() + "/summary", jwt);
    }

    @When("user {string} posts tweet with single mention of {string}")
    public void userPostsWithMention(String handle, String mentionHandle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var uid = ctx.userIdByHandle.get(handle);
        String content = "hello @" + mentionHandle + " good day to you";
        String body = "{\"content\":\"" + escape(content) + "\"}";
        RestApi.postJson("/tweet-service/tweets/" + uid, jwt, body);
        if (ctx.lastStatus == 200 && ctx.lastJson != null && ctx.lastJson.has("id")) {
            ctx.lastTweetIdByContent.put(content, UUID.fromString(ctx.lastJson.get("id").asText()));
            ctx.lastTweetIdByHandle.put(handle, UUID.fromString(ctx.lastJson.get("id").asText()));
        }
    }

    @When("user {string} posts tweet mentioning a missing user {string}")
    public void userPostsMentionMissingUser(String handle, String missingHandle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var uid = ctx.userIdByHandle.get(handle);
        String content = "hello @" + missingHandle + " who doesnt exist anywhere";
        String body = "{\"content\":\"" + escape(content) + "\"}";
        RestApi.postJson("/tweet-service/tweets/" + uid, jwt, body);
        if (ctx.lastStatus == 200 && ctx.lastJson != null && ctx.lastJson.has("id")) {
            ctx.lastTweetIdByContent.put(content, UUID.fromString(ctx.lastJson.get("id").asText()));
            ctx.lastTweetIdByHandle.put(handle, UUID.fromString(ctx.lastJson.get("id").asText()));
        }
    }

    @When("user {string} updates their last tweet to mention {string}")
    public void userUpdatesLastTweetMention(String handle, String mentionHandle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        UUID tid = ctx.lastTweetIdByHandle.get(handle);
        assertNotNull(tid, "no tweet tracked for handle " + handle);
        String content = "now mentioning @" + mentionHandle + " in updated content";
        String body = "{\"content\":\"" + escape(content) + "\"}";
        RestApi.putJson("/tweet-service/tweets/" + tid, jwt, body);
    }

    @When("user {string} updates their last tweet to plain content {string}")
    public void userUpdatesLastTweetPlain(String handle, String content) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        UUID tid = ctx.lastTweetIdByHandle.get(handle);
        assertNotNull(tid, "no tweet tracked for handle " + handle);
        String body = "{\"content\":\"" + escape(content) + "\"}";
        RestApi.putJson("/tweet-service/tweets/" + tid, jwt, body);
    }

    @When("user {string} fetches summary of tweet by malformed id {string}")
    public void userFetchesSummaryMalformed(String handle, String malformed) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        RestApi.get("/tweet-service/tweets/" + malformed + "/summary", jwt);
    }

    @When("user {string} searches by hashtag with leading hash {string}")
    public void userSearchesHashtagWithHash(String handle, String tag) {
        // Hashtag includes a literal '#' in the URL — should be normalised by the service.
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        RestApi.get("/tweet-service/tweets/search/hashtag/" + RestApi.segment(tag), jwt);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static void sseGet(String path, String bearer) { sseRequest("GET", path, bearer, null); }
    private static void ssePost(String path, String bearer, String body) { sseRequest("POST", path, bearer, body); }

    /** Streaming SSE request — reads the response body fully. Not for high-throughput testing, just functional verification. */
    private static void sseRequest(String method, String path, String bearer, String body) {
        ScenarioContext ctx = ScenarioContext.current();
        try {
            URI uri = URI.create(System.getProperty("fe.gateway.base.url", "http://localhost:8080") + path);
            HttpURLConnection c = (HttpURLConnection) uri.toURL().openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(5_000);
            c.setReadTimeout(60_000);
            if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
            c.setRequestProperty("Accept", "text/event-stream");
            if (body != null) {
                c.setRequestProperty("Content-Type", "application/json");
                c.setDoOutput(true);
                c.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            }
            ctx.lastStatus = c.getResponseCode();
            ctx.lastContentType = c.getContentType();
            try (InputStream in = ctx.lastStatus < 400 ? c.getInputStream() : c.getErrorStream()) {
                if (in == null) { ctx.lastBody = ""; return; }
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                }
                ctx.lastBody = sb.toString();
            }
            ctx.lastJson = null; // SSE bodies are not JSON
            c.disconnect();
        } catch (IOException e) {
            throw new RuntimeException(method + " SSE " + path + " failed: " + e.getMessage(), e);
        }
    }
}
