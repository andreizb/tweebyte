Feature: Authentication errors at the gateway
  All non-/login and non-/register requests must carry a valid Bearer JWT.
  These scenarios go through the gateway's JwtTokenValidationFilter.

  Scenario: Profile fetch is rejected when no Authorization header is provided
    Given user "alice" is registered with email "alice@example.com" and password "Sup3rS3cret!"
    When a client fetches the profile of "alice" without auth
    Then the response status is 401

  Scenario: Profile fetch is rejected when token is malformed
    Given user "alice" is registered with email "alice@example.com" and password "Sup3rS3cret!"
    When a client fetches the profile of "alice" with token "not.a.real.jwt"
    Then the response status is 401

  Scenario: Profile fetch is rejected when token is empty
    Given user "alice" is registered with email "alice@example.com" and password "Sup3rS3cret!"
    When a client fetches the profile of "alice" with token ""
    Then the response status is 401

  Scenario: Profile fetch is rejected when token has only a prefix
    Given user "alice" is registered with email "alice@example.com" and password "Sup3rS3cret!"
    When a client fetches the profile of "alice" with token "abcdef"
    Then the response status is 401

  Scenario: Login does NOT require an Authorization header
    Given user "bob" is registered with email "bob@example.com" and password "Sup3rS3cret!"
    When a client logs in with email "bob@example.com" and password "Sup3rS3cret!"
    Then the response status is 200

  Scenario: Register does NOT require an Authorization header
    When a client registers user "carol" with email "carol@example.com" and password "Sup3rS3cret!"
    Then the response status is 200
