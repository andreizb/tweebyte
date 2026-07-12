Feature: Gateway routing
  Requests to /user-service/**, /tweet-service/**, /interaction-service/**
  are routed to the matching downstream service. Async uses Zuul, reactive
  uses Spring Cloud Gateway — same .feature file, both should pass.

  Scenario: Gateway routes /user-service/auth/register to user-service
    When a client registers user "ralpha" with email "ralpha@x.com" and password "Sup3rS3cret!"
    Then the response status is 200
    And the response body contains a JWT token

  Scenario: Gateway routes /user-service/auth/login to user-service
    Given user "rbeta" is registered with email "rbeta@x.com" and password "Sup3rS3cret!"
    When user "rbeta" logs in
    Then the response status is 200
    And the response body contains a JWT token

  Scenario: Gateway routes /tweet-service/tweets/{id}/feed to tweet-service
    Given user "rgamma" is registered with email "rgamma@x.com" and password "Sup3rS3cret!"
    When user "rgamma" lists their own tweets
    Then the response status is 200

  Scenario: Gateway routes /interaction-service/follows/{id}/followers/count to interaction-service
    Given user "rdelta" is registered with email "rdelta@x.com" and password "Sup3rS3cret!"
    When user "rdelta" reads followers count of "rdelta"
    Then the response status is 200

  Scenario: Gateway routes /interaction-service/likes/{id}/count to interaction-service
    Given user "repsilon" is registered with email "repsilon@x.com" and password "Sup3rS3cret!"
    When user "repsilon" posts tweet "tweet for routing test of likes count"
    Then the response status is 200
    When user "repsilon" reads like count of tweet "tweet for routing test of likes count"
    Then the response status is 200
