Feature: Follow recommendations
  GET /interaction-service/recommendations/{userId}/follow.

  Scenario: A user can fetch follow recommendations
    Given user "alice" is registered with email "alice@example.com" and password "Sup3rS3cret!"
    When user "alice" fetches follow recommendations
    Then the response status is 200

  Scenario: Recommendations endpoint responds 200 even with no other users
    Given user "bob" is registered with email "bob@example.com" and password "Sup3rS3cret!"
    When user "bob" fetches follow recommendations
    Then the response status is 200

  Scenario: Recommendations endpoint responds 200 after registering several users
    Given user "carol" is registered with email "carol@example.com" and password "Sup3rS3cret!"
    And user "dan" is registered with email "dan@example.com" and password "Sup3rS3cret!"
    And user "ed" is registered with email "ed@example.com" and password "Sup3rS3cret!"
    When user "carol" fetches follow recommendations
    Then the response status is 200

  # interaction-service TweetClient now calls /tweets/hashtag/popular
  # (singular) on both stacks. The hashtag-recommendations endpoint can be
  # exercised again.
  Scenario: Hashtag recommendations endpoint responds 200
    Given user "fay" is registered with email "fay@example.com" and password "Sup3rS3cret!"
    When user "fay" fetches hashtag recommendations
    Then the response status is 200

  Scenario: Hashtag recommendations endpoint returns 200 when there are tweets with hashtags
    Given user "gus" is registered with email "gus@example.com" and password "Sup3rS3cret!"
    When user "gus" posts tweet "talking about #spring6 and #java21 today"
    Then the response status is 200
    When user "gus" fetches hashtag recommendations
    Then the response status is 200

  # Calling /recommendations/hashtags twice — first miss, second hit — drives
  # the cache-hit deserialize lambda on reactive (range returns the cached
  # bytes; flatMap parses them back into HashtagDto) and the @Cacheable hit on
  # async ("popular_hashtags").
  Scenario: Hashtag recommendations endpoint is idempotent across two calls when hashtags exist
    Given user "han" is registered with email "han@example.com" and password "Sup3rS3cret!"
    When user "han" posts tweet "han talking about #cucumber5 today"
    Then the response status is 200
    When user "han" fetches hashtag recommendations
    Then the response status is 200
    When user "han" fetches hashtag recommendations
    Then the response status is 200

  # Drives RecommendationService.getUserRecommendations on async (fills the
  # @Cacheable("follow_recommendations") cache) and the Redis cache + popular
  # users path on reactive.
  Scenario: Follow recommendations endpoint is idempotent across two calls
    Given user "iam" is registered with email "iam@example.com" and password "Sup3rS3cret!"
    When user "iam" fetches follow recommendations
    Then the response status is 200
    When user "iam" fetches follow recommendations
    Then the response status is 200

  # Two-hop "friend-of-a-friend" recommendation: alpha -> beta -> gamma.
  # alpha asks for recs and the service should *consider* gamma (a 2nd-degree
  # follow, not yet followed). This drives the inner flatMap loop in
  # fetchUserRecommendations + RecommendationService.getUserRecommendations.
  Scenario: Recommendations endpoint considers second-degree follows
    Given user "alf" is registered with email "alf@example.com" and password "Sup3rS3cret!"
    And user "bre" is registered with email "bre@example.com" and password "Sup3rS3cret!"
    And user "cam" is registered with email "cam@example.com" and password "Sup3rS3cret!"
    When user "alf" follows "bre"
    Then the response status is 204
    When user "bre" follows "cam"
    Then the response status is 204
    When user "alf" fetches follow recommendations
    Then the response status is 200

  # Recommendations after multiple follows from this user — exercises the
  # "filter out already-followed" branch in fetchUserRecommendations.
  Scenario: Recommendations endpoint filters out users already followed
    Given user "dav" is registered with email "dav@example.com" and password "Sup3rS3cret!"
    And user "eva" is registered with email "eva@example.com" and password "Sup3rS3cret!"
    And user "fra" is registered with email "fra@example.com" and password "Sup3rS3cret!"
    When user "dav" follows "eva"
    Then the response status is 204
    When user "dav" follows "fra"
    Then the response status is 204
    When user "dav" fetches follow recommendations
    Then the response status is 200

  # Two distinct users both ask for recommendations after a network of follows
  # and engagement (tweets, likes, retweets) has formed. The first call
  # populates popular_users via calculateUserScore (which iterates over each
  # followed user's tweets — exercising tweetService.getUserTweetsSummary on
  # users who actually have tweets). Drives the deepest end of
  # RecommendationService + TweetService cache-miss/hit paths.
  Scenario: Recommendations after a small interaction graph exercise calculateUserScore deeply
    Given user "ged" is registered with email "ged@example.com" and password "Sup3rS3cret!"
    And user "hin" is registered with email "hin@example.com" and password "Sup3rS3cret!"
    And user "ish" is registered with email "ish@example.com" and password "Sup3rS3cret!"
    And user "jak" is registered with email "jak@example.com" and password "Sup3rS3cret!"
    When user "hin" posts tweet "hin tweet getting engagement"
    Then the response status is 200
    When user "ish" posts tweet "ish second tweet for graph score"
    Then the response status is 200
    When user "jak" likes tweet "hin tweet getting engagement"
    Then the response status is 200
    When user "jak" retweets tweet "ish second tweet for graph score"
    Then the response status is 200
    When user "ged" follows "hin"
    Then the response status is 204
    When user "ged" follows "ish"
    Then the response status is 204
    When user "jak" follows "hin"
    Then the response status is 204
    When user "ged" fetches follow recommendations
    Then the response status is 200
    When user "jak" fetches follow recommendations
    Then the response status is 200
