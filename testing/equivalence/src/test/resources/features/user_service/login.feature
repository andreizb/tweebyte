Feature: User login
  POST /user-service/auth/login through the gateway. Issues a JWT on success.

  Scenario: Login succeeds with correct credentials
    Given user "alice" is registered with email "alice@example.com" and password "Sup3rS3cret!"
    When a client logs in with email "alice@example.com" and password "Sup3rS3cret!"
    Then the response status is 200
    And the response body contains a JWT token

  Scenario: Login fails with wrong password
    Given user "bob" is registered with email "bob@example.com" and password "Sup3rS3cret!"
    When a client logs in with email "bob@example.com" and password "WrongPassword!"
    Then the response status is 401
    And the response body does not contain a JWT token

  Scenario: Login fails for unknown email
    When a client logs in with email "ghost@example.com" and password "Sup3rS3cret!"
    Then the response status is 401
    And the response body does not contain a JWT token

  Scenario: Login is case-sensitive on email
    Given user "carol" is registered with email "carol@example.com" and password "Sup3rS3cret!"
    When a client logs in with email "Carol@Example.com" and password "Sup3rS3cret!"
    Then the response status is 401
    And the response body does not contain a JWT token

  Scenario: Re-login of an existing user issues a fresh JWT
    Given user "dave" is registered with email "dave@example.com" and password "Sup3rS3cret!"
    When user "dave" logs in
    Then the response status is 200
    And the response body contains a JWT token
