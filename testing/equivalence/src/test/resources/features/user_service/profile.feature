Feature: User profile read + update
  GET /user-service/users/{id}, GET /user-service/users/summary/{id},
  GET /user-service/users/summary/name/{name}, PUT /user-service/users/{id}.
  All gateway-routed paths require a Bearer JWT — only /auth/* is exempted by
  the gateway's JwtTokenValidationFilter. Internal callers (tweet-service,
  interaction-service) hit user-service directly on port 9091, bypassing the
  gateway and JWT entirely; FE asserts the gateway-routed external behaviour.

  Scenario: An authenticated user can fetch their own profile
    Given user "alice" is registered with email "alice@example.com" and password "Sup3rS3cret!"
    When user "alice" fetches their own profile
    Then the response status is 200
    And the response body has user_name "alice"

  Scenario: A user can fetch another user's profile when authenticated
    Given user "alice" is registered with email "alice@example.com" and password "Sup3rS3cret!"
    And user "bob" is registered with email "bob@example.com" and password "Sup3rS3cret!"
    When user "alice" fetches profile of "bob"
    Then the response status is 200
    And the response body has user_name "bob"

  Scenario: Authenticated client can fetch another user's public summary by id
    Given user "carol" is registered with email "carol@example.com" and password "Sup3rS3cret!"
    And user "dan" is registered with email "dan@example.com" and password "Sup3rS3cret!"
    When user "dan" fetches the public summary of "carol"
    Then the response status is 200
    And the response body has user_name "carol"
    And the response body has no email field

  Scenario: Authenticated client can fetch another user's public summary by name
    Given user "ed" is registered with email "ed@example.com" and password "Sup3rS3cret!"
    And user "fay" is registered with email "fay@example.com" and password "Sup3rS3cret!"
    When user "fay" fetches the public summary of "ed" by name
    Then the response status is 200
    And the response body has user_name "ed"
    And the response body has no email field

  Scenario: A user can update their own biography
    Given user "gus" is registered with email "gus@example.com" and password "Sup3rS3cret!"
    When user "gus" updates their biography to "newly updated biography"
    Then the response status is 204
    When user "gus" fetches their own profile
    Then the response status is 200
    And the response body has biography "newly updated biography"

  Scenario: Fetching a non-existent profile returns 404
    Given user "hank" is registered with email "hank@example.com" and password "Sup3rS3cret!"
    When user "hank" fetches a non-existent profile
    Then the response status is 404

  Scenario: Public summary of a non-existent user returns 404
    Given user "iva" is registered with email "iva@example.com" and password "Sup3rS3cret!"
    When user "iva" fetches the public summary of a non-existent user
    Then the response status is 404

  Scenario: Public summary by name of a non-existent user returns 404
    Given user "jay" is registered with email "jay@example.com" and password "Sup3rS3cret!"
    When user "jay" fetches the public summary by name "thisuserdoesnotexistanywhere"
    Then the response status is 404

  Scenario: Updating biography to unicode succeeds
    Given user "kim" is registered with email "kim@example.com" and password "Sup3rS3cret!"
    When user "kim" updates their biography to "héllo wörld bio with émojis 🚀"
    Then the response status is 204
    When user "kim" fetches their own profile
    Then the response status is 200
    And the response body has biography "héllo wörld bio with émojis 🚀"
