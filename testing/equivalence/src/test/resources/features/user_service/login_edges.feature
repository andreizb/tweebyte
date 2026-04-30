Feature: Login edge cases
  POST /user-service/auth/login — extended scenarios to exercise the auth path:
  password-mismatch loop, repeated successful logins, and post-update logins.

  Scenario: Login with empty email and password returns 400 (validation error)
    When a client logs in with email "" and password ""
    Then the response status is 400
    And the response body does not contain a JWT token

  Scenario: Login with malformed email returns 400 (validation error)
    When a client logs in with email "not-an-email" and password "Sup3rS3cret!"
    Then the response status is 400
    And the response body does not contain a JWT token

  Scenario: After password update, the old password no longer authenticates
    Given user "victor" is registered with email "victor@example.com" and password "Sup3rS3cret!"
    When user "victor" updates their password to "RotatedPa55!"
    Then the response status is 204
    When a client logs in with email "victor@example.com" and password "Sup3rS3cret!"
    Then the response status is 401

  Scenario: After password update, the new password authenticates
    Given user "whiskey" is registered with email "whiskey@example.com" and password "Sup3rS3cret!"
    When user "whiskey" updates their password to "BrandNewKey9!"
    Then the response status is 204
    When a client logs in with email "whiskey@example.com" and password "BrandNewKey9!"
    Then the response status is 200
    And the response body contains a JWT token

  Scenario: Three failed login attempts each return 401 (no lockout, no token)
    Given user "xray" is registered with email "xray@example.com" and password "Sup3rS3cret!"
    When a client logs in with email "xray@example.com" and password "wrong1"
    Then the response status is 401
    When a client logs in with email "xray@example.com" and password "wrong2"
    Then the response status is 401
    When a client logs in with email "xray@example.com" and password "wrong3"
    Then the response status is 401
    And the response body does not contain a JWT token
