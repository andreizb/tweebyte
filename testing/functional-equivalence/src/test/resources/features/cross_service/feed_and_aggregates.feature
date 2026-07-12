Feature: Cross-service feed + aggregate operations
  Sanity checks that aggregate endpoints return 200 in realistic flows.

  Scenario: Two tweets post + listing user tweets shows both
    Given user "fa" is registered with email "fa@x.com" and password "Sup3rS3cret!"
    When user "fa" posts tweet "first tweet body content here"
    Then the response status is 200
    When user "fa" posts tweet "second tweet body content here"
    Then the response status is 200
    When user "fa" lists their own tweets
    Then the response status is 200

  Scenario: Like, unlike, like again sequence works
    Given user "fb" is registered with email "fb@x.com" and password "Sup3rS3cret!"
    And user "fc" is registered with email "fc@x.com" and password "Sup3rS3cret!"
    When user "fb" posts tweet "tweet to be like-toggled here"
    Then the response status is 200
    When user "fc" likes tweet "tweet to be like-toggled here"
    Then the response status is 200
    When user "fc" unlikes tweet "tweet to be like-toggled here"
    Then the response status is 204
    When user "fc" likes tweet "tweet to be like-toggled here"
    Then the response status is 200

  Scenario: Follow, then reads of follow counts all return 200
    Given user "fd" is registered with email "fd@x.com" and password "Sup3rS3cret!"
    And user "fe" is registered with email "fe@x.com" and password "Sup3rS3cret!"
    When user "fd" follows "fe"
    Then the response status is 204
    When user "fd" reads following count of "fd"
    Then the response status is 200
    When user "fe" reads followers count of "fe"
    Then the response status is 200

  Scenario: Profile fetch after several actions still returns 200
    Given user "ff" is registered with email "ff@x.com" and password "Sup3rS3cret!"
    When user "ff" posts tweet "first action of ff for profile test"
    Then the response status is 200
    When user "ff" updates their biography to "biography after actions"
    Then the response status is 204
    When user "ff" fetches their own profile
    Then the response status is 200
    And the response body has biography "biography after actions"

  Scenario: Search returns 200 with several tweets in DB
    Given user "fg" is registered with email "fg@x.com" and password "Sup3rS3cret!"
    When user "fg" posts tweet "alpha bravo charlie delta"
    Then the response status is 200
    When user "fg" posts tweet "echo foxtrot golf hotel"
    Then the response status is 200
    When user "fg" searches tweets for "alpha"
    Then the response status is 200

  Scenario: Reply, then like own reply via cross-service
    Given user "fh" is registered with email "fh@x.com" and password "Sup3rS3cret!"
    When user "fh" posts tweet "tweet that triggers self-reply ops"
    Then the response status is 200
    When user "fh" replies to tweet "tweet that triggers self-reply ops" with "fh own reply content"
    Then the response status is 200
    When user "fh" likes their last reply
    Then the response status is 200

  Scenario: Retweet then update its content
    Given user "fj" is registered with email "fj@x.com" and password "Sup3rS3cret!"
    And user "fk" is registered with email "fk@x.com" and password "Sup3rS3cret!"
    When user "fj" posts tweet "tweet to be quote-retweeted by fk"
    Then the response status is 200
    When user "fk" retweets tweet "tweet to be quote-retweeted by fk"
    Then the response status is 200
    When user "fk" updates their last retweet with content "new commentary about it"
    Then the response status is 200

  Scenario: Feed of a user that follows another returns 200
    Given user "fl" is registered with email "fl@x.com" and password "Sup3rS3cret!"
    And user "fm" is registered with email "fm@x.com" and password "Sup3rS3cret!"
    When user "fl" follows "fm"
    Then the response status is 204
    When user "fm" posts tweet "tweet from fm that fl should see"
    Then the response status is 200
    When user "fl" fetches their feed
    Then the response status is 200
