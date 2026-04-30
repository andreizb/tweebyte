Feature: Replies
  POST /interaction-service/replies/{userId} body {tweetId,content}.
  GET /interaction-service/replies/tweet/{tweetId}.
  GET /interaction-service/replies/tweet/{tweetId}/count.

  Scenario: A user can reply to a tweet
    Given user "alice" is registered with email "alice@example.com" and password "Sup3rS3cret!"
    And user "bob" is registered with email "bob@example.com" and password "Sup3rS3cret!"
    When user "alice" posts tweet "tweet that wants a reply"
    Then the response status is 200
    When user "bob" replies to tweet "tweet that wants a reply" with "this is bobs reply content"
    Then the response status is 200

  Scenario: Reply count increments after a reply
    Given user "carol" is registered with email "carol@example.com" and password "Sup3rS3cret!"
    And user "dan" is registered with email "dan@example.com" and password "Sup3rS3cret!"
    When user "carol" posts tweet "another tweet for a reply"
    Then the response status is 200
    When user "dan" replies to tweet "another tweet for a reply" with "dans well-considered reply"
    Then the response status is 200
    When user "carol" reads reply count of tweet "another tweet for a reply"
    Then the response status is 200

  Scenario: Listing replies on an unreplied tweet returns an empty list
    Given user "ed" is registered with email "ed@example.com" and password "Sup3rS3cret!"
    When user "ed" posts tweet "ignored tweet without replies"
    Then the response status is 200
    When user "ed" lists replies for tweet "ignored tweet without replies"
    Then the response status is 200
    And the response body is a list of size 0

  Scenario: Reply count on an unreplied tweet is 0
    Given user "fay" is registered with email "fay@example.com" and password "Sup3rS3cret!"
    When user "fay" posts tweet "another quiet tweet here"
    Then the response status is 200
    When user "fay" reads reply count of tweet "another quiet tweet here"
    Then the response status is 200
    And the response body is the number 0

  Scenario: A user can reply to their own tweet
    Given user "gus" is registered with email "gus@example.com" and password "Sup3rS3cret!"
    When user "gus" posts tweet "self reply target tweet here"
    Then the response status is 200
    When user "gus" replies to tweet "self reply target tweet here" with "self reply content here"
    Then the response status is 200

  Scenario: Top reply on an unreplied tweet returns 200 with empty placeholder
    Given user "hank" is registered with email "hank@example.com" and password "Sup3rS3cret!"
    When user "hank" posts tweet "another tweet without any replies"
    Then the response status is 200
    When user "hank" fetches top reply for tweet "another tweet without any replies"
    Then the response status is 200

  Scenario: Top reply returns 200 once at least one reply exists
    Given user "iva" is registered with email "iva@example.com" and password "Sup3rS3cret!"
    And user "jay" is registered with email "jay@example.com" and password "Sup3rS3cret!"
    When user "iva" posts tweet "tweet seeking exactly one reply"
    Then the response status is 200
    When user "jay" replies to tweet "tweet seeking exactly one reply" with "the only reply available here"
    Then the response status is 200
    When user "iva" fetches top reply for tweet "tweet seeking exactly one reply"
    Then the response status is 200

  Scenario: A user can update their reply content
    Given user "kim" is registered with email "kim@example.com" and password "Sup3rS3cret!"
    And user "leo" is registered with email "leo@example.com" and password "Sup3rS3cret!"
    When user "kim" posts tweet "tweet whose reply will be edited"
    Then the response status is 200
    When user "leo" replies to tweet "tweet whose reply will be edited" with "leo first reply content"
    Then the response status is 200
    When user "leo" updates their last reply with content "leo edited reply content"
    Then the response status is 200

  Scenario: A user can delete their reply
    Given user "mia" is registered with email "mia@example.com" and password "Sup3rS3cret!"
    And user "nia" is registered with email "nia@example.com" and password "Sup3rS3cret!"
    When user "mia" posts tweet "tweet whose reply will be deleted"
    Then the response status is 200
    When user "nia" replies to tweet "tweet whose reply will be deleted" with "transient reply content"
    Then the response status is 200
    When user "nia" deletes their last reply
    Then the response status is 200

  # Drives ReplyService.createReply's tweet-not-found branch — 404 from
  # TweetClient.
  Scenario: Replying to a non-existent tweet returns 404
    Given user "ozz" is registered with email "ozz@example.com" and password "Sup3rS3cret!"
    When user "ozz" replies to a non-existent tweet with "this should fail"
    Then the response status is 404

  # Listing replies on a tweet with multiple replies exercises the per-entity
  # userService.getUserSummary loop in ReplyService.getRepliesForTweet.
  Scenario: Listing replies on a multi-replied tweet succeeds
    Given user "pat" is registered with email "pat@example.com" and password "Sup3rS3cret!"
    And user "qua" is registered with email "qua@example.com" and password "Sup3rS3cret!"
    And user "rio" is registered with email "rio@example.com" and password "Sup3rS3cret!"
    When user "pat" posts tweet "tweet wanted by two repliers"
    Then the response status is 200
    When user "qua" replies to tweet "tweet wanted by two repliers" with "first independent reply here"
    Then the response status is 200
    When user "rio" replies to tweet "tweet wanted by two repliers" with "second independent reply here"
    Then the response status is 200
    When user "pat" lists replies for tweet "tweet wanted by two repliers"
    Then the response status is 200
    And the response body is a list of size 2

  Scenario: Reply count after two replies reflects the total
    Given user "sue" is registered with email "sue@example.com" and password "Sup3rS3cret!"
    And user "tod" is registered with email "tod@example.com" and password "Sup3rS3cret!"
    And user "uma" is registered with email "uma@example.com" and password "Sup3rS3cret!"
    When user "sue" posts tweet "tweet expecting two replies"
    Then the response status is 200
    When user "tod" replies to tweet "tweet expecting two replies" with "tod reply for count"
    Then the response status is 200
    When user "uma" replies to tweet "tweet expecting two replies" with "uma reply for count"
    Then the response status is 200
    When user "sue" reads reply count of tweet "tweet expecting two replies"
    Then the response status is 200
    And the response body is the number 2

  # Reading replies twice exercises the cached path in TweetService underneath
  # (and the userService.getUserSummary cache too).
  Scenario: Listing replies for the same tweet twice both succeed
    Given user "vic" is registered with email "vic@example.com" and password "Sup3rS3cret!"
    And user "wen" is registered with email "wen@example.com" and password "Sup3rS3cret!"
    When user "vic" posts tweet "tweet whose replies are listed twice"
    Then the response status is 200
    When user "wen" replies to tweet "tweet whose replies are listed twice" with "wen reply for double list"
    Then the response status is 200
    When user "vic" lists replies for tweet "tweet whose replies are listed twice"
    Then the response status is 200
    When user "vic" lists replies for tweet "tweet whose replies are listed twice"
    Then the response status is 200

  # Drives ReplyService.deleteReply's "Unauthorized" branch — when a non-author
  # tries to delete, both stacks raise an IllegalArgumentException → 500.
  # We also wait briefly for async — its delete path runs through CompletableFuture.
  Scenario: Deleting another user's reply is rejected
    Given user "xio" is registered with email "xio@example.com" and password "Sup3rS3cret!"
    And user "yan" is registered with email "yan@example.com" and password "Sup3rS3cret!"
    When user "xio" posts tweet "tweet whose reply is foreign-deleted"
    Then the response status is 200
    When user "yan" replies to tweet "tweet whose reply is foreign-deleted" with "foreign-deleted reply content"
    Then the response status is 200
    When user "xio" tries to delete user "yan" last reply
    Then the response status is 500

  # Drives ReplyService.updateReply's unauthorized branch.
  Scenario: Updating another user's reply is rejected
    Given user "zac" is registered with email "zac@example.com" and password "Sup3rS3cret!"
    And user "abe" is registered with email "abe@example.com" and password "Sup3rS3cret!"
    When user "zac" posts tweet "tweet whose reply is foreign-edited"
    Then the response status is 200
    When user "abe" replies to tweet "tweet whose reply is foreign-edited" with "abe reply foreign-edit"
    Then the response status is 200
    When user "zac" tries to update user "abe" last reply with content "stolen edit"
    Then the response status is 500
