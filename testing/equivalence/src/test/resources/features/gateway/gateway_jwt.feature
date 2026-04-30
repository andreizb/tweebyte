Feature: Gateway JWT enforcement
  The gateway's JwtTokenValidationFilter runs before routing on every path
  except /auth/login and /auth/register (the only exemptions hardcoded in
  the filter's `shouldFilter()`).

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
