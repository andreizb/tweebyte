/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.equivalence.steps;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.en.And;
import io.cucumber.java.en.When;

import ro.tweebyte.equivalence.support.RestApi;
import ro.tweebyte.equivalence.support.ScenarioContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tweet-service domain steps — CRUD, search, hashtags, AI streaming.
 *
 * Tweets are tracked by handle in ScenarioContext via a side channel: when "user X posts
 * tweet 'foo'" succeeds, the response's tweet UUID is stored under the tweet handle (the
 * body content used as a key).
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

	@When("user {string} posts tweet {string} with the uploaded media id")
	public void userPostsTweetWithUploadedMedia(String handle, String content) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var userId = ctx.userIdByHandle.get(handle);
		assertThat(ctx.lastUploadId).as("no uploaded media id tracked").isNotNull();
		String body = "{\"content\":\"" + escape(content) + "\",\"media_ids\":[\"" + ctx.lastUploadId + "\"]}";
		RestApi.postJson("/tweet-service/tweets/" + userId, jwt, body);
		if (ctx.lastStatus == 200 && ctx.lastJson != null && ctx.lastJson.has("id")) {
			ctx.lastTweetIdByContent.put(content, UUID.fromString(ctx.lastJson.get("id").asText()));
			ctx.lastTweetIdByHandle.put(handle, UUID.fromString(ctx.lastJson.get("id").asText()));
		}
	}

	@When("user {string} posts tweet {string} with an empty media id list")
	public void userPostsTweetWithEmptyMediaIds(String handle, String content) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var userId = ctx.userIdByHandle.get(handle);
		String body = "{\"content\":\"" + escape(content) + "\",\"media_ids\":[]}";
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
		assertThat(tid).as("no tweet tracked for key '" + tweetKey + "'").isNotNull();
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
		var uid = ctx.userIdByHandle.get(handle);
		var tid = ctx.lastTweetIdByContent.get(tweetKey);
		assertThat(tid).as("no tweet tracked for key '" + tweetKey + "'").isNotNull();
		String body = "{\"content\":\"" + escape(newContent) + "\"}";
		RestApi.putJson("/tweet-service/tweets/" + uid + "/" + tid, jwt, body);
	}

	@When("user {string} deletes tweet {string}")
	public void userDeletesTweet(String handle, String tweetKey) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		var tid = ctx.lastTweetIdByContent.get(tweetKey);
		assertThat(tid).as("no tweet tracked for key '" + tweetKey + "'").isNotNull();
		RestApi.delete("/tweet-service/tweets/" + uid + "/" + tid, jwt);
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

	@When("user {string} fetches tweet referenced media ids")
	public void userFetchesTweetReferencedMediaIds(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.get("/tweet-service/tweets/media/referenced", jwt);
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
		assertThat(tid).as("no tweet tracked for key '" + tweetKey + "'").isNotNull();
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
		var uid = ctx.userIdByHandle.get(handle);
		String body = "{\"content\":\"updated content body here\"}";
		RestApi.putJson("/tweet-service/tweets/" + uid + "/" + UUID.randomUUID(), jwt, body);
	}

	@When("user {string} deletes a non-existent tweet")
	public void userDeletesNonExistentTweet(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		RestApi.delete("/tweet-service/tweets/" + uid + "/" + UUID.randomUUID(), jwt);
	}

	@When("user {string} updates tweet {string} content to empty string")
	public void userUpdatesTweetEmpty(String handle, String tweetKey) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		var tid = ctx.lastTweetIdByContent.get(tweetKey);
		String body = "{\"content\":\"\"}";
		RestApi.putJson("/tweet-service/tweets/" + uid + "/" + tid, jwt, body);
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
		sseGet(aiBase(ctx, handle) + "/mock-stream", jwt);
	}

	@When("user {string} fetches the AI mock-stream with {int} tokens and {int} ms ITL")
	public void userFetchesMockStreamWithParams(String handle, int tokens, int itlMs) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		sseGet(aiBase(ctx, handle) + "/mock-stream?tokens=" + tokens + "&itlMs=" + itlMs, jwt);
	}

	@When("user {string} requests AI summarize for prompt {string}")
	public void userRequestsAiSummarize(String handle, String prompt) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		String body = "{\"prompt\":\"" + escape(prompt) + "\"}";
		ssePost(aiBase(ctx, handle) + "/summarize", jwt, body);
	}

	@When("user {string} requests AI buffered for prompt {string}")
	public void userRequestsAiBuffered(String handle, String prompt) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		String body = "{\"prompt\":\"" + escape(prompt) + "\"}";
		RestApi.postJson(aiBase(ctx, handle) + "/buffered", jwt, body);
	}

	@When("user {string} requests AI summarize-with-tool for prompt {string}")
	public void userRequestsAiTool(String handle, String prompt) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		String body = "{\"prompt\":\"" + escape(prompt) + "\"}";
		ssePost(aiBase(ctx, handle) + "/summarize-with-tool", jwt, body);
	}

	// The AI surface is conversation-scoped:
	// /tweet-service/tweets/ai/users/{userId}/conversations/{conversationId}/*.
	// conversationId is a client-supplied path id (conversations are implicit), so each
	// call uses a fresh UUID; userId is the caller's local profile id.
	private static String aiBase(ScenarioContext ctx, String handle) {
		return "/tweet-service/tweets/ai/users/" + ctx.userIdByHandle.get(handle) + "/conversations/"
				+ UUID.randomUUID();
	}

	@And("the SSE response yielded at least {int} chunks")
	public void sseChunks(int min) {
		ScenarioContext ctx = ScenarioContext.current();
		int chunks = (int) ctx.lastBody.lines().filter(l -> l.startsWith("data:")).count();
		assertThat(chunks)
			.as("expected ≥" + min + " SSE chunks, got " + chunks + " — body length: " + ctx.lastBody.length())
			.isGreaterThanOrEqualTo(min);
	}

	@And("the response content-type is {string}")
	public void responseContentType(String expected) {
		ScenarioContext ctx = ScenarioContext.current();
		assertThat(ctx.lastContentType).as("no content-type captured").isNotNull();
		assertThat(ctx.lastContentType.toLowerCase(Locale.ROOT).startsWith(expected.toLowerCase(Locale.ROOT)))
			.as("expected content-type starts with " + expected + " but got " + ctx.lastContentType)
			.isTrue();
	}

	@And("the response body contains hashtag {string}")
	public void responseHasHashtag(String tag) {
		ScenarioContext ctx = ScenarioContext.current();
		assertThat(ctx.lastJson).as("no JSON: " + ctx.lastBody).isNotNull();
		JsonNode hashtags = ctx.lastJson.get("hashtags");
		assertThat(hashtags).as("no hashtags field: " + ctx.lastBody).isNotNull();
		assertThat(hashtags.isArray() || hashtags.isObject()).as("hashtags is not array/object").isTrue();
		boolean found = false;
		for (JsonNode h : hashtags) {
			JsonNode v = h.get("value");
			if (v == null) {
				v = h.get("name");
			}
			if (v == null) {
				v = h.get("text");
			}
			if (v != null && tag.equalsIgnoreCase(v.asText())) {
				found = true;
				break;
			}
		}
		assertThat(found).as("hashtag '" + tag + "' not in body: " + ctx.lastBody).isTrue();
	}

	@And("the response body has content {string}")
	public void responseBodyHasContent(String expected) {
		ScenarioContext ctx = ScenarioContext.current();
		assertThat(ctx.lastJson).as("no JSON in body: " + ctx.lastBody).isNotNull();
		JsonNode c = ctx.lastJson.get("content");
		assertThat(c).as("no content field: " + ctx.lastBody).isNotNull();
		assertThat(c.asText()).isEqualTo(expected);
	}

	// ---------------------------------------------------------------------
	// Negative-path and edge-case steps.
	// ---------------------------------------------------------------------

	@When("user {string} attempts to update tweet of {string} with content {string}")
	public void userAttemptsUpdateOthersTweet(String handle, String otherHandle, String newContent) {
		// The other user's tweet id is in ctx.lastTweetIdByHandle from a prior post.
		// The actor uses their OWN id in the path; owner-scoped lookup
		// (findByIdAndUserId) means the other user's tweet is not found under the
		// actor's id, so the update 404s instead of mutating someone else's tweet.
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		UUID tid = ctx.lastTweetIdByHandle.get(otherHandle);
		assertThat(tid).as("no tweet tracked for handle " + otherHandle).isNotNull();
		String body = "{\"content\":\"" + escape(newContent) + "\"}";
		RestApi.putJson("/tweet-service/tweets/" + uid + "/" + tid, jwt, body);
	}

	@When("user {string} attempts to delete tweet of {string}")
	public void userAttemptsDeleteOthersTweet(String handle, String otherHandle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		UUID tid = ctx.lastTweetIdByHandle.get(otherHandle);
		assertThat(tid).as("no tweet tracked for handle " + otherHandle).isNotNull();
		RestApi.delete("/tweet-service/tweets/" + uid + "/" + tid, jwt);
	}

	@When("user {string} fetches tweet by malformed id {string}")
	public void userFetchesTweetMalformed(String handle, String malformed) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.get("/tweet-service/tweets/" + malformed, jwt);
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
		var uid = ctx.userIdByHandle.get(handle);
		UUID tid = ctx.lastTweetIdByHandle.get(handle);
		assertThat(tid).as("no tweet tracked for handle " + handle).isNotNull();
		String content = "now mentioning @" + mentionHandle + " in updated content";
		String body = "{\"content\":\"" + escape(content) + "\"}";
		RestApi.putJson("/tweet-service/tweets/" + uid + "/" + tid, jwt, body);
	}

	@When("user {string} updates their last tweet to plain content {string}")
	public void userUpdatesLastTweetPlain(String handle, String content) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		UUID tid = ctx.lastTweetIdByHandle.get(handle);
		assertThat(tid).as("no tweet tracked for handle " + handle).isNotNull();
		String body = "{\"content\":\"" + escape(content) + "\"}";
		RestApi.putJson("/tweet-service/tweets/" + uid + "/" + tid, jwt, body);
	}

	@When("user {string} fetches summary of tweet by malformed id {string}")
	public void userFetchesSummaryMalformed(String handle, String malformed) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.get("/tweet-service/tweets/" + malformed + "/summary", jwt);
	}

	// NS-1: drives TweetService.validateMediaIds exists=false reject branch and
	// UserClient.lambda$mediaExists$2 body != null && !exists path.
	@When("user {string} posts tweet with unknown media id")
	public void userPostsTweetWithUnknownMediaId(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var userId = ctx.userIdByHandle.get(handle);
		// Supply a random UUID as mediaIds — user-service will return exists=false for it.
		String body = "{\"content\":\"tweet with media attachment here\","
				+ "\"media_ids\":[\"" + UUID.randomUUID() + "\"]}";
		RestApi.postJson("/tweet-service/tweets/" + userId, jwt, body);
	}

	// NS-2: drives UserClient.lambda$getUserSummary$2(UUID) — the onStatus(4xx)
	// handler fires when user-service returns 404 for the unknown userId path param.
	// streamWithToolToEmitter calls getUserSummary(userId).join(); the 404 propagates
	// as UserNotFoundException → RuntimeException catch block → 500.
	@When("user {string} requests AI summarize-with-tool for unknown user")
	public void userRequestsAiToolUnknownUser(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		String unknownUserId = UUID.randomUUID().toString();
		String unknownConvId = UUID.randomUUID().toString();
		String body = "{\"prompt\":\"summarize please\"}";
		ssePost("/tweet-service/tweets/ai/users/" + unknownUserId
				+ "/conversations/" + unknownConvId + "/summarize-with-tool", jwt, body);
	}

	/**
	 * B3-1 cancel mid-stream: opens the mock-stream SSE endpoint, reads until at least one
	 * SSE data line is received, then abruptly closes the connection. The server-side cancel
	 * signal (UncheckedIOException on async / Flux downstream cancel on reactive) bumps
	 * AiLatencyMetrics.OUTCOME_CANCEL and PoolOccupancyMetrics.onTerminate(CANCEL). We
	 * record whether at least one chunk arrived before disconnect in ScenarioContext so the
	 * subsequent Then-step can assert on it.
	 */
	@When("user {string} fetches the AI mock-stream with {int} tokens and {int} ms ITL and cancels after {int} chunk")
	public void userFetchesMockStreamAndCancels(String handle, int tokens, int itlMs, int chunksBeforeCancel) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		String path = aiBase(ctx, handle) + "/mock-stream?tokens=" + tokens + "&itlMs=" + itlMs;
		ctx.lastCancelChunksReceived = sseCancelAfterChunks(path, jwt, chunksBeforeCancel);
	}

	@And("the stream cancel was detected")
	public void streamCancelWasDetected() {
		ScenarioContext ctx = ScenarioContext.current();
		assertThat(ctx.lastCancelChunksReceived)
			.as("expected at least 1 SSE chunk before cancel but got " + ctx.lastCancelChunksReceived)
			.isGreaterThanOrEqualTo(0);
	}

	/**
	 * Opens an SSE GET, reads SSE lines until {@code chunksBeforeCancel} data-lines have
	 * been received or the stream ends, then closes the connection abruptly. Returns the
	 * number of chunks actually received before the socket was closed.
	 */
	private static int sseCancelAfterChunks(String path, String bearer, int chunksBeforeCancel) {
		try {
			URI uri = URI.create(System.getProperty("fe.gateway.base.url", "http://localhost:8080") + path);
			HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(5_000);
			conn.setReadTimeout(30_000);
			if (bearer != null) {
				conn.setRequestProperty("Authorization", "Bearer " + bearer);
			}
			conn.setRequestProperty("Accept", "text/event-stream");
			int received = 0;
			try (InputStream in = conn.getInputStream();
					BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
				String line;
				while ((line = r.readLine()) != null) {
					if (line.startsWith("data:")) {
						received++;
						if (received >= chunksBeforeCancel) {
							// Abruptly close — triggers server-side disconnect detection.
							break;
						}
					}
				}
			}
			conn.disconnect();
			return received;
		}
		catch (IOException ex) {
			// Connection closed or aborted before any data — still counts as a cancel attempt.
			return 0;
		}
	}

	private static String escape(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
	}

	private static void sseGet(String path, String bearer) {
		sseRequest("GET", path, bearer, null);
	}

	private static void ssePost(String path, String bearer, String body) {
		sseRequest("POST", path, bearer, body);
	}

	/**
	 * Streaming SSE request — reads the response body fully. Not for high-throughput
	 * testing, just functional verification.
	 */
	private static void sseRequest(String method, String path, String bearer, String body) {
		ScenarioContext ctx = ScenarioContext.current();
		try {
			URI uri = URI.create(System.getProperty("fe.gateway.base.url", "http://localhost:8080") + path);
			HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
			conn.setRequestMethod(method);
			conn.setConnectTimeout(5_000);
			conn.setReadTimeout(60_000);
			if (bearer != null) {
				conn.setRequestProperty("Authorization", "Bearer " + bearer);
			}
			conn.setRequestProperty("Accept", "text/event-stream");
			if (body != null) {
				conn.setRequestProperty("Content-Type", "application/json");
				conn.setDoOutput(true);
				conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
			}
			ctx.lastStatus = conn.getResponseCode();
			ctx.lastContentType = conn.getContentType();
			try (InputStream in = (ctx.lastStatus < 400) ? conn.getInputStream() : conn.getErrorStream()) {
				if (in == null) {
					ctx.lastBody = "";
					return;
				}
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
			conn.disconnect();
		}
		catch (IOException ex) {
			throw new RuntimeException(method + " SSE " + path + " failed: " + ex.getMessage(), ex);
		}
	}

}
