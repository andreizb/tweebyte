/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.equivalence.steps;

import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.en.And;
import io.cucumber.java.en.When;

import ro.tweebyte.equivalence.support.RestApi;
import ro.tweebyte.equivalence.support.ScenarioContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Interaction-service domain steps — likes, retweets, replies, follows, recommendations.
 */
public class InteractionSteps {

	@When("user {string} follows {string}")
	public void userFollows(String follower, String followed) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(follower);
		var fid = ctx.userIdByHandle.get(follower);
		var tid = ctx.userIdByHandle.get(followed);
		RestApi.postJson("/interaction-service/follows/" + fid + "/" + tid, jwt, "{}");
	}

	@When("user {string} unfollows {string}")
	public void userUnfollows(String follower, String followed) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(follower);
		var fid = ctx.userIdByHandle.get(follower);
		var tid = ctx.userIdByHandle.get(followed);
		RestApi.delete("/interaction-service/follows/" + fid + "/" + tid, jwt);
	}

	@When("user {string} reads followers count of {string}")
	public void userReadsFollowersCount(String requester, String target) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(requester);
		var tid = ctx.userIdByHandle.get(target);
		RestApi.get("/interaction-service/follows/" + tid + "/followers/count", jwt);
	}

	@When("user {string} reads following count of {string}")
	public void userReadsFollowingCount(String requester, String target) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(requester);
		var tid = ctx.userIdByHandle.get(target);
		RestApi.get("/interaction-service/follows/" + tid + "/following/count", jwt);
	}

	@When("user {string} reads combined follow counts of {string}")
	public void userReadsCombinedFollowCounts(String requester, String target) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(requester);
		var tid = ctx.userIdByHandle.get(target);
		RestApi.get("/interaction-service/follows/" + tid + "/counts", jwt);
	}

	@When("user {string} likes tweet {string}")
	public void userLikesTweet(String handle, String tweetKey) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		var tid = ctx.lastTweetIdByContent.get(tweetKey);
		assertThat(tid).as("no tweet tracked: " + tweetKey).isNotNull();
		String path = "/interaction-service/likes/" + uid + "/tweets/" + tid;
		try {
			RestApi.postJson(path, jwt, "{}");
		}
		catch (RuntimeException ex) {
			if (!String.valueOf(ex.getMessage()).contains("Read timed out")) {
				throw ex;
			}
			RestApi.postJson(path, jwt, "{}");
		}
	}

	@When("user {string} unlikes tweet {string}")
	public void userUnlikesTweet(String handle, String tweetKey) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		var tid = ctx.lastTweetIdByContent.get(tweetKey);
		RestApi.delete("/interaction-service/likes/" + uid + "/tweets/" + tid, jwt);
	}

	@When("user {string} reads like count of tweet {string}")
	public void userReadsLikeCount(String handle, String tweetKey) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var tid = ctx.lastTweetIdByContent.get(tweetKey);
		RestApi.get("/interaction-service/likes/" + tid + "/count", jwt);
	}

	@When("user {string} retweets tweet {string}")
	public void userRetweets(String handle, String tweetKey) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		var tid = ctx.lastTweetIdByContent.get(tweetKey);
		// RetweetCreateRequest declares @JsonProperty("original_tweet_id"), not
		// "tweetId".
		String body = "{\"original_tweet_id\":\"" + tid + "\"}";
		RestApi.postJson("/interaction-service/retweets/" + uid, jwt, body);
	}

	@When("user {string} reads retweet count of tweet {string}")
	public void userReadsRetweetCount(String handle, String tweetKey) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var tid = ctx.lastTweetIdByContent.get(tweetKey);
		RestApi.get("/interaction-service/retweets/tweet/" + tid + "/count", jwt);
	}

	@When("user {string} replies to tweet {string} with {string}")
	public void userReplies(String handle, String tweetKey, String content) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		var tid = ctx.lastTweetIdByContent.get(tweetKey);
		// ReplyCreateRequest declares @JsonProperty("tweet_id"), not "tweetId".
		String body = "{\"tweet_id\":\"" + tid + "\",\"content\":\"" + content.replace("\"", "\\\"") + "\"}";
		RestApi.postJson("/interaction-service/replies/" + uid, jwt, body);
	}

	@When("user {string} lists replies for tweet {string}")
	public void userListsReplies(String handle, String tweetKey) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var tid = ctx.lastTweetIdByContent.get(tweetKey);
		RestApi.get("/interaction-service/replies/tweet/" + tid, jwt);
	}

	// Reply listing enriches each row via an inter-service getUserSummary call, which can lag
	// under suite load and momentarily under-report the count. Poll until the expected number
	// materialises (bounded) so the gate is deterministic.
	@When("user {string} lists replies for tweet {string} expecting {int}")
	public void userListsRepliesExpecting(String handle, String tweetKey, int expected) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var tid = ctx.lastTweetIdByContent.get(tweetKey);
		for (int attempt = 0; attempt < 30; attempt++) {
			RestApi.get("/interaction-service/replies/tweet/" + tid, jwt);
			if (ctx.lastJson != null && ctx.lastJson.isArray() && ctx.lastJson.size() == expected) {
				return;
			}
			try {
				Thread.sleep(500);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return;
			}
		}
		throw new AssertionError("timed out waiting for the reply list to reach size " + expected
				+ " (last size=" + ((ctx.lastJson != null && ctx.lastJson.isArray()) ? ctx.lastJson.size() : "n/a") + ")");
	}

	@When("user {string} reads reply count of tweet {string}")
	public void userReadsReplyCount(String handle, String tweetKey) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var tid = ctx.lastTweetIdByContent.get(tweetKey);
		RestApi.get("/interaction-service/replies/tweet/" + tid + "/count", jwt);
	}

	@When("user {string} fetches follow recommendations")
	public void userFetchesRecommendations(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		RestApi.get("/interaction-service/recommendations/" + uid + "/follow", jwt);
	}

	@When("user {string} fetches hashtag recommendations")
	public void userFetchesHashtagRecommendations(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.get("/interaction-service/recommendations/hashtags", jwt);
	}

	@When("user {string} lists likes by user")
	public void userListsLikesByUser(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		RestApi.get("/interaction-service/likes/user/" + uid, jwt);
	}

	@When("user {string} lists likes for tweet {string}")
	public void userListsLikesForTweet(String handle, String tweetKey) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var tid = ctx.lastTweetIdByContent.get(tweetKey);
		RestApi.get("/interaction-service/likes/tweet/" + tid, jwt);
	}

	@When("user {string} lists retweets by user")
	public void userListsRetweetsByUser(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		RestApi.get("/interaction-service/retweets/user/" + uid, jwt);
	}

	@When("user {string} lists retweets for tweet {string}")
	public void userListsRetweetsForTweet(String handle, String tweetKey) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var tid = ctx.lastTweetIdByContent.get(tweetKey);
		RestApi.get("/interaction-service/retweets/tweet/" + tid, jwt);
	}

	@When("user {string} fetches top reply for tweet {string}")
	public void userFetchesTopReply(String handle, String tweetKey) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var tid = ctx.lastTweetIdByContent.get(tweetKey);
		RestApi.get("/interaction-service/replies/tweet/" + tid + "/top", jwt);
	}

	@When("user {string} fetches batch like counts for tweet {string}")
	public void userFetchesBatchLikeCounts(String handle, String tweetKey) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.postJson("/interaction-service/likes/counts", jwt, tweetIdArray(ctx, tweetKey));
	}

	@When("user {string} fetches batch retweet counts for tweet {string}")
	public void userFetchesBatchRetweetCounts(String handle, String tweetKey) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.postJson("/interaction-service/retweets/tweet/counts", jwt, tweetIdArray(ctx, tweetKey));
	}

	@When("user {string} fetches batch reply counts for tweet {string}")
	public void userFetchesBatchReplyCounts(String handle, String tweetKey) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.postJson("/interaction-service/replies/tweet/counts", jwt, tweetIdArray(ctx, tweetKey));
	}

	@When("user {string} fetches batch top replies for tweet {string}")
	public void userFetchesBatchTopReplies(String handle, String tweetKey) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.postJson("/interaction-service/replies/tweet/top", jwt, tweetIdArray(ctx, tweetKey));
	}

	@When("user {string} fetches interaction referenced media ids")
	public void userFetchesInteractionReferencedMediaIds(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.get("/interaction-service/media/referenced", jwt);
	}

	@When("user {string} updates their last reply with content {string}")
	public void userUpdatesLastReply(String handle, String content) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		// The id of the reply was put into ctx.lastJson["id"] by the create-reply call
		UUID replyId = (ctx.lastJson != null && ctx.lastJson.has("id"))
				? UUID.fromString(ctx.lastJson.get("id").asText()) : null;
		assertThat(replyId).as("no reply tracked in last response: " + ctx.lastBody).isNotNull();
		String body = "{\"user_id\":\"" + uid + "\",\"content\":\"" + content.replace("\"", "\\\"") + "\"}";
		RestApi.putJson("/interaction-service/replies/" + uid + "/" + replyId, jwt, body);
	}

	@When("user {string} deletes their last reply")
	public void userDeletesLastReply(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		UUID replyId = (ctx.lastJson != null && ctx.lastJson.has("id"))
				? UUID.fromString(ctx.lastJson.get("id").asText()) : null;
		assertThat(replyId).as("no reply tracked in last response: " + ctx.lastBody).isNotNull();
		RestApi.delete("/interaction-service/replies/" + uid + "/" + replyId, jwt);
	}

	@When("user {string} updates their last retweet with content {string}")
	public void userUpdatesLastRetweet(String handle, String content) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		UUID rtId = (ctx.lastJson != null && ctx.lastJson.has("id")) ? UUID.fromString(ctx.lastJson.get("id").asText())
				: null;
		assertThat(rtId).as("no retweet tracked in last response: " + ctx.lastBody).isNotNull();
		String body = "{\"content\":\"" + content.replace("\"", "\\\"") + "\"}";
		RestApi.putJson("/interaction-service/retweets/" + uid + "/" + rtId, jwt, body);
	}

	@When("user {string} deletes their last retweet")
	public void userDeletesLastRetweet(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		UUID rtId = (ctx.lastJson != null && ctx.lastJson.has("id")) ? UUID.fromString(ctx.lastJson.get("id").asText())
				: null;
		assertThat(rtId).as("no retweet tracked in last response: " + ctx.lastBody).isNotNull();
		RestApi.delete("/interaction-service/retweets/" + uid + "/" + rtId, jwt);
	}

	@When("user {string} likes their last reply")
	public void userLikesLastReply(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		UUID replyId = (ctx.lastJson != null && ctx.lastJson.has("id"))
				? UUID.fromString(ctx.lastJson.get("id").asText()) : null;
		assertThat(replyId).as("no reply tracked in last response: " + ctx.lastBody).isNotNull();
		RestApi.postJson("/interaction-service/likes/" + uid + "/replies/" + replyId, jwt, "{}");
	}

	@When("user {string} unlikes their last reply")
	public void userUnlikesLastReply(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		UUID replyId = (ctx.lastJson != null && ctx.lastJson.has("id"))
				? UUID.fromString(ctx.lastJson.get("id").asText()) : null;
		assertThat(replyId).as("no reply tracked in last response: " + ctx.lastBody).isNotNull();
		RestApi.delete("/interaction-service/likes/" + uid + "/replies/" + replyId, jwt);
	}

	@When("user {string} lists followers of {string}")
	public void userListsFollowersOf(String handle, String target) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var tid = ctx.userIdByHandle.get(target);
		RestApi.get("/interaction-service/follows/" + tid + "/followers", jwt);
	}

	@When("user {string} lists following of {string}")
	public void userListsFollowingOf(String handle, String target) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var tid = ctx.userIdByHandle.get(target);
		RestApi.get("/interaction-service/follows/" + tid + "/following", jwt);
	}

	@When("user {string} lists follower identifiers of {string}")
	public void userListsFollowerIdentifiersOf(String handle, String target) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var tid = ctx.userIdByHandle.get(target);
		RestApi.get("/interaction-service/follows/" + tid + "/followers/identifiers", jwt);
	}

	@When("user {string} lists their follow requests")
	public void userListsTheirFollowRequests(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		RestApi.get("/interaction-service/follows/" + uid + "/requests", jwt);
	}

	@When("user {string} updates follow request from {string} to {string}")
	public void userUpdatesFollowRequest(String handle, String requester, String status) {
		// PUT /follows/{userId}/{followRequestId}/{status} — userId is the recipient,
		// followRequestId is the FollowDto id; we don't track that, but the controller
		// accepts arbitrary UUIDs and returns 404 when not found, exercising the path.
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		UUID fid = UUID.randomUUID();
		RestApi.putJson("/interaction-service/follows/" + uid + "/" + fid + "/" + status, jwt, "");
	}

	/**
	 * After "user X lists their follow requests", parse the first FollowDto.id out of the
	 * response body so we can hand it to PUT/{status}.
	 */
	@When("user {string} accepts the first pending follow request")
	public void userAcceptsFirstPending(String handle) {
		acceptOrRejectFirst(handle, "ACCEPTED");
	}

	@When("user {string} rejects the first pending follow request")
	public void userRejectsFirstPending(String handle) {
		acceptOrRejectFirst(handle, "REJECTED");
	}

	private void acceptOrRejectFirst(String handle, String status) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		// First, list pending requests; pick the first id.
		RestApi.get("/interaction-service/follows/" + uid + "/requests", jwt);
		UUID followRequestId = null;
		if (ctx.lastJson != null && ctx.lastJson.isArray() && ctx.lastJson.size() > 0) {
			JsonNode first = ctx.lastJson.get(0);
			if (first.has("id") && !first.get("id").isNull()) {
				followRequestId = UUID.fromString(first.get("id").asText());
			}
		}
		assertThat(followRequestId).as("no pending follow request found in: " + ctx.lastBody).isNotNull();
		RestApi.putJson("/interaction-service/follows/" + uid + "/" + followRequestId + "/" + status, jwt, "");
	}

	@When("user {string} likes a non-existent tweet")
	public void userLikesNonExistentTweet(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		RestApi.postJson("/interaction-service/likes/" + uid + "/tweets/" + UUID.randomUUID(), jwt, "{}");
	}

	@When("user {string} reads like count for non-existent tweet")
	public void userReadsLikeCountNonExistent(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.get("/interaction-service/likes/" + UUID.randomUUID() + "/count", jwt);
	}

	@When("user {string} likes a non-existent reply")
	public void userLikesNonExistentReply(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		RestApi.postJson("/interaction-service/likes/" + uid + "/replies/" + UUID.randomUUID(), jwt, "{}");
	}

	@When("user {string} retweets a non-existent tweet")
	public void userRetweetsNonExistentTweet(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		String body = "{\"original_tweet_id\":\"" + UUID.randomUUID() + "\"}";
		RestApi.postJson("/interaction-service/retweets/" + uid, jwt, body);
	}

	@When("user {string} replies to a non-existent tweet with {string}")
	public void userRepliesNonExistentTweet(String handle, String content) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		String body = "{\"tweet_id\":\"" + UUID.randomUUID() + "\",\"content\":\"" + content.replace("\"", "\\\"")
				+ "\"}";
		RestApi.postJson("/interaction-service/replies/" + uid, jwt, body);
	}

	// For unauthorized update/delete scenarios: the *other* user's reply id is
	// already in ctx.lastJson["id"] from the create-reply call. The acting user
	// (handle) attempts the mutation, which both stacks reject.
	@When("user {string} tries to delete user {string} last reply")
	public void userTriesToDeleteOthersReply(String handle, String otherHandle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		UUID replyId = (ctx.lastJson != null && ctx.lastJson.has("id"))
				? UUID.fromString(ctx.lastJson.get("id").asText()) : null;
		assertThat(replyId).as("no reply tracked: " + ctx.lastBody).isNotNull();
		RestApi.delete("/interaction-service/replies/" + uid + "/" + replyId, jwt);
	}

	@When("user {string} tries to update user {string} last reply with content {string}")
	public void userTriesToUpdateOthersReply(String handle, String otherHandle, String content) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		UUID replyId = (ctx.lastJson != null && ctx.lastJson.has("id"))
				? UUID.fromString(ctx.lastJson.get("id").asText()) : null;
		assertThat(replyId).as("no reply tracked: " + ctx.lastBody).isNotNull();
		String body = "{\"user_id\":\"" + uid + "\",\"content\":\"" + content.replace("\"", "\\\"") + "\"}";
		RestApi.putJson("/interaction-service/replies/" + uid + "/" + replyId, jwt, body);
	}

	@And("the response body is the number {int}")
	public void responseIsNumber(int expected) {
		ScenarioContext ctx = ScenarioContext.current();
		// Counts come back as a JSON number — the body is just "0" or "1" etc.
		try {
			int actual = Integer.parseInt(ctx.lastBody.trim());
			assertThat(actual).isEqualTo(expected);
		}
		catch (NumberFormatException ex) {
			fail("expected numeric body but got: " + ctx.lastBody);
		}
	}

	// ---------------------------------------------------------------------
	// Negative-path and edge-case steps.
	// ---------------------------------------------------------------------

	@When("user {string} attempts to follow themselves")
	public void userFollowsSelf(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		RestApi.postJson("/interaction-service/follows/" + uid + "/" + uid, jwt, "{}");
	}

	@When("user {string} attempts to follow a non-existent user")
	public void userFollowsNonExistent(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		RestApi.postJson("/interaction-service/follows/" + uid + "/" + UUID.randomUUID(), jwt, "{}");
	}

	@When("user {string} attempts to unfollow a non-existent user")
	public void userUnfollowsNonExistent(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		RestApi.delete("/interaction-service/follows/" + uid + "/" + UUID.randomUUID(), jwt);
	}

	@When("user {string} updates follow request to PENDING")
	public void userUpdatesFollowToPending(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		// First list pending requests; pick first id.
		RestApi.get("/interaction-service/follows/" + uid + "/requests", jwt);
		UUID frId = null;
		if (ctx.lastJson != null && ctx.lastJson.isArray() && ctx.lastJson.size() > 0) {
			JsonNode first = ctx.lastJson.get(0);
			if (first.has("id") && !first.get("id").isNull()) {
				frId = UUID.fromString(first.get("id").asText());
			}
		}
		assertThat(frId).as("no pending follow request: " + ctx.lastBody).isNotNull();
		RestApi.putJson("/interaction-service/follows/" + uid + "/" + frId + "/PENDING", jwt, "");
	}

	@When("user {string} self-accepts their pending follow request to {string}")
	public void userSelfAcceptsOwnRequest(String handle, String target) {
		// Drives FollowService.updateFollowRequest's `followerId.equals(userId) && status
		// == ACCEPTED` branch.
		ScenarioContext ctx = ScenarioContext.current();
		var targetJwt = ctx.jwtByHandle.get(target);
		var targetId = ctx.userIdByHandle.get(target);
		RestApi.get("/interaction-service/follows/" + targetId + "/requests", targetJwt);
		UUID frId = firstIdFromLastArray(ctx, "no pending follow request for " + target + ": " + ctx.lastBody);
		var followerJwt = ctx.jwtByHandle.get(handle);
		var followerId = ctx.userIdByHandle.get(handle);
		RestApi.putJson("/interaction-service/follows/" + followerId + "/" + frId + "/ACCEPTED", followerJwt, "");
	}

	@When("user {string} reads followers count of a non-existent user")
	public void userReadsFollowersCountNonExistent(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.get("/interaction-service/follows/" + UUID.randomUUID() + "/followers/count", jwt);
	}

	@When("user {string} reads following count of a non-existent user")
	public void userReadsFollowingCountNonExistent(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.get("/interaction-service/follows/" + UUID.randomUUID() + "/following/count", jwt);
	}

	@When("user {string} lists followers of a non-existent user")
	public void userListsFollowersNonExistent(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.get("/interaction-service/follows/" + UUID.randomUUID() + "/followers", jwt);
	}

	@When("user {string} lists following of a non-existent user")
	public void userListsFollowingNonExistent(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.get("/interaction-service/follows/" + UUID.randomUUID() + "/following", jwt);
	}

	@When("user {string} reads retweet count for non-existent tweet")
	public void userReadsRetweetCountNonExistent(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.get("/interaction-service/retweets/tweet/" + UUID.randomUUID() + "/count", jwt);
	}

	@When("user {string} reads reply count for non-existent tweet")
	public void userReadsReplyCountNonExistent(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.get("/interaction-service/replies/tweet/" + UUID.randomUUID() + "/count", jwt);
	}

	@When("user {string} fetches top reply for a non-existent tweet")
	public void userFetchesTopReplyNonExistent(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.get("/interaction-service/replies/tweet/" + UUID.randomUUID() + "/top", jwt);
	}

	@When("user {string} lists replies for a non-existent tweet")
	public void userListsRepliesNonExistent(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.get("/interaction-service/replies/tweet/" + UUID.randomUUID(), jwt);
	}

	@When("user {string} lists retweets for a non-existent tweet")
	public void userListsRetweetsNonExistent(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.get("/interaction-service/retweets/tweet/" + UUID.randomUUID(), jwt);
	}

	@When("user {string} lists likes for a non-existent tweet")
	public void userListsLikesNonExistent(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		RestApi.get("/interaction-service/likes/tweet/" + UUID.randomUUID(), jwt);
	}

	@When("user {string} unlikes a non-existent tweet")
	public void userUnlikesNonExistent(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		RestApi.delete("/interaction-service/likes/" + uid + "/tweets/" + UUID.randomUUID(), jwt);
	}

	@When("user {string} unlikes a non-existent reply")
	public void userUnlikesNonExistentReply(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		RestApi.delete("/interaction-service/likes/" + uid + "/replies/" + UUID.randomUUID(), jwt);
	}

	@When("user {string} retweets tweet {string} with content {string}")
	public void userRetweetsWithContent(String handle, String tweetKey, String content) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		var tid = ctx.lastTweetIdByContent.get(tweetKey);
		String body = "{\"original_tweet_id\":\"" + tid + "\",\"content\":\"" + content.replace("\"", "\\\"") + "\"}";
		RestApi.postJson("/interaction-service/retweets/" + uid, jwt, body);
	}

	@When("user {string} replies with empty content to tweet {string}")
	public void userRepliesEmpty(String handle, String tweetKey) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		var tid = ctx.lastTweetIdByContent.get(tweetKey);
		String body = "{\"tweet_id\":\"" + tid + "\",\"content\":\"\"}";
		RestApi.postJson("/interaction-service/replies/" + uid, jwt, body);
	}

	// -------------------------------------------------------------------------
	// B1 coverage scenarios — interaction_coverage.feature
	// -------------------------------------------------------------------------

	// RT-M1: retweets tweet with a known (uploaded) media id.
	// Uploads a 1×1 JPEG to user-service first, captures the returned id, then
	// POSTs the retweet with media_ids set to that id.
	@When("user {string} retweets tweet {string} with a known media id")
	public void userRetweetsWithKnownMediaId(String handle, String tweetKey) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		var tid = ctx.lastTweetIdByContent.get(tweetKey);
		assertThat(tid).as("no tweet tracked: " + tweetKey).isNotNull();
		UUID mediaId = uploadTinyImage(jwt);
		String body = "{\"original_tweet_id\":\"" + tid + "\",\"media_ids\":[\"" + mediaId + "\"]}";
		RestApi.postJson("/interaction-service/retweets/" + uid, jwt, body);
	}

	// RT-M2: retweets tweet with a bogus (unknown) media id → expects 400.
	@When("user {string} retweets tweet {string} with a bogus media id")
	public void userRetweetsWithBogusMediaId(String handle, String tweetKey) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		var tid = ctx.lastTweetIdByContent.get(tweetKey);
		assertThat(tid).as("no tweet tracked: " + tweetKey).isNotNull();
		String body = "{\"original_tweet_id\":\"" + tid + "\",\"media_ids\":[\"" + UUID.randomUUID() + "\"]}";
		RestApi.postJson("/interaction-service/retweets/" + uid, jwt, body);
	}

	// RT-U1: a third party tries to update another user's retweet → expects 403.
	// The last retweet id is taken from ctx.lastJson["id"] written by the preceding
	// "user X retweets tweet Y" step.
	@When("user {string} tries to update user {string} last retweet with content {string}")
	public void userTriesToUpdateOthersRetweet(String handle, String otherHandle, String content) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		UUID rtId = (ctx.lastJson != null && ctx.lastJson.has("id"))
				? UUID.fromString(ctx.lastJson.get("id").asText()) : null;
		assertThat(rtId).as("no retweet tracked in last response: " + ctx.lastBody).isNotNull();
		String body = "{\"content\":\"" + content.replace("\"", "\\\"") + "\"}";
		RestApi.putJson("/interaction-service/retweets/" + uid + "/" + rtId, jwt, body);
	}

	// RT-D1: a third party tries to delete another user's retweet → expects 403.
	@When("user {string} tries to delete user {string} last retweet")
	public void userTriesToDeleteOthersRetweet(String handle, String otherHandle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		UUID rtId = (ctx.lastJson != null && ctx.lastJson.has("id"))
				? UUID.fromString(ctx.lastJson.get("id").asText()) : null;
		assertThat(rtId).as("no retweet tracked in last response: " + ctx.lastBody).isNotNull();
		RestApi.delete("/interaction-service/retweets/" + uid + "/" + rtId, jwt);
	}

	// RP-M1: replies to tweet with a known (uploaded) media id.
	@When("user {string} replies to tweet {string} with a known media id")
	public void userRepliesWithKnownMediaId(String handle, String tweetKey) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		var tid = ctx.lastTweetIdByContent.get(tweetKey);
		assertThat(tid).as("no tweet tracked: " + tweetKey).isNotNull();
		UUID mediaId = uploadTinyImage(jwt);
		String body = "{\"tweet_id\":\"" + tid + "\",\"content\":\"reply with media attachment\","
				+ "\"media_ids\":[\"" + mediaId + "\"]}";
		RestApi.postJson("/interaction-service/replies/" + uid, jwt, body);
	}

	// RP-M2: replies to tweet with a bogus media id → expects 400.
	@When("user {string} replies to tweet {string} with a bogus media id")
	public void userRepliesWithBogusMediaId(String handle, String tweetKey) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		var tid = ctx.lastTweetIdByContent.get(tweetKey);
		assertThat(tid).as("no tweet tracked: " + tweetKey).isNotNull();
		String body = "{\"tweet_id\":\"" + tid + "\",\"content\":\"media-reply\","
				+ "\"media_ids\":[\"" + UUID.randomUUID() + "\"]}";
		RestApi.postJson("/interaction-service/replies/" + uid, jwt, body);
	}

	// RPM-C1: replies to tweet omitting "content" entirely — null in ReplyCreateRequest.
	// Drives ReplyMapper.mapRequestToEntity content != null false arm.
	@When("user {string} replies to tweet {string} without content")
	public void userRepliesWithoutContent(String handle, String tweetKey) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		var tid = ctx.lastTweetIdByContent.get(tweetKey);
		assertThat(tid).as("no tweet tracked: " + tweetKey).isNotNull();
		// Omit "content" field entirely — null in ReplyCreateRequest.
		String body = "{\"tweet_id\":\"" + tid + "\"}";
		RestApi.postJson("/interaction-service/replies/" + uid, jwt, body);
	}

	// TIA-1 / CC-M1 / CC-P1 / TIS-1: profile-interactions aggregate for all tweet ids
	// posted by this user. Builds tweetIds query param from ctx.lastTweetIdByContent.
	@When("user {string} fetches profile interactions for their own tweets")
	public void userFetchesProfileInteractions(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		String ids = ctx.lastTweetIdByContent.values().stream()
				.map(id -> "\"" + id + "\"")
				.collect(Collectors.joining(",", "[", "]"));
		RestApi.postJson("/interaction-service/follows/" + uid + "/profile-interactions", jwt, ids);
	}

	// CC-M2 / TRC-E1: profile-interactions with an empty tweet list — exercises the
	// ids.isEmpty() true arm in CountCache.getAllMulti and TopReplyCache.getAll.
	@When("user {string} fetches profile interactions with no tweets")
	public void userFetchesProfileInteractionsNoTweets(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		RestApi.postJson("/interaction-service/follows/" + uid + "/profile-interactions", jwt, "[]");
	}

	private static UUID firstIdFromLastArray(ScenarioContext ctx, String assertionMessage) {
		UUID id = null;
		if (ctx.lastJson != null && ctx.lastJson.isArray() && ctx.lastJson.size() > 0) {
			JsonNode first = ctx.lastJson.get(0);
			if (first.has("id") && !first.get("id").isNull()) {
				id = UUID.fromString(first.get("id").asText());
			}
		}
		assertThat(id).as(assertionMessage).isNotNull();
		return id;
	}

	private static String tweetIdArray(ScenarioContext ctx, String tweetKey) {
		UUID tweetId = ctx.lastTweetIdByContent.get(tweetKey);
		assertThat(tweetId).as("no tweet tracked: " + tweetKey).isNotNull();
		return "[\"" + tweetId + "\",\"" + UUID.randomUUID() + "\"]";
	}

	// RMU-1: updates the last retweet omitting "content" — null in RetweetUpdateRequest.
	// Drives RetweetMapper.mapRequestToEntity content != null false arm.
	@When("user {string} updates their last retweet without content")
	public void userUpdatesLastRetweetWithoutContent(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		UUID rtId = (ctx.lastJson != null && ctx.lastJson.has("id"))
				? UUID.fromString(ctx.lastJson.get("id").asText()) : null;
		assertThat(rtId).as("no retweet tracked in last response: " + ctx.lastBody).isNotNull();
		// Omit "content" field — null in DTO triggers the null-guard in mapper.
		RestApi.putJson("/interaction-service/retweets/" + uid + "/" + rtId, jwt, "{}");
	}

	// RMU-2: updates the last retweet with a known (uploaded) media id.
	// Drives RetweetMapper.mapRequestToEntity mediaIds != null true arm.
	@When("user {string} updates their last retweet with a known media id")
	public void userUpdatesLastRetweetWithKnownMediaId(String handle) {
		ScenarioContext ctx = ScenarioContext.current();
		var jwt = ctx.jwtByHandle.get(handle);
		var uid = ctx.userIdByHandle.get(handle);
		UUID rtId = (ctx.lastJson != null && ctx.lastJson.has("id"))
				? UUID.fromString(ctx.lastJson.get("id").asText()) : null;
		assertThat(rtId).as("no retweet tracked in last response: " + ctx.lastBody).isNotNull();
		UUID mediaId = uploadTinyImage(jwt);
		String body = "{\"content\":\"updated with media\",\"media_ids\":[\"" + mediaId + "\"]}";
		RestApi.putJson("/interaction-service/retweets/" + uid + "/" + rtId, jwt, body);
	}

	// -------------------------------------------------------------------------
	// Private helpers
	// -------------------------------------------------------------------------

	/**
	 * Uploads a minimal 1×1 white JPEG to the user-service media endpoint and returns the
	 * assigned media id. Used by steps that need a "known" (existing) media id to exercise
	 * the mediaExists=true branch in RetweetService/ReplyService.validateMediaIds.
	 *
	 * The upload uses the same postMultipartFile helper as UserSteps but is inlined here
	 * so InteractionSteps stays self-contained. The JPEG bytes are stored as a Base64
	 * string to avoid Java's lossy-conversion-from-int compile error with byte literals
	 * whose values exceed 0x7F.
	 */
	private static UUID uploadTinyImage(String jwt) {
		// Minimal valid JPEG (1×1 white pixel), Base64-encoded to avoid byte-literal
		// sign-extension compile errors. Decoded at call time.
		String tinyJpegB64 =
			"/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8U"
			+ "HRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAARC"
			+ "AABAAEDAQIRAF8ABAMBAAIRAAD/xAAUAAEAAAAAAAAAAAAAAAAAAAAI/8QAF"
			+ "BABAAAAAAAAAAAAAAAAAAAAAP/EABQBAQAAAAAAAAAAAAAAAAAAAAD/xAAUE"
			+ "QEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwCwABmX/9k=";
		byte[] tinyJpeg = java.util.Base64.getDecoder().decode(tinyJpegB64);
		RestApi.postMultipartFile("/user-service/media", jwt, "file", "tiny.jpg", "image/jpeg", tinyJpeg);
		ScenarioContext ctx = ScenarioContext.current();
		assertThat(ctx.lastStatus).as("media upload should succeed but got " + ctx.lastStatus + ": " + ctx.lastBody)
				.isEqualTo(200);
		assertThat(ctx.lastJson).as("no JSON in media upload response: " + ctx.lastBody).isNotNull();
		assertThat(ctx.lastJson.has("id")).as("no id in media upload response: " + ctx.lastBody).isTrue();
		return UUID.fromString(ctx.lastJson.get("id").asText());
	}

}
