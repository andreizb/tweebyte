Feature: Media download
  GET /user-service/media/ streams a fixed-size payload back to the caller.
  The endpoint is JWT-protected at the gateway (only /auth/* is exempt).

  Scenario: An authenticated user can download the media payload
    Given user "kilo" is registered with email "kilo@example.com" and password "Sup3rS3cret!"
    When user "kilo" downloads media
    Then the response status is 206
    And the response body has a content-length of at least 1024 bytes

  Scenario: Media download is rejected when no JWT is provided
    When a client downloads media without auth
    Then the response status is 401

  Scenario: Two consecutive media downloads both succeed
    Given user "lima" is registered with email "lima@example.com" and password "Sup3rS3cret!"
    When user "lima" downloads media
    Then the response status is 206
    When user "lima" downloads media
    Then the response status is 206
