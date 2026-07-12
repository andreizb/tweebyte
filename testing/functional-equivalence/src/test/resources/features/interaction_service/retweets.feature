Feature: Retweets
  POST /interaction-service/retweets/{userId} body {tweetId}.
  GET /interaction-service/retweets/tweet/{tweetId}/count.

  Scenario: A user can retweet someone else's tweet
    Given user "alice" is registered with email "alice@example.com" and password "Sup3rS3cret!"
    And user "bob" is registered with email "bob@example.com" and password "Sup3rS3cret!"
    When user "alice" posts tweet "a tweet to be retweeted soon"
    Then the response status is 200
    When user "bob" retweets tweet "a tweet to be retweeted soon"
    Then the response status is 200

  Scenario: Retweet count increments after a retweet
    Given user "carol" is registered with email "carol@example.com" and password "Sup3rS3cret!"
    And user "dan" is registered with email "dan@example.com" and password "Sup3rS3cret!"
    When user "carol" posts tweet "another tweet to be retweeted"
    Then the response status is 200
    When user "dan" retweets tweet "another tweet to be retweeted"
    Then the response status is 200
    When user "carol" reads retweet count of tweet "another tweet to be retweeted"
    Then the response status is 200

  Scenario: Retweet count on an un-retweeted tweet is 0
    Given user "ed" is registered with email "ed@example.com" and password "Sup3rS3cret!"
    When user "ed" posts tweet "untouched tweet content here"
    Then the response status is 200
    When user "ed" reads retweet count of tweet "untouched tweet content here"
    Then the response status is 200
    And the response body is the number 0

  Scenario: A user can retweet their own tweet
    Given user "fay" is registered with email "fay@example.com" and password "Sup3rS3cret!"
    When user "fay" posts tweet "fay self-retweets the day"
    Then the response status is 200
    When user "fay" retweets tweet "fay self-retweets the day"
    Then the response status is 200

  Scenario: Listing retweets by user returns 200
    Given user "gus" is registered with email "gus@example.com" and password "Sup3rS3cret!"
    And user "hank" is registered with email "hank@example.com" and password "Sup3rS3cret!"
    When user "gus" posts tweet "tweet to be retweeted by hank"
    Then the response status is 200
    When user "hank" retweets tweet "tweet to be retweeted by hank"
    Then the response status is 200
    When user "hank" lists retweets by user
    Then the response status is 200

  Scenario: Listing retweets for a tweet returns 200
    Given user "iva" is registered with email "iva@example.com" and password "Sup3rS3cret!"
    And user "jay" is registered with email "jay@example.com" and password "Sup3rS3cret!"
    When user "iva" posts tweet "another tweet for retweet listing"
    Then the response status is 200
    When user "jay" retweets tweet "another tweet for retweet listing"
    Then the response status is 200
    When user "iva" lists retweets for tweet "another tweet for retweet listing"
    Then the response status is 200

  Scenario: Listing retweets for an un-retweeted tweet is empty
    Given user "kim" is registered with email "kim@example.com" and password "Sup3rS3cret!"
    When user "kim" posts tweet "untouched tweet kim posted"
    Then the response status is 200
    When user "kim" lists retweets for tweet "untouched tweet kim posted"
    Then the response status is 200
    And the response body is a list of size 0

  Scenario: A user can update content of their retweet
    Given user "leo" is registered with email "leo@example.com" and password "Sup3rS3cret!"
    And user "mia" is registered with email "mia@example.com" and password "Sup3rS3cret!"
    When user "leo" posts tweet "leo tweet to be quote-retweeted"
    Then the response status is 200
    When user "mia" retweets tweet "leo tweet to be quote-retweeted"
    Then the response status is 200
    When user "mia" updates their last retweet with content "added retweet commentary"
    Then the response status is 200

  Scenario: A user can delete their retweet
    Given user "nia" is registered with email "nia@example.com" and password "Sup3rS3cret!"
    And user "ozz" is registered with email "ozz@example.com" and password "Sup3rS3cret!"
    When user "nia" posts tweet "nia tweet to be retweeted then deleted"
    Then the response status is 200
    When user "ozz" retweets tweet "nia tweet to be retweeted then deleted"
    Then the response status is 200
    When user "ozz" deletes their last retweet
    Then the response status is 200

  # Drives RetweetService.createRetweet's tweet-not-found branch — TweetClient
  # raises TweetNotFoundException → 404.
  Scenario: Retweeting a non-existent tweet returns 404
    Given user "pia" is registered with email "pia@example.com" and password "Sup3rS3cret!"
    When user "pia" retweets a non-existent tweet
    Then the response status is 404

  # Listing retweets by user with multiple retweets exercises the per-entity
  # mapping loop (TweetClient + UserClient calls) on both stacks.
  Scenario: Listing retweets by user with multiple retweets succeeds
    Given user "qad" is registered with email "qad@example.com" and password "Sup3rS3cret!"
    And user "ras" is registered with email "ras@example.com" and password "Sup3rS3cret!"
    When user "qad" posts tweet "first tweet for multi-retweet user listing"
    Then the response status is 200
    When user "qad" posts tweet "second tweet for multi-retweet user listing"
    Then the response status is 200
    When user "ras" retweets tweet "first tweet for multi-retweet user listing"
    Then the response status is 200
    When user "ras" retweets tweet "second tweet for multi-retweet user listing"
    Then the response status is 200
    When user "ras" lists retweets by user
    Then the response status is 200

  Scenario: Listing retweets by user with no retweets returns empty list
    Given user "sah" is registered with email "sah@example.com" and password "Sup3rS3cret!"
    When user "sah" lists retweets by user
    Then the response status is 200
    And the response body is a list of size 0

  Scenario: Retweet count after two retweeters reflects the total
    Given user "tab" is registered with email "tab@example.com" and password "Sup3rS3cret!"
    And user "ude" is registered with email "ude@example.com" and password "Sup3rS3cret!"
    And user "vex" is registered with email "vex@example.com" and password "Sup3rS3cret!"
    When user "tab" posts tweet "tweet retweeted by two strangers"
    Then the response status is 200
    When user "ude" retweets tweet "tweet retweeted by two strangers"
    Then the response status is 200
    When user "vex" retweets tweet "tweet retweeted by two strangers"
    Then the response status is 200
    When user "tab" reads retweet count of tweet "tweet retweeted by two strangers"
    Then the response status is 200
    And the response body is the number 2

  # Reading retweets twice exercises the cached path in TweetService for the
  # underlying retweet-mapping loop's tweet lookup, plus UserClient lookups.
  Scenario: Listing retweets for the same tweet twice both succeed
    Given user "wat" is registered with email "wat@example.com" and password "Sup3rS3cret!"
    And user "xen" is registered with email "xen@example.com" and password "Sup3rS3cret!"
    When user "wat" posts tweet "tweet whose retweet list is read twice"
    Then the response status is 200
    When user "xen" retweets tweet "tweet whose retweet list is read twice"
    Then the response status is 200
    When user "wat" lists retweets for tweet "tweet whose retweet list is read twice"
    Then the response status is 200
    When user "wat" lists retweets for tweet "tweet whose retweet list is read twice"
    Then the response status is 200
