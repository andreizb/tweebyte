Feature: End-to-end cross-service flows
  Multi-step scenarios that touch user-service, tweet-service AND
  interaction-service through the gateway, exercising JWT propagation +
  inter-service HTTP under realistic flow.

  Scenario: Sign up, post a tweet, like it from another user
    Given user "ealice" is registered with email "ealice@x.com" and password "Sup3rS3cret!"
    And user "ebob" is registered with email "ebob@x.com" and password "Sup3rS3cret!"
    When user "ealice" posts tweet "alice cross-service tweet here"
    Then the response status is 200
    When user "ebob" likes tweet "alice cross-service tweet here"
    Then the response status is 200
    When user "ealice" reads like count of tweet "alice cross-service tweet here"
    Then the response status is 200

  Scenario: Sign up, follow another user, count reflects it
    Given user "ecarol" is registered with email "ecarol@x.com" and password "Sup3rS3cret!"
    And user "edan" is registered with email "edan@x.com" and password "Sup3rS3cret!"
    When user "ecarol" follows "edan"
    Then the response status is 204
    When user "ecarol" reads following count of "ecarol"
    Then the response status is 200

  Scenario: Sign up, post a tweet, retweet from another user
    Given user "eed" is registered with email "eed@x.com" and password "Sup3rS3cret!"
    And user "efay" is registered with email "efay@x.com" and password "Sup3rS3cret!"
    When user "eed" posts tweet "eed cross-service tweet to retweet"
    Then the response status is 200
    When user "efay" retweets tweet "eed cross-service tweet to retweet"
    Then the response status is 200
    When user "eed" reads retweet count of tweet "eed cross-service tweet to retweet"
    Then the response status is 200

  Scenario: Sign up, post a tweet, reply from another user
    Given user "egus" is registered with email "egus@x.com" and password "Sup3rS3cret!"
    And user "ehank" is registered with email "ehank@x.com" and password "Sup3rS3cret!"
    When user "egus" posts tweet "egus cross-service tweet for reply"
    Then the response status is 200
    When user "ehank" replies to tweet "egus cross-service tweet for reply" with "ehanks reply content here"
    Then the response status is 200
    When user "egus" reads reply count of tweet "egus cross-service tweet for reply"
    Then the response status is 200

  Scenario: Sign up, fetch own profile, recommendations all reachable
    Given user "eiva" is registered with email "eiva@x.com" and password "Sup3rS3cret!"
    When user "eiva" fetches their own profile
    Then the response status is 200
    When user "eiva" fetches follow recommendations
    Then the response status is 200

  Scenario: Sign up, post a tweet, search for it via tweet-service
    Given user "ejay" is registered with email "ejay@x.com" and password "Sup3rS3cret!"
    When user "ejay" posts tweet "uniquephrase yz_marker_123 in body"
    Then the response status is 200
    When user "ejay" searches tweets for "uniquephrase"
    Then the response status is 200

  Scenario: Two users interact: follow + post + like flow
    Given user "ekai" is registered with email "ekai@x.com" and password "Sup3rS3cret!"
    And user "elaz" is registered with email "elaz@x.com" and password "Sup3rS3cret!"
    When user "ekai" follows "elaz"
    Then the response status is 204
    When user "elaz" posts tweet "tweet from elaz to be liked"
    Then the response status is 200
    When user "ekai" likes tweet "tweet from elaz to be liked"
    Then the response status is 200
