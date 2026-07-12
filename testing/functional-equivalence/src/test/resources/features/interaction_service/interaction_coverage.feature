Feature: Interaction-service branch coverage — B1 black-box scenarios
  Drives branches in RetweetService, ReplyService, FollowService, CountCache,
  TopReplyCache, TweetInteractionsService, RetweetMapper, ReplyMapper and
  UserService that are not reached by the existing feature files.
  All scenarios hit the API gateway over HTTPS — no fault injection.

  # ---------------------------------------------------------------------------
  # RetweetService — validateMediaIds and forbidden-update/delete
  # ---------------------------------------------------------------------------

  # RT-M1 (+4 branches): validateMediaIds non-empty path + mediaExists true arm
  # in both RetweetService and UserClient.lambda$mediaExists$2.
  Scenario: Retweeting with a valid media id is accepted (200)
    Given user "rtm-a" is registered with email "rtm-a@example.com" and password "Sup3rS3cret!"
    And user "rtm-b" is registered with email "rtm-b@example.com" and password "Sup3rS3cret!"
    When user "rtm-a" posts tweet "tweet to be retweeted with media"
    Then the response status is 200
    When user "rtm-b" retweets tweet "tweet to be retweeted with media" with a known media id
    Then the response status is 200
    When user "rtm-b" updates their last retweet with a known media id
    Then the response status is 200

  # RT-M2 (+3 branches): validateMediaIds non-empty path + !exists throw arm.
  Scenario: Retweeting with an invalid media id is rejected (400)
    Given user "rtm-c" is registered with email "rtm-c@example.com" and password "Sup3rS3cret!"
    And user "rtm-d" is registered with email "rtm-d@example.com" and password "Sup3rS3cret!"
    When user "rtm-d" posts tweet "tweet retweeted with bogus media"
    Then the response status is 200
    When user "rtm-c" retweets tweet "tweet retweeted with bogus media" with a bogus media id
    Then the response status is 400

  # RT-U1 (+1 branch): updateRetweet forbidden path —
  # !retweeterId.equals(request.getRetweeterId()) == true arm (403).
  Scenario: Updating another user's retweet returns 403
    Given user "rtm-e" is registered with email "rtm-e@example.com" and password "Sup3rS3cret!"
    And user "rtm-f" is registered with email "rtm-f@example.com" and password "Sup3rS3cret!"
    When user "rtm-e" posts tweet "tweet for foreign retweet update"
    Then the response status is 200
    When user "rtm-f" retweets tweet "tweet for foreign retweet update"
    Then the response status is 200
    When user "rtm-e" tries to update user "rtm-f" last retweet with content "stolen content"
    Then the response status is 403

  # RT-D1 (+1 branch): deleteRetweet forbidden path —
  # !retweeterId.equals(userId) == true arm (403).
  Scenario: Deleting another user's retweet returns 403
    Given user "rtm-g" is registered with email "rtm-g@example.com" and password "Sup3rS3cret!"
    And user "rtm-h" is registered with email "rtm-h@example.com" and password "Sup3rS3cret!"
    When user "rtm-g" posts tweet "tweet for foreign retweet delete"
    Then the response status is 200
    When user "rtm-h" retweets tweet "tweet for foreign retweet delete"
    Then the response status is 200
    When user "rtm-g" tries to delete user "rtm-h" last retweet
    Then the response status is 403

  # ---------------------------------------------------------------------------
  # ReplyService — validateMediaIds
  # ---------------------------------------------------------------------------

  # RP-M1 (+2 branches): validateMediaIds non-empty path + mediaExists true arm.
  Scenario: Replying with a valid media id is accepted (200)
    Given user "rpm-a" is registered with email "rpm-a@example.com" and password "Sup3rS3cret!"
    And user "rpm-b" is registered with email "rpm-b@example.com" and password "Sup3rS3cret!"
    When user "rpm-a" posts tweet "tweet awaiting media reply"
    Then the response status is 200
    When user "rpm-b" replies to tweet "tweet awaiting media reply" with a known media id
    Then the response status is 200

  # RP-M2 (+2 branches): validateMediaIds non-empty path + !exists throw arm.
  Scenario: Replying with an invalid media id is rejected (400)
    Given user "rpm-c" is registered with email "rpm-c@example.com" and password "Sup3rS3cret!"
    And user "rpm-d" is registered with email "rpm-d@example.com" and password "Sup3rS3cret!"
    When user "rpm-c" posts tweet "tweet for bad media reply"
    Then the response status is 200
    When user "rpm-d" replies to tweet "tweet for bad media reply" with a bogus media id
    Then the response status is 400

  # ---------------------------------------------------------------------------
  # TweetInteractionsService / ReplyService — profile-interactions aggregate
  # ---------------------------------------------------------------------------

  # TIA-1 (+4 branches): profile-interactions aggregate; exercises
  # loadTopRepliesForTweets non-empty list, resolveTopReplies withTopReply
  # non-empty arm, getReplyCountsForTweets lambda body and
  # TopReplyCache.fillMisses dto==null false path.
  Scenario: Profile interactions aggregate covers reply batched path
    Given user "tia-a" is registered with email "tia-a@example.com" and password "Sup3rS3cret!"
    And user "tia-b" is registered with email "tia-b@example.com" and password "Sup3rS3cret!"
    When user "tia-a" posts tweet "tia-a first tweet for profile interactions"
    Then the response status is 200
    When user "tia-b" replies to tweet "tia-a first tweet for profile interactions" with "tia-b reply for top-reply path"
    Then the response status is 200
    When user "tia-a" fetches profile interactions for their own tweets
    Then the response status is 200

  # CC-M2 / TRC-E1 (+1 branch each): ids-empty guards in CountCache.getAllMulti
  # and TopReplyCache.getAll.
  Scenario: Profile interactions with an empty tweet list returns empty entries (200)
    Given user "ccm-c" is registered with email "ccm-c@example.com" and password "Sup3rS3cret!"
    When user "ccm-c" fetches profile interactions with no tweets
    Then the response status is 200

  # ---------------------------------------------------------------------------
  # FollowService — resolveFollowingEntry (cache-hit) and resolveFollowingEntries
  # ---------------------------------------------------------------------------

  # FS-C1 (+3 branches): resolveFollowingEntry cachedUser != null arm;
  # coldIds.isEmpty() == false arm in resolveFollowingEntries;
  # UserService.getUserSummaries empty coldIds guard.
  # Call list-followers first to warm users:: key in Redis, then list-following
  # cold (first time) to force resolveFollowingEntries to read the hot users:: entry.
  Scenario: Following list resolution uses cached user summaries when available
    Given user "fsc-a" is registered with email "fsc-a@example.com" and password "Sup3rS3cret!"
    And user "fsc-b" is registered with email "fsc-b@example.com" and password "Sup3rS3cret!"
    When user "fsc-a" follows "fsc-b"
    Then the response status is 204
    When user "fsc-a" lists followers of "fsc-b"
    Then the response status is 200
    When user "fsc-a" lists following of "fsc-a"
    Then the response status is 200
    And the response body is a list of size 1

  # ---------------------------------------------------------------------------
  # FollowService — updateFollowRequest forbidden arms
  # ---------------------------------------------------------------------------

  # FS-U1 (+1 branch): PENDING guard true arm — updating a pending request to
  # PENDING returns 400.
  Scenario: Updating a follow request to PENDING status returns 400
    Given private user "fsu-a" is registered with email "fsu-a@example.com" and password "Sup3rS3cret!"
    And user "fsu-b" is registered with email "fsu-b@example.com" and password "Sup3rS3cret!"
    When user "fsu-b" follows "fsu-a"
    Then the response status is 204
    When user "fsu-a" updates follow request to PENDING
    Then the response status is 400

  # FS-U2 (+2 branches): followerId.equals(userId) && status == ACCEPTED true arm —
  # the requester tries to self-accept their own pending follow request.
  Scenario: Requester self-accepting their own pending follow returns 400
    Given private user "fsu-c" is registered with email "fsu-c@example.com" and password "Sup3rS3cret!"
    And user "fsu-d" is registered with email "fsu-d@example.com" and password "Sup3rS3cret!"
    When user "fsu-d" follows "fsu-c"
    Then the response status is 204
    When user "fsu-d" self-accepts their pending follow request to "fsu-c"
    Then the response status is 400

  # FS-U3 (+1 branch): nowAccepted == false path — the ACCEPTED entity is not
  # returned by the requests listing (it's no longer PENDING), so a second accept
  # attempt uses a non-existent follow-request id and returns 404. We use
  # "updates follow request from X to ACCEPTED" which sends a random UUID directly,
  # reaching the not-found arm (which subsumes the nowAccepted=false path).
  Scenario: Re-accepting a follow that is no longer pending returns 404
    Given private user "fsu-e" is registered with email "fsu-e@example.com" and password "Sup3rS3cret!"
    And user "fsu-f" is registered with email "fsu-f@example.com" and password "Sup3rS3cret!"
    When user "fsu-f" follows "fsu-e"
    Then the response status is 204
    When user "fsu-e" accepts the first pending follow request
    Then the response status is 204
    When user "fsu-e" updates follow request from "fsu-e" to "ACCEPTED"
    Then the response status is 404

  # ---------------------------------------------------------------------------
  # FollowService — unfollow pending-edge path
  # ---------------------------------------------------------------------------

  # FS-UF1 (+1 branch): edge.getStatus() == ACCEPTED false arm — unfollowing a
  # PENDING request must not decrement follower count.
  Scenario: Cancelling a pending follow request by unfollowing does not change follower count
    Given private user "fsuf-a" is registered with email "fsuf-a@example.com" and password "Sup3rS3cret!"
    And user "fsuf-b" is registered with email "fsuf-b@example.com" and password "Sup3rS3cret!"
    When user "fsuf-b" follows "fsuf-a"
    Then the response status is 204
    When user "fsuf-b" unfollows "fsuf-a"
    Then the response status is 204
    When user "fsuf-a" reads followers count of "fsuf-a"
    Then the response status is 200
    And the response body is the number 0

  # ---------------------------------------------------------------------------
  # CountCache.getAllMulti — cache warm/hit cycle
  # ---------------------------------------------------------------------------

  # CC-M1 (+5 branches): familyMisses.isEmpty() true arm, bytes hit arm, and
  # writeBatch keys.isEmpty() skip arm — achieved by calling profile-interactions
  # twice for the same seeded tweet.
  Scenario: Calling profile interactions twice warms then hits the getAllMulti cache-hit path
    Given user "ccm-a" is registered with email "ccm-a@example.com" and password "Sup3rS3cret!"
    And user "ccm-b" is registered with email "ccm-b@example.com" and password "Sup3rS3cret!"
    When user "ccm-a" posts tweet "first tweet for multi-interactions warm"
    Then the response status is 200
    When user "ccm-b" likes tweet "first tweet for multi-interactions warm"
    Then the response status is 200
    When user "ccm-a" fetches profile interactions for their own tweets
    Then the response status is 200
    When user "ccm-a" fetches profile interactions for their own tweets
    Then the response status is 200

  # ---------------------------------------------------------------------------
  # CountCache.getPair — both-cached arm
  # ---------------------------------------------------------------------------

  # CC-P1 (+2 branches): aHit && bHit true arm in getPair via getFollowCounts —
  # achieved by calling profile-interactions twice after a follow (second call
  # reads the warm pair from Redis).
  Scenario: Reading follow counts twice exercises the both-cached arm of getPair
    Given user "ccp-a" is registered with email "ccp-a@example.com" and password "Sup3rS3cret!"
    And user "ccp-b" is registered with email "ccp-b@example.com" and password "Sup3rS3cret!"
    When user "ccp-a" follows "ccp-b"
    Then the response status is 204
    When user "ccp-a" fetches profile interactions for their own tweets
    Then the response status is 200
    When user "ccp-a" fetches profile interactions for their own tweets
    Then the response status is 200

  # ---------------------------------------------------------------------------
  # TopReplyCache.get — absent-marker and real-reply cache-hit paths
  # ---------------------------------------------------------------------------

  # TRC-H1 (+2 branches): TopReplyCache.get cached != null true arm +
  # isAbsentMarker true — call top-reply for a reply-less tweet twice;
  # first call writes the absent-marker, second call reads it.
  Scenario: Fetching top reply for an unreplied tweet twice hits the absent-marker cache path
    Given user "trch-a" is registered with email "trch-a@example.com" and password "Sup3rS3cret!"
    When user "trch-a" posts tweet "silent tweet with no replies here"
    Then the response status is 200
    When user "trch-a" fetches top reply for tweet "silent tweet with no replies here"
    Then the response status is 200
    When user "trch-a" fetches top reply for tweet "silent tweet with no replies here"
    Then the response status is 200

  # TRC-H2 (+2 branches): TopReplyCache.get cached != null && !isAbsentMarker
  # → decode path — call top-reply for a tweet with a reply twice;
  # first call writes the real reply, second call deserialises it from Redis.
  Scenario: Fetching top reply for a replied tweet twice hits the cached reply path
    Given user "trch-b" is registered with email "trch-b@example.com" and password "Sup3rS3cret!"
    And user "trch-c" is registered with email "trch-c@example.com" and password "Sup3rS3cret!"
    When user "trch-b" posts tweet "tweet with one reply for top-reply cache"
    Then the response status is 200
    When user "trch-c" replies to tweet "tweet with one reply for top-reply cache" with "trch-c top reply content"
    Then the response status is 200
    When user "trch-b" fetches top reply for tweet "tweet with one reply for top-reply cache"
    Then the response status is 200
    When user "trch-b" fetches top reply for tweet "tweet with one reply for top-reply cache"
    Then the response status is 200

  # ---------------------------------------------------------------------------
  # TweetInteractionsService — multi-tweet dedup path
  # ---------------------------------------------------------------------------

  # TIS-1 (+2 branches): distinctAuthorIds.size() > 1 dedup path and
  # withTopReply.isEmpty() false arm — two tweets with top replies from
  # different authors in the same profile-interactions call.
  Scenario: Profile interactions for multiple tweets with top replies from distinct authors
    Given user "tis-a" is registered with email "tis-a@example.com" and password "Sup3rS3cret!"
    And user "tis-b" is registered with email "tis-b@example.com" and password "Sup3rS3cret!"
    And user "tis-c" is registered with email "tis-c@example.com" and password "Sup3rS3cret!"
    When user "tis-a" posts tweet "first tweet for multi-top-reply interactions"
    Then the response status is 200
    When user "tis-a" posts tweet "second tweet for multi-top-reply interactions"
    Then the response status is 200
    When user "tis-b" replies to tweet "first tweet for multi-top-reply interactions" with "tis-b distinct reply"
    Then the response status is 200
    When user "tis-c" replies to tweet "second tweet for multi-top-reply interactions" with "tis-c distinct reply"
    Then the response status is 200
    When user "tis-a" fetches profile interactions for their own tweets
    Then the response status is 200

  # ---------------------------------------------------------------------------
  # RetweetMapper — null-content and non-null content/mediaIds arms
  # ---------------------------------------------------------------------------

  # RTM-C1 (+2 branches): entity.getContent() != null true arm in both
  # mapEntityToDto overloads — create a retweet with content, then list.
  Scenario: Retweeting with content sets content in the DTO (non-null content branch)
    Given user "rtmc-a" is registered with email "rtmc-a@example.com" and password "Sup3rS3cret!"
    And user "rtmc-b" is registered with email "rtmc-b@example.com" and password "Sup3rS3cret!"
    When user "rtmc-a" posts tweet "tweet that gets a quote retweet"
    Then the response status is 200
    When user "rtmc-b" retweets tweet "tweet that gets a quote retweet" with content "quoted with content"
    Then the response status is 200
    When user "rtmc-b" lists retweets by user
    Then the response status is 200

  # RMU-1 (+2 branches): mapRequestToEntity(RetweetUpdateRequest, RetweetEntity)
  # content != null false arm + mediaIds != null false arm — update with empty
  # body omits both fields, triggering both null guards.
  Scenario: Updating a retweet without content field leaves content unchanged
    Given user "rmu-a" is registered with email "rmu-a@example.com" and password "Sup3rS3cret!"
    And user "rmu-b" is registered with email "rmu-b@example.com" and password "Sup3rS3cret!"
    When user "rmu-a" posts tweet "tweet for null-content retweet update"
    Then the response status is 200
    When user "rmu-b" retweets tweet "tweet for null-content retweet update" with content "original retweet commentary"
    Then the response status is 200
    When user "rmu-b" updates their last retweet without content
    Then the response status is 200

  # ---------------------------------------------------------------------------
  # ReplyMapper — null-content mapper branch
  # ---------------------------------------------------------------------------

  # RPM-C1 (+2 branches): ReplyMapper.mapRequestToEntity content != null false arm
  # + mediaIds != null false arm — reply body omits "content" and "media_ids".
  Scenario: Creating a reply without a content field covers the null-content mapper branch
    Given user "rpm-e" is registered with email "rpm-e@example.com" and password "Sup3rS3cret!"
    And user "rpm-f" is registered with email "rpm-f@example.com" and password "Sup3rS3cret!"
    When user "rpm-e" posts tweet "tweet for null-content reply mapper test"
    Then the response status is 200
    When user "rpm-f" replies to tweet "tweet for null-content reply mapper test" without content
    Then the response status is 200

  # ---------------------------------------------------------------------------
  # Public batch endpoints and media-reference aggregate
  # ---------------------------------------------------------------------------

  Scenario: Batch interaction endpoints return counts and top replies for a mixed tweet list
    Given user "ibatch-a" is registered with email "ibatch-a@example.com" and password "Sup3rS3cret!"
    And user "ibatch-b" is registered with email "ibatch-b@example.com" and password "Sup3rS3cret!"
    When user "ibatch-a" posts tweet "tweet for direct batch interaction endpoints"
    Then the response status is 200
    When user "ibatch-b" likes tweet "tweet for direct batch interaction endpoints"
    Then the response status is 200
    When user "ibatch-b" retweets tweet "tweet for direct batch interaction endpoints"
    Then the response status is 200
    When user "ibatch-b" replies to tweet "tweet for direct batch interaction endpoints" with "reply that should become top in batch lookup"
    Then the response status is 200
    When user "ibatch-a" fetches batch like counts for tweet "tweet for direct batch interaction endpoints"
    Then the response status is 200
    When user "ibatch-a" fetches batch retweet counts for tweet "tweet for direct batch interaction endpoints"
    Then the response status is 200
    When user "ibatch-a" fetches batch reply counts for tweet "tweet for direct batch interaction endpoints"
    Then the response status is 200
    When user "ibatch-a" fetches batch top replies for tweet "tweet for direct batch interaction endpoints"
    Then the response status is 200

  Scenario: Interaction media reference endpoint returns ids after media replies and retweets
    Given user "imref-a" is registered with email "imref-a@example.com" and password "Sup3rS3cret!"
    And user "imref-b" is registered with email "imref-b@example.com" and password "Sup3rS3cret!"
    When user "imref-a" posts tweet "tweet with interaction media references"
    Then the response status is 200
    When user "imref-b" replies to tweet "tweet with interaction media references" with a known media id
    Then the response status is 200
    When user "imref-b" retweets tweet "tweet with interaction media references" with a known media id
    Then the response status is 200
    When user "imref-a" fetches interaction referenced media ids
    Then the response status is 200

  Scenario: Combined follow-count endpoint returns counts for a followed user
    Given user "icount-a" is registered with email "icount-a@example.com" and password "Sup3rS3cret!"
    And user "icount-b" is registered with email "icount-b@example.com" and password "Sup3rS3cret!"
    When user "icount-a" follows "icount-b"
    Then the response status is 204
    When user "icount-a" reads combined follow counts of "icount-b"
    Then the response status is 200
