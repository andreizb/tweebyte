package ro.tweebyte.equivalence.steps;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import ro.tweebyte.equivalence.support.RestApi;
import ro.tweebyte.equivalence.support.ScenarioContext;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Interaction-service domain steps — likes, retweets, replies, follows,
 * recommendations.
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

    @When("user {string} likes tweet {string}")
    public void userLikesTweet(String handle, String tweetKey) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var uid = ctx.userIdByHandle.get(handle);
        var tid = ctx.lastTweetIdByContent.get(tweetKey);
        assertNotNull(tid, "no tweet tracked: " + tweetKey);
        RestApi.postJson("/interaction-service/likes/" + uid + "/tweets/" + tid, jwt, "{}");
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
        // RetweetCreateRequest declares @JsonProperty("original_tweet_id"), not "tweetId".
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
        String body = "{\"tweet_id\":\"" + tid + "\",\"content\":\"" + content.replace("\"","\\\"") + "\"}";
        RestApi.postJson("/interaction-service/replies/" + uid, jwt, body);
    }

    @When("user {string} lists replies for tweet {string}")
    public void userListsReplies(String handle, String tweetKey) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var tid = ctx.lastTweetIdByContent.get(tweetKey);
        RestApi.get("/interaction-service/replies/tweet/" + tid, jwt);
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

    @When("user {string} updates their last reply with content {string}")
    public void userUpdatesLastReply(String handle, String content) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var uid = ctx.userIdByHandle.get(handle);
        // The id of the reply was put into ctx.lastJson["id"] by the create-reply call
        UUID replyId = (ctx.lastJson != null && ctx.lastJson.has("id"))
                ? UUID.fromString(ctx.lastJson.get("id").asText())
                : null;
        assertNotNull(replyId, "no reply tracked in last response: " + ctx.lastBody);
        String body = "{\"user_id\":\"" + uid + "\",\"content\":\""
                + content.replace("\"", "\\\"") + "\"}";
        RestApi.putJson("/interaction-service/replies/" + uid + "/" + replyId, jwt, body);
    }

    @When("user {string} deletes their last reply")
    public void userDeletesLastReply(String handle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var uid = ctx.userIdByHandle.get(handle);
        UUID replyId = (ctx.lastJson != null && ctx.lastJson.has("id"))
                ? UUID.fromString(ctx.lastJson.get("id").asText())
                : null;
        assertNotNull(replyId, "no reply tracked in last response: " + ctx.lastBody);
        RestApi.delete("/interaction-service/replies/" + uid + "/" + replyId, jwt);
    }

    @When("user {string} updates their last retweet with content {string}")
    public void userUpdatesLastRetweet(String handle, String content) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var uid = ctx.userIdByHandle.get(handle);
        UUID rtId = (ctx.lastJson != null && ctx.lastJson.has("id"))
                ? UUID.fromString(ctx.lastJson.get("id").asText())
                : null;
        assertNotNull(rtId, "no retweet tracked in last response: " + ctx.lastBody);
        String body = "{\"content\":\"" + content.replace("\"", "\\\"") + "\"}";
        RestApi.putJson("/interaction-service/retweets/" + uid + "/" + rtId, jwt, body);
    }

    @When("user {string} deletes their last retweet")
    public void userDeletesLastRetweet(String handle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var uid = ctx.userIdByHandle.get(handle);
        UUID rtId = (ctx.lastJson != null && ctx.lastJson.has("id"))
                ? UUID.fromString(ctx.lastJson.get("id").asText())
                : null;
        assertNotNull(rtId, "no retweet tracked in last response: " + ctx.lastBody);
        RestApi.delete("/interaction-service/retweets/" + uid + "/" + rtId, jwt);
    }

    @When("user {string} likes their last reply")
    public void userLikesLastReply(String handle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var uid = ctx.userIdByHandle.get(handle);
        UUID replyId = (ctx.lastJson != null && ctx.lastJson.has("id"))
                ? UUID.fromString(ctx.lastJson.get("id").asText())
                : null;
        assertNotNull(replyId, "no reply tracked in last response: " + ctx.lastBody);
        RestApi.postJson("/interaction-service/likes/" + uid + "/replies/" + replyId, jwt, "{}");
    }

    @When("user {string} unlikes their last reply")
    public void userUnlikesLastReply(String handle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var uid = ctx.userIdByHandle.get(handle);
        UUID replyId = (ctx.lastJson != null && ctx.lastJson.has("id"))
                ? UUID.fromString(ctx.lastJson.get("id").asText())
                : null;
        assertNotNull(replyId, "no reply tracked in last response: " + ctx.lastBody);
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

    /** After "user X lists their follow requests", parse the first FollowDto.id
     *  out of the response body so we can hand it to PUT/{status}. */
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
        assertNotNull(followRequestId, "no pending follow request found in: " + ctx.lastBody);
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
        String body = "{\"tweet_id\":\"" + UUID.randomUUID()
                + "\",\"content\":\"" + content.replace("\"", "\\\"") + "\"}";
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
                ? UUID.fromString(ctx.lastJson.get("id").asText())
                : null;
        assertNotNull(replyId, "no reply tracked: " + ctx.lastBody);
        RestApi.delete("/interaction-service/replies/" + uid + "/" + replyId, jwt);
    }

    @When("user {string} tries to update user {string} last reply with content {string}")
    public void userTriesToUpdateOthersReply(String handle, String otherHandle, String content) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var uid = ctx.userIdByHandle.get(handle);
        UUID replyId = (ctx.lastJson != null && ctx.lastJson.has("id"))
                ? UUID.fromString(ctx.lastJson.get("id").asText())
                : null;
        assertNotNull(replyId, "no reply tracked: " + ctx.lastBody);
        String body = "{\"user_id\":\"" + uid + "\",\"content\":\""
                + content.replace("\"", "\\\"") + "\"}";
        RestApi.putJson("/interaction-service/replies/" + uid + "/" + replyId, jwt, body);
    }

    @And("the response body is the number {int}")
    public void responseIsNumber(int expected) {
        ScenarioContext ctx = ScenarioContext.current();
        // Counts come back as a JSON number — the body is just "0" or "1" etc.
        try {
            int actual = Integer.parseInt(ctx.lastBody.trim());
            assertEquals(expected, actual);
        } catch (NumberFormatException e) {
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
            if (first.has("id") && !first.get("id").isNull()) frId = UUID.fromString(first.get("id").asText());
        }
        assertNotNull(frId, "no pending follow request: " + ctx.lastBody);
        RestApi.putJson("/interaction-service/follows/" + uid + "/" + frId + "/PENDING", jwt, "");
    }

    @When("user {string} self-accepts their pending follow request")
    public void userSelfAcceptsOwnRequest(String handle) {
        // Drives FollowService.updateFollowRequest's `followerId.equals(userId) && status == ACCEPTED` branch.
        // The follower attempts to accept their own outgoing request, which is rejected.
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var uid = ctx.userIdByHandle.get(handle);
        // Discover request id from pending list as recipient — for that we need the followee's token,
        // but here the tester usage is: the follower (handle) tries to accept by passing their own uid.
        // We'll reuse the last lookup id if known; fall back to a random UUID.
        UUID frId = (ctx.lastJson != null && ctx.lastJson.has("id"))
                ? UUID.fromString(ctx.lastJson.get("id").asText())
                : UUID.randomUUID();
        RestApi.putJson("/interaction-service/follows/" + uid + "/" + frId + "/ACCEPTED", jwt, "");
    }

    @When("user {string} updates a non-existent retweet")
    public void userUpdatesNonExistentRetweet(String handle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var uid = ctx.userIdByHandle.get(handle);
        String body = "{\"content\":\"new content for nothing\"}";
        RestApi.putJson("/interaction-service/retweets/" + uid + "/" + UUID.randomUUID(), jwt, body);
    }

    @When("user {string} deletes a non-existent retweet")
    public void userDeletesNonExistentRetweet(String handle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var uid = ctx.userIdByHandle.get(handle);
        RestApi.delete("/interaction-service/retweets/" + uid + "/" + UUID.randomUUID(), jwt);
    }

    @When("user {string} updates a non-existent reply")
    public void userUpdatesNonExistentReply(String handle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var uid = ctx.userIdByHandle.get(handle);
        String body = "{\"user_id\":\"" + uid + "\",\"content\":\"some new content here\"}";
        RestApi.putJson("/interaction-service/replies/" + uid + "/" + UUID.randomUUID(), jwt, body);
    }

    @When("user {string} deletes a non-existent reply")
    public void userDeletesNonExistentReply(String handle) {
        ScenarioContext ctx = ScenarioContext.current();
        var jwt = ctx.jwtByHandle.get(handle);
        var uid = ctx.userIdByHandle.get(handle);
        RestApi.delete("/interaction-service/replies/" + uid + "/" + UUID.randomUUID(), jwt);
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
        String body = "{\"original_tweet_id\":\"" + tid + "\",\"content\":\""
                + content.replace("\"", "\\\"") + "\"}";
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

    @When("user {string} retweets tweet {string} twice")
    public void userRetweetsTwice(String handle, String tweetKey) {
        userRetweets(handle, tweetKey);
        userRetweets(handle, tweetKey);
    }

    @When("user {string} likes tweet {string} twice")
    public void userLikesTwice(String handle, String tweetKey) {
        userLikesTweet(handle, tweetKey);
        userLikesTweet(handle, tweetKey);
    }
}
