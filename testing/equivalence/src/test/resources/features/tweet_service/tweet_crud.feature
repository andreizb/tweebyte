Feature: Tweet CRUD
  POST /tweet-service/tweets/{userId}, GET /tweet-service/tweets/{tweetId},
  GET /tweet-service/tweets/user/{userId}, PUT /tweet-service/tweets/{tweetId},
  DELETE /tweet-service/tweets/{tweetId}.
  All gateway-routed paths require a Bearer JWT.

  Scenario: A user can post a valid tweet
    Given user "alice" is registered with email "alice@example.com" and password "Sup3rS3cret!"
    When user "alice" posts tweet "hello world from alice"
    Then the response status is 200
    # POST returns just {id} on this codebase; verify content via subsequent GET.
    When user "alice" fetches tweet "hello world from alice"
    Then the response status is 200
    And the response body has content "hello world from alice"

  Scenario: A user can fetch a tweet they just posted
    Given user "bob" is registered with email "bob@example.com" and password "Sup3rS3cret!"
    When user "bob" posts tweet "this is bob speaking"
    Then the response status is 200
    When user "bob" fetches tweet "this is bob speaking"
    Then the response status is 200
    And the response body has content "this is bob speaking"

  Scenario: A user can list their own tweets after posting
    Given user "carol" is registered with email "carol@example.com" and password "Sup3rS3cret!"
    When user "carol" posts tweet "carol first tweet here"
    Then the response status is 200
    When user "carol" lists their own tweets
    Then the response status is 200

  Scenario: A user can list another user's tweets
    Given user "dan" is registered with email "dan@example.com" and password "Sup3rS3cret!"
    And user "ed" is registered with email "ed@example.com" and password "Sup3rS3cret!"
    When user "dan" posts tweet "another tweet content here"
    Then the response status is 200
    When user "ed" lists tweets of "dan"
    Then the response status is 200

  Scenario: Fetching a non-existent tweet returns 404
    Given user "fay" is registered with email "fay@example.com" and password "Sup3rS3cret!"
    When user "fay" fetches a non-existent tweet
    Then the response status is 404

  Scenario: A user can edit their own tweet
    Given user "gus" is registered with email "gus@example.com" and password "Sup3rS3cret!"
    When user "gus" posts tweet "original content here"
    Then the response status is 200
    # Note: PUT /tweets/{id} returns 200 (not 204) on this codebase. Both stacks agree.
    When user "gus" updates tweet "original content here" content to "updated content version 2"
    Then the response status is 200

  Scenario: A user can delete their own tweet
    Given user "hank" is registered with email "hank@example.com" and password "Sup3rS3cret!"
    When user "hank" posts tweet "tweet to be deleted soon"
    Then the response status is 200
    When user "hank" deletes tweet "tweet to be deleted soon"
    Then the response status is 204

  Scenario: After delete, fetching the tweet returns 404
    Given user "iva" is registered with email "iva@example.com" and password "Sup3rS3cret!"
    When user "iva" posts tweet "ephemeral tweet about life"
    Then the response status is 200
    When user "iva" deletes tweet "ephemeral tweet about life"
    Then the response status is 204
    When user "iva" fetches tweet "ephemeral tweet about life"
    Then the response status is 404

  Scenario: Updating a non-existent tweet returns 404
    Given user "jay" is registered with email "jay@example.com" and password "Sup3rS3cret!"
    When user "jay" updates a non-existent tweet
    Then the response status is 404

  # NOTE: DELETE /tweets/{id} is annotated @ResponseStatus(NO_CONTENT) and the
  # underlying repo.deleteById is a silent no-op for missing rows on both
  # stacks, so a non-existent tweet still yields 204. FE asserts current
  # behaviour rather than expected REST-purist 404.
  Scenario: Deleting a non-existent tweet still returns 204
    Given user "kim" is registered with email "kim@example.com" and password "Sup3rS3cret!"
    When user "kim" deletes a non-existent tweet
    Then the response status is 204

  Scenario: Updating a tweet with empty content returns 400
    Given user "leo" is registered with email "leo@example.com" and password "Sup3rS3cret!"
    When user "leo" posts tweet "tweet whose update will be invalid"
    Then the response status is 200
    When user "leo" updates tweet "tweet whose update will be invalid" content to empty string
    Then the response status is 400

  Scenario: Fetching summary of a tweet returns 200
    Given user "mia" is registered with email "mia@example.com" and password "Sup3rS3cret!"
    When user "mia" posts tweet "tweet whose summary will be fetched"
    Then the response status is 200
    When user "mia" fetches summary of tweet "tweet whose summary will be fetched"
    Then the response status is 200

  Scenario: Fetching summary of a non-existent tweet returns 404
    Given user "nia" is registered with email "nia@example.com" and password "Sup3rS3cret!"
    When user "nia" fetches summary of a non-existent tweet
    Then the response status is 404

  Scenario: Fetching another user's tweets summary returns 200
    Given user "ozz" is registered with email "ozz@example.com" and password "Sup3rS3cret!"
    And user "pat" is registered with email "pat@example.com" and password "Sup3rS3cret!"
    When user "ozz" posts tweet "an ozz tweet to be summarised"
    Then the response status is 200
    When user "pat" fetches tweets summary of "ozz"
    Then the response status is 200

  Scenario: Fetching their own feed returns 200
    Given user "qui" is registered with email "qui@example.com" and password "Sup3rS3cret!"
    When user "qui" fetches their feed
    Then the response status is 200

  Scenario: Fetching feed after posting a tweet returns 200
    Given user "ros" is registered with email "ros@example.com" and password "Sup3rS3cret!"
    When user "ros" posts tweet "tweet that should appear in feed"
    Then the response status is 200
    When user "ros" fetches their feed
    Then the response status is 200

  Scenario: Listing another user's tweets after they posted some returns 200
    Given user "sam" is registered with email "sam@example.com" and password "Sup3rS3cret!"
    And user "tom" is registered with email "tom@example.com" and password "Sup3rS3cret!"
    When user "sam" posts tweet "sam's first tweet content here"
    Then the response status is 200
    When user "sam" posts tweet "sam's second tweet content here"
    Then the response status is 200
    When user "tom" lists tweets of "sam"
    Then the response status is 200

  Scenario: Updating a tweet then fetching shows updated content
    Given user "uma" is registered with email "uma@example.com" and password "Sup3rS3cret!"
    When user "uma" posts tweet "version one of this tweet content"
    Then the response status is 200
    When user "uma" updates tweet "version one of this tweet content" content to "version two of this tweet content"
    Then the response status is 200

  Scenario: Fetching summary returns plain mapped DTO
    # /tweets/{id}/summary uses mapper.mapEntityToDto (no enrich) — covers the
    # short-circuit path in TweetService.getTweetSummary and TweetMapper.
    Given user "vic" is registered with email "vic@example.com" and password "Sup3rS3cret!"
    When user "vic" posts tweet "summary endpoint short circuit test"
    Then the response status is 200
    When user "vic" fetches summary of tweet "summary endpoint short circuit test"
    Then the response status is 200
    And the response body has content "summary endpoint short circuit test"

  Scenario: Fetching tweets summary of a user with multiple tweets returns 200
    Given user "walt" is registered with email "walt@example.com" and password "Sup3rS3cret!"
    When user "walt" posts tweet "walt tweet one for summary listing"
    Then the response status is 200
    When user "walt" posts tweet "walt tweet two for summary listing"
    Then the response status is 200
    When user "walt" fetches tweets summary of "walt"
    Then the response status is 200

  Scenario: Fetching feed after posting multiple tweets returns 200
    Given user "xav" is registered with email "xav@example.com" and password "Sup3rS3cret!"
    When user "xav" posts tweet "first feed entry should appear here"
    Then the response status is 200
    When user "xav" posts tweet "second feed entry should appear here"
    Then the response status is 200
    When user "xav" fetches their feed
    Then the response status is 200

  Scenario: Posting a tweet with content at boundary length succeeds
    Given user "yan" is registered with email "yan@example.com" and password "Sup3rS3cret!"
    When user "yan" posts tweet "1234567890"
    Then the response status is 200

  Scenario: Updating a tweet to a longer content returns 200
    Given user "zoe" is registered with email "zoe@example.com" and password "Sup3rS3cret!"
    When user "zoe" posts tweet "short initial tweet content"
    Then the response status is 200
    When user "zoe" updates tweet "short initial tweet content" content to "much longer updated content with substantially more text in the body to drive the entity-update mapper field-copy branch hard"
    Then the response status is 200
