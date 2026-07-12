Feature: Gateway boundary coverage
  The gateway should consistently separate public documentation routes from
  protected downstream routes before any service-specific controller runs.

  Scenario: User-service OpenAPI document is public
    When a client GETs "/user-service/v3/api-docs" without auth
    Then the response status is 200

  Scenario: Interaction-service OpenAPI document is public
    When a client GETs "/interaction-service/v3/api-docs" without auth
    Then the response status is 200

  Scenario: User profile collection is protected
    When a client GETs "/user-service/users" without auth
    Then the response status is 401

  Scenario: User search is protected
    When a client GETs "/user-service/users/search?query=any" without auth
    Then the response status is 401

  Scenario: Tweet search is protected
    When a client GETs "/tweet-service/tweets/search?query=any" without auth
    Then the response status is 401

  Scenario: Tweet fetch is protected
    When a client GETs "/tweet-service/tweets/00000000-0000-0000-0000-000000000000" without auth
    Then the response status is 401

  Scenario: Tweet summary is protected
    When a client GETs "/tweet-service/tweets/00000000-0000-0000-0000-000000000000/summary" without auth
    Then the response status is 401

  Scenario: Hashtag search is protected
    When a client GETs "/tweet-service/tweets/hashtag/notag" without auth
    Then the response status is 401

  Scenario: Followers list is protected
    When a client GETs "/interaction-service/follows/00000000-0000-0000-0000-000000000000/followers" without auth
    Then the response status is 401

  Scenario: Following list is protected
    When a client GETs "/interaction-service/follows/00000000-0000-0000-0000-000000000000/following" without auth
    Then the response status is 401

  Scenario: Like count is protected
    When a client GETs "/interaction-service/likes/00000000-0000-0000-0000-000000000000/count" without auth
    Then the response status is 401

  Scenario: Retweet count is protected
    When a client GETs "/interaction-service/retweets/00000000-0000-0000-0000-000000000000/count" without auth
    Then the response status is 401

  Scenario: Reply count is protected
    When a client GETs "/interaction-service/replies/00000000-0000-0000-0000-000000000000/count" without auth
    Then the response status is 401

  Scenario: User recommendations are protected
    When a client GETs "/interaction-service/recommendations/users/00000000-0000-0000-0000-000000000000?limit=5" without auth
    Then the response status is 401
