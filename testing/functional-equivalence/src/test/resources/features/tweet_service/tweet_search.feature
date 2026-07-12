Feature: Tweet search + hashtag listing
  GET /tweet-service/tweets/search/{term}, GET /tweet-service/tweets/search/hashtag/{tag},
  GET /tweet-service/tweets/hashtag/popular.

  Scenario: Search for a unique substring matches the posted tweet
    Given user "alice" is registered with email "alice@example.com" and password "Sup3rS3cret!"
    When user "alice" posts tweet "uniquemarker zzzphrase one two"
    Then the response status is 200
    When user "alice" searches tweets for "uniquemarker"
    Then the response status is 200

  Scenario: Search by hashtag matches a posted tweet that uses it
    Given user "bob" is registered with email "bob@example.com" and password "Sup3rS3cret!"
    When user "bob" posts tweet "fluently learning #java18 today"
    Then the response status is 200
    When user "bob" searches tweets by hashtag "java18"
    Then the response status is 200

  Scenario: Popular hashtags endpoint responds 200
    Given user "carol" is registered with email "carol@example.com" and password "Sup3rS3cret!"
    When user "carol" fetches popular hashtags
    Then the response status is 200

  Scenario: Searching for an unmatched term returns an empty list
    Given user "dan" is registered with email "dan@example.com" and password "Sup3rS3cret!"
    When user "dan" searches tweets for "nothingmatchesthistermxyz"
    Then the response status is 200
    And the response body is a list of size 0

  Scenario: Searching by an unused hashtag returns an empty list
    Given user "ed" is registered with email "ed@example.com" and password "Sup3rS3cret!"
    When user "ed" searches tweets by hashtag "definitelynottweetedyetabc"
    Then the response status is 200
    And the response body is a list of size 0

  Scenario: Hashtag search after a tweet with that exact hashtag returns 200
    # Exercises searchTweetsByHashtag → mapEntityToDto path with userService.getUserSummary
    # call (covers TweetService private mapEntityToDto + UserService).
    Given user "fay" is registered with email "fay@example.com" and password "Sup3rS3cret!"
    When user "fay" posts tweet "deploying with #kubernetes today is fine"
    Then the response status is 200
    When user "fay" searches tweets by hashtag "kubernetes"
    Then the response status is 200

  Scenario: Substring search after a posted tweet returns 200
    # Exercises searchTweets → mapEntityToDto → userService.getUserSummary path.
    Given user "gus" is registered with email "gus@example.com" and password "Sup3rS3cret!"
    When user "gus" posts tweet "yet another tweet about supercalifragilistic"
    Then the response status is 200
    When user "gus" searches tweets for "supercalifragilistic"
    Then the response status is 200

  Scenario: Popular hashtags after several tagged tweets returns 200
    Given user "hank" is registered with email "hank@example.com" and password "Sup3rS3cret!"
    When user "hank" posts tweet "first try with #poptag inside content"
    Then the response status is 200
    When user "hank" posts tweet "again with #poptag mentioned twice now"
    Then the response status is 200
    When user "hank" fetches popular hashtags
    Then the response status is 200

  Scenario: Searching with a single character returns 200
    Given user "iva" is registered with email "iva@example.com" and password "Sup3rS3cret!"
    When user "iva" searches tweets for "a"
    Then the response status is 200

  Scenario: Searching with a unicode term returns 200
    Given user "jay" is registered with email "jay@example.com" and password "Sup3rS3cret!"
    When user "jay" searches tweets for "héllo"
    Then the response status is 200
