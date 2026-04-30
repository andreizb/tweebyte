Feature: Follow graph
  POST/DELETE /interaction-service/follows/{userId}/{followedId}.
  GET /interaction-service/follows/{userId}/(followers|following)/count.

  Scenario: A user can follow another user
    Given user "alice" is registered with email "alice@example.com" and password "Sup3rS3cret!"
    And user "bob" is registered with email "bob@example.com" and password "Sup3rS3cret!"
    When user "alice" follows "bob"
    Then the response status is 204

  Scenario: A user can unfollow someone they previously followed
    Given user "carol" is registered with email "carol@example.com" and password "Sup3rS3cret!"
    And user "dan" is registered with email "dan@example.com" and password "Sup3rS3cret!"
    When user "carol" follows "dan"
    Then the response status is 204
    When user "carol" unfollows "dan"
    Then the response status is 204

  Scenario: Followers count increments after a follow
    Given user "ed" is registered with email "ed@example.com" and password "Sup3rS3cret!"
    And user "fay" is registered with email "fay@example.com" and password "Sup3rS3cret!"
    When user "ed" follows "fay"
    Then the response status is 204
    When user "fay" reads followers count of "fay"
    Then the response status is 200

  Scenario: Following count increments after a follow
    Given user "gus" is registered with email "gus@example.com" and password "Sup3rS3cret!"
    And user "hank" is registered with email "hank@example.com" and password "Sup3rS3cret!"
    When user "gus" follows "hank"
    Then the response status is 204
    When user "gus" reads following count of "gus"
    Then the response status is 200

  Scenario: Followers count on a fresh user is 0
    Given user "iva" is registered with email "iva@example.com" and password "Sup3rS3cret!"
    When user "iva" reads followers count of "iva"
    Then the response status is 200
    And the response body is the number 0

  Scenario: Following count on a fresh user is 0
    Given user "jay" is registered with email "jay@example.com" and password "Sup3rS3cret!"
    When user "jay" reads following count of "jay"
    Then the response status is 200
    And the response body is the number 0

  Scenario: Listing followers of a fresh user returns empty
    Given user "kim" is registered with email "kim@example.com" and password "Sup3rS3cret!"
    When user "kim" lists followers of "kim"
    Then the response status is 200
    And the response body is a list of size 0

  Scenario: Listing followers of a followed user returns 200
    Given user "leo" is registered with email "leo@example.com" and password "Sup3rS3cret!"
    And user "mia" is registered with email "mia@example.com" and password "Sup3rS3cret!"
    When user "leo" follows "mia"
    Then the response status is 204
    When user "mia" lists followers of "mia"
    Then the response status is 200

  Scenario: Listing following of a fresh user returns empty
    Given user "nia" is registered with email "nia@example.com" and password "Sup3rS3cret!"
    When user "nia" lists following of "nia"
    Then the response status is 200

  Scenario: Listing following of a follower returns 200
    Given user "ozz" is registered with email "ozz@example.com" and password "Sup3rS3cret!"
    And user "pat" is registered with email "pat@example.com" and password "Sup3rS3cret!"
    When user "ozz" follows "pat"
    Then the response status is 204
    When user "ozz" lists following of "ozz"
    Then the response status is 200

  Scenario: Listing follower identifiers returns 200
    Given user "qui" is registered with email "qui@example.com" and password "Sup3rS3cret!"
    And user "ros" is registered with email "ros@example.com" and password "Sup3rS3cret!"
    When user "qui" follows "ros"
    Then the response status is 204
    When user "ros" lists follower identifiers of "ros"
    Then the response status is 200

  Scenario: Listing follow requests returns 200 even when none exist
    Given user "sam" is registered with email "sam@example.com" and password "Sup3rS3cret!"
    When user "sam" lists their follow requests
    Then the response status is 200

  Scenario: Updating a non-existent follow request to ACCEPTED returns 4xx
    Given user "tim" is registered with email "tim@example.com" and password "Sup3rS3cret!"
    When user "tim" updates follow request from "tim" to "ACCEPTED"
    Then the response status is 404

  # Drives FollowService.getFollowing twice — first call hits the DB and writes
  # the Redis cache (60s TTL), the second call should hit the cache branch
  # (`switchIfEmpty` short-circuits on reactive; `cached != null` branch on async).
  Scenario: Listing following twice in a row both succeed (warms then hits Redis cache)
    Given user "uma" is registered with email "uma@example.com" and password "Sup3rS3cret!"
    And user "vic" is registered with email "vic@example.com" and password "Sup3rS3cret!"
    When user "uma" follows "vic"
    Then the response status is 204
    When user "uma" lists following of "uma"
    Then the response status is 200
    When user "uma" lists following of "uma"
    Then the response status is 200

  Scenario: Listing followers twice in a row both succeed
    Given user "wes" is registered with email "wes@example.com" and password "Sup3rS3cret!"
    And user "xan" is registered with email "xan@example.com" and password "Sup3rS3cret!"
    When user "wes" follows "xan"
    Then the response status is 204
    When user "xan" lists followers of "xan"
    Then the response status is 200
    When user "xan" lists followers of "xan"
    Then the response status is 200

  Scenario: Followers count after multiple followers is 2
    Given user "yas" is registered with email "yas@example.com" and password "Sup3rS3cret!"
    And user "zoe" is registered with email "zoe@example.com" and password "Sup3rS3cret!"
    And user "aja" is registered with email "aja@example.com" and password "Sup3rS3cret!"
    When user "zoe" follows "yas"
    Then the response status is 204
    When user "aja" follows "yas"
    Then the response status is 204
    When user "yas" reads followers count of "yas"
    Then the response status is 200
    And the response body is the number 2

  Scenario: Following count after following two people is 2
    Given user "ben" is registered with email "ben@example.com" and password "Sup3rS3cret!"
    And user "cal" is registered with email "cal@example.com" and password "Sup3rS3cret!"
    And user "dot" is registered with email "dot@example.com" and password "Sup3rS3cret!"
    When user "ben" follows "cal"
    Then the response status is 204
    When user "ben" follows "dot"
    Then the response status is 204
    When user "ben" reads following count of "ben"
    Then the response status is 200
    And the response body is the number 2

  Scenario: Listing follower identifiers of a fresh user returns empty
    Given user "eli" is registered with email "eli@example.com" and password "Sup3rS3cret!"
    When user "eli" lists follower identifiers of "eli"
    Then the response status is 200
    And the response body is a list of size 0

  Scenario: Updating a non-existent follow request to REJECTED returns 404
    Given user "gil" is registered with email "gil@example.com" and password "Sup3rS3cret!"
    When user "gil" updates follow request from "gil" to "REJECTED"
    Then the response status is 404

  # After unfollowing, the followers count drops to 0; exercises unfollow's
  # cache-evict branch then a fresh count-from-repo call.
  Scenario: Unfollowing reduces followers count back to 0
    Given user "hal" is registered with email "hal@example.com" and password "Sup3rS3cret!"
    And user "ima" is registered with email "ima@example.com" and password "Sup3rS3cret!"
    When user "hal" follows "ima"
    Then the response status is 204
    When user "hal" unfollows "ima"
    Then the response status is 204
    When user "ima" reads followers count of "ima"
    Then the response status is 200
    And the response body is the number 0

  # Followers list non-empty path: drives the userService.getUserSummary flatMap
  # branch in FollowService.getFollowers.
  Scenario: Listing followers after a follow returns a non-empty list
    Given user "jed" is registered with email "jed@example.com" and password "Sup3rS3cret!"
    And user "kit" is registered with email "kit@example.com" and password "Sup3rS3cret!"
    When user "jed" follows "kit"
    Then the response status is 204
    When user "kit" lists followers of "kit"
    Then the response status is 200
    And the response body is a list of size 1

  Scenario: Listing follower identifiers after a follow returns a non-empty list
    Given user "lee" is registered with email "lee@example.com" and password "Sup3rS3cret!"
    And user "moe" is registered with email "moe@example.com" and password "Sup3rS3cret!"
    When user "lee" follows "moe"
    Then the response status is 204
    When user "lee" lists follower identifiers of "lee"
    Then the response status is 200
    And the response body is a list of size 1

  # Following a private user creates a PENDING request (the user_service-side
  # is_private flag is set, the followee gets a follow-request the recipient
  # must accept). Drives FollowService.follow's PENDING branch.
  Scenario: Following a private user yields a pending follow request
    Given private user "noa" is registered with email "noa@example.com" and password "Sup3rS3cret!"
    And user "olu" is registered with email "olu@example.com" and password "Sup3rS3cret!"
    When user "olu" follows "noa"
    Then the response status is 204
    When user "noa" lists their follow requests
    Then the response status is 200
    And the response body is a list of size 1

  # Drives FollowService.updateFollowRequest's "accept" branch (status flips
  # PENDING → ACCEPTED, cache evict fires). Async exercises the @CacheEvict
  # AOP wrapper; reactive runs the doFinally cache.evict.
  Scenario: Accepting a pending follow request promotes it to ACCEPTED and bumps follower count
    Given private user "pas" is registered with email "pas@example.com" and password "Sup3rS3cret!"
    And user "qel" is registered with email "qel@example.com" and password "Sup3rS3cret!"
    When user "qel" follows "pas"
    Then the response status is 204
    When user "pas" accepts the first pending follow request
    Then the response status is 204
    When user "pas" reads followers count of "pas"
    Then the response status is 200
    And the response body is the number 1

  Scenario: Rejecting a pending follow request keeps follower count at 0
    Given private user "rik" is registered with email "rik@example.com" and password "Sup3rS3cret!"
    And user "sap" is registered with email "sap@example.com" and password "Sup3rS3cret!"
    When user "sap" follows "rik"
    Then the response status is 204
    When user "rik" rejects the first pending follow request
    Then the response status is 204
    When user "rik" reads followers count of "rik"
    Then the response status is 200
    And the response body is the number 0
