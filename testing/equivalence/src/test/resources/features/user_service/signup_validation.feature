Feature: User signup validation errors
  Extended validation coverage for POST /user-service/auth/register.
  All variants must yield 400 with no JWT in the body, on both stacks.

  Scenario: Register fails when password is missing
    When a client registers user "missingpw" with email "missingpw@example.com" and password ""
    Then the response status is 400
    And the response body does not contain a JWT token

  Scenario: Register fails when password is exactly 7 characters (one short of minimum)
    When a client registers user "shortpw" with email "shortpw@example.com" and password "1234567"
    Then the response status is 400
    And the response body does not contain a JWT token

  Scenario: Register succeeds when password is exactly 8 characters (boundary)
    When a client registers user "edgepw" with email "edgepw@example.com" and password "12345678"
    Then the response status is 200
    And the response body contains a JWT token

  Scenario: Register fails when userName is blank
    When a client registers user "" with email "blankname@example.com" and password "Sup3rS3cret!"
    Then the response status is 400
    And the response body does not contain a JWT token

  Scenario: Register fails when email is malformed
    When a client registers user "badmail" with email "not-an-email" and password "Sup3rS3cret!"
    Then the response status is 400
    And the response body does not contain a JWT token

  Scenario: Register accepts a multi-byte unicode biography
    When a client registers user "uniBio" with email "unibio@example.com" password "Sup3rS3cret!" biography "بسم الله 🌍 中文 русский"
    Then the response status is 200
    And the response body contains a JWT token

  Scenario: Register and immediately fetching the profile returns the registered details
    Given user "fresh1" is registered with email "fresh1@example.com" and password "Sup3rS3cret!"
    When user "fresh1" fetches their own profile
    Then the response status is 200
    And the response body has user_name "fresh1"
    And the response body has email "fresh1@example.com"
