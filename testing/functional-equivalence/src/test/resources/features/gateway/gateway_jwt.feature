Feature: Gateway JWT enforcement
  The gateway's JwtTokenValidationFilter runs before routing on every path
  except the two exact public paths /user-service/auth/login and
  /user-service/auth/register. Matching is exact (not suffix), so a path that
  merely ends in /login or /register is still challenged.

  Scenario: Gateway exempts /auth/register from JWT
    When a client registers user "jwt1" with email "jwt1@x.com" and password "Sup3rS3cret!"
    Then the response status is 200

  Scenario: Gateway exempts /auth/login from JWT
    Given user "jwt2" is registered with email "jwt2@x.com" and password "Sup3rS3cret!"
    When a client logs in with email "jwt2@x.com" and password "Sup3rS3cret!"
    Then the response status is 200

  Scenario: Gateway rejects unauthenticated profile fetch with 401
    Given user "jwt3" is registered with email "jwt3@x.com" and password "Sup3rS3cret!"
    When a client fetches the profile of "jwt3" without auth
    Then the response status is 401

  Scenario: Gateway rejects malformed token with 401
    Given user "jwt4" is registered with email "jwt4@x.com" and password "Sup3rS3cret!"
    When a client fetches the profile of "jwt4" with token "not.a.real.jwt"
    Then the response status is 401

  Scenario: Gateway rejects empty bearer with 401
    Given user "jwt5" is registered with email "jwt5@x.com" and password "Sup3rS3cret!"
    When a client fetches the profile of "jwt5" with token ""
    Then the response status is 401

  Scenario: Gateway accepts a valid JWT issued by user-service
    Given user "jwt6" is registered with email "jwt6@x.com" and password "Sup3rS3cret!"
    When user "jwt6" fetches their own profile
    Then the response status is 200

  Scenario: A path that merely ends in /login is NOT exempt from JWT
    When a client GETs "/tweet-service/tweets/search/login" without auth
    Then the response status is 401

  Scenario: Gateway exposes the aggregated OpenAPI document without JWT
    When a client GETs "/v3/api-docs" without auth
    Then the response status is 200

  Scenario: Gateway exposes Swagger UI without JWT
    When a client GETs "/swagger-ui.html" without auth
    Then the response status is 200

  Scenario: Gateway exposes Swagger config without JWT
    When a client GETs "/v3/api-docs/swagger-config" without auth
    Then the response status is 200

  Scenario: Gateway exposes a downstream OpenAPI document without JWT
    When a client GETs "/tweet-service/v3/api-docs" without auth
    Then the response status is 200

  Scenario: A downstream-looking docs suffix is NOT exempt from JWT
    When a client GETs "/tweet-service/tweets/search/v3/api-docs" without auth
    Then the response status is 401

  Scenario: Public login tolerates a bearer token without JWT segments
    Given user "jwt7" is registered with email "jwt7@x.com" and password "Sup3rS3cret!"
    When a client logs in with email "jwt7@x.com" and password "Sup3rS3cret!" using token "abcdef"
    Then the response status is 200

  Scenario: Public login tolerates a bearer token without a subject claim
    Given user "jwt8" is registered with email "jwt8@x.com" and password "Sup3rS3cret!"
    When a client logs in with email "jwt8@x.com" and password "Sup3rS3cret!" using token "aaa.eyJub3QiOiJzdWIifQ.ccc"
    Then the response status is 200

  Scenario: Public login tolerates a bearer token with a malformed subject value
    Given user "jwt9" is registered with email "jwt9@x.com" and password "Sup3rS3cret!"
    When a client logs in with email "jwt9@x.com" and password "Sup3rS3cret!" using token "aaa.eyJzdWIiOn0.ccc"
    Then the response status is 200

  Scenario: Public login tolerates a bearer token with invalid base64 payload
    Given user "jwt10" is registered with email "jwt10@x.com" and password "Sup3rS3cret!"
    When a client logs in with email "jwt10@x.com" and password "Sup3rS3cret!" using token "aaa.!.ccc"
    Then the response status is 200
