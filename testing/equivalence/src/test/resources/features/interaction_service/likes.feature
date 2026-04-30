Feature: Likes
  POST/DELETE /interaction-service/likes/{userId}/tweets/{tweetId}.
  GET /interaction-service/likes/{tweetId}/count.

  Scenario: A user can like a tweet
    Given user "alice" is registered with email "alice@example.com" and password "Sup3rS3cret!"
    And user "bob" is registered with email "bob@example.com" and password "Sup3rS3cret!"
    When user "alice" posts tweet "tweet to be liked here"
    Then the response status is 200
    When user "bob" likes tweet "tweet to be liked here"
    Then the response status is 200

  Scenario: Like count increments after a like
    Given user "carol" is registered with email "carol@example.com" and password "Sup3rS3cret!"
    And user "dan" is registered with email "dan@example.com" and password "Sup3rS3cret!"
    When user "carol" posts tweet "another tweet for like count"
    Then the response status is 200
    When user "dan" likes tweet "another tweet for like count"
    Then the response status is 200
    When user "carol" reads like count of tweet "another tweet for like count"
    Then the response status is 200

  Scenario: A user can unlike a tweet they previously liked
    Given user "ed" is registered with email "ed@example.com" and password "Sup3rS3cret!"
    And user "fay" is registered with email "fay@example.com" and password "Sup3rS3cret!"
    When user "ed" posts tweet "yet another likeable tweet"
    Then the response status is 200
    When user "fay" likes tweet "yet another likeable tweet"
    Then the response status is 200
    When user "fay" unlikes tweet "yet another likeable tweet"
    Then the response status is 204

  Scenario: Reading like count on an unliked tweet returns 0
    Given user "gus" is registered with email "gus@example.com" and password "Sup3rS3cret!"
    When user "gus" posts tweet "a quiet untouched tweet"
    Then the response status is 200
    When user "gus" reads like count of tweet "a quiet untouched tweet"
    Then the response status is 200
    And the response body is the number 0

  Scenario: Liking own tweet succeeds
    Given user "hank" is registered with email "hank@example.com" and password "Sup3rS3cret!"
    When user "hank" posts tweet "self-promotional tweet here"
    Then the response status is 200
    When user "hank" likes tweet "self-promotional tweet here"
    Then the response status is 200

  Scenario: Listing likes by user returns 200
    Given user "iva" is registered with email "iva@example.com" and password "Sup3rS3cret!"
    When user "iva" posts tweet "tweet that iva will like"
    Then the response status is 200
    When user "iva" likes tweet "tweet that iva will like"
    Then the response status is 200
    When user "iva" lists likes by user
    Then the response status is 200

  Scenario: Listing likes by user with no likes returns empty list
    Given user "jay" is registered with email "jay@example.com" and password "Sup3rS3cret!"
    When user "jay" lists likes by user
    Then the response status is 200
    And the response body is a list of size 0

  Scenario: Listing likes for a tweet returns 200
    Given user "kim" is registered with email "kim@example.com" and password "Sup3rS3cret!"
    And user "leo" is registered with email "leo@example.com" and password "Sup3rS3cret!"
    When user "kim" posts tweet "tweet that will be liked by leo"
    Then the response status is 200
    When user "leo" likes tweet "tweet that will be liked by leo"
    Then the response status is 200
    When user "kim" lists likes for tweet "tweet that will be liked by leo"
    Then the response status is 200

  Scenario: Listing likes for a tweet with no likes returns empty list
    Given user "mia" is registered with email "mia@example.com" and password "Sup3rS3cret!"
    When user "mia" posts tweet "an unliked tweet of mia"
    Then the response status is 200
    When user "mia" lists likes for tweet "an unliked tweet of mia"
    Then the response status is 200
    And the response body is a list of size 0

  # async LikeService.likeReply now calls `findById(replyId)` (matches
  # reactive) so any user can like any reply, not just the reply author.
  Scenario: A user can like another user's reply
    Given user "rae" is registered with email "rae@example.com" and password "Sup3rS3cret!"
    And user "sam" is registered with email "sam@example.com" and password "Sup3rS3cret!"
    When user "rae" posts tweet "tweet that wants third-party reply"
    Then the response status is 200
    When user "sam" replies to tweet "tweet that wants third-party reply" with "sam reply content for like"
    Then the response status is 200
    When user "rae" likes their last reply
    Then the response status is 200

  Scenario: A user can like their own reply
    Given user "nia" is registered with email "nia@example.com" and password "Sup3rS3cret!"
    When user "nia" posts tweet "tweet seeking some replies"
    Then the response status is 200
    When user "nia" replies to tweet "tweet seeking some replies" with "nia self reply content"
    Then the response status is 200
    When user "nia" likes their last reply
    Then the response status is 200

  Scenario: A user can unlike a reply they liked themselves
    Given user "pat" is registered with email "pat@example.com" and password "Sup3rS3cret!"
    When user "pat" posts tweet "tweet for self reply unlike"
    Then the response status is 200
    When user "pat" replies to tweet "tweet for self reply unlike" with "pat self reply content"
    Then the response status is 200
    When user "pat" likes their last reply
    Then the response status is 200
    When user "pat" unlikes their last reply
    Then the response status is 204

  # Drives LikeService.likeTweet's tweet-not-found branch.
  # Both stacks surface TweetNotFoundException → 404 from TweetClient.
  Scenario: Liking a non-existent tweet returns 404
    Given user "qui" is registered with email "qui@example.com" and password "Sup3rS3cret!"
    When user "qui" likes a non-existent tweet
    Then the response status is 404

  # Drives LikeService.likeReply's "reply does not exist" branch (returns 500
  # because the IllegalArgumentException doesn't carry @ResponseStatus).
  Scenario: Liking a non-existent reply returns 500
    Given user "rae" is registered with email "rae@example.com" and password "Sup3rS3cret!"
    When user "rae" likes a non-existent reply
    Then the response status is 500

  # Like-count for a never-existing tweet still returns 0 (count query yields 0
  # rows; no NOT_FOUND).
  Scenario: Reading like count for a non-existent tweet returns 0
    Given user "sam" is registered with email "sam@example.com" and password "Sup3rS3cret!"
    When user "sam" reads like count for non-existent tweet
    Then the response status is 200
    And the response body is the number 0

  # Drives the multi-like list path on getUserLikes / getTweetLikes — multiple
  # entries flow through the LikeMapper + tweetService.getTweetSummary loop.
  Scenario: Listing likes by user with multiple likes succeeds
    Given user "tia" is registered with email "tia@example.com" and password "Sup3rS3cret!"
    When user "tia" posts tweet "first tweet for multi-like list"
    Then the response status is 200
    When user "tia" posts tweet "second tweet for multi-like list"
    Then the response status is 200
    When user "tia" likes tweet "first tweet for multi-like list"
    Then the response status is 200
    When user "tia" likes tweet "second tweet for multi-like list"
    Then the response status is 200
    When user "tia" lists likes by user
    Then the response status is 200
    And the response body is a list of size 2

  Scenario: Listing likes for a tweet with multiple likers succeeds
    Given user "ula" is registered with email "ula@example.com" and password "Sup3rS3cret!"
    And user "vie" is registered with email "vie@example.com" and password "Sup3rS3cret!"
    And user "wil" is registered with email "wil@example.com" and password "Sup3rS3cret!"
    When user "ula" posts tweet "tweet liked by two strangers"
    Then the response status is 200
    When user "vie" likes tweet "tweet liked by two strangers"
    Then the response status is 200
    When user "wil" likes tweet "tweet liked by two strangers"
    Then the response status is 200
    When user "ula" lists likes for tweet "tweet liked by two strangers"
    Then the response status is 200
    And the response body is a list of size 2

  Scenario: Like count after multiple likers reflects the total
    Given user "xia" is registered with email "xia@example.com" and password "Sup3rS3cret!"
    And user "yul" is registered with email "yul@example.com" and password "Sup3rS3cret!"
    And user "zev" is registered with email "zev@example.com" and password "Sup3rS3cret!"
    When user "xia" posts tweet "tweet liked twice in a row"
    Then the response status is 200
    When user "yul" likes tweet "tweet liked twice in a row"
    Then the response status is 200
    When user "zev" likes tweet "tweet liked twice in a row"
    Then the response status is 200
    When user "xia" reads like count of tweet "tweet liked twice in a row"
    Then the response status is 200
    And the response body is the number 2

  # Reading the same tweet's likes twice exercises the cached path in
  # TweetService.getTweetSummary (Redis hit on the second call).
  Scenario: Listing likes for the same tweet twice both succeed
    Given user "amy" is registered with email "amy@example.com" and password "Sup3rS3cret!"
    And user "bea" is registered with email "bea@example.com" and password "Sup3rS3cret!"
    When user "amy" posts tweet "tweet whose like list is read twice"
    Then the response status is 200
    When user "bea" likes tweet "tweet whose like list is read twice"
    Then the response status is 200
    When user "amy" lists likes for tweet "tweet whose like list is read twice"
    Then the response status is 200
    When user "amy" lists likes for tweet "tweet whose like list is read twice"
    Then the response status is 200
