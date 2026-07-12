Feature: User signup
  Behaviour of POST /user-service/auth/register through the gateway.
  Same .feature file runs unchanged on async and reactive stacks.

  Scenario: Register a new user with valid credentials
    When a client registers user "alice" with email "alice@example.com" and password "Sup3rS3cret!"
    Then the response status is 200
    And the response body contains a JWT token

  # NOTE: user-service currently maps UserAlreadyExistsException to 400, not 409.
  # FE behaviour is identical across stacks — both return 400 with the descriptive errors[] body.
  Scenario: Register fails when username is duplicated
    Given user "bob" is registered with email "bob@example.com" and password "Sup3rS3cret!"
    When a client registers user "bob" with email "bob2@example.com" and password "Sup3rS3cret!"
    Then the response status is 400
    And the response body does not contain a JWT token

  Scenario: Register fails when email is duplicated
    Given user "carol" is registered with email "carol@example.com" and password "Sup3rS3cret!"
    When a client registers user "carol2" with email "carol@example.com" and password "Sup3rS3cret!"
    Then the response status is 400
    And the response body does not contain a JWT token

  Scenario: Register fails when password is too short
    When a client registers user "dave" with email "dave@example.com" and password "short"
    Then the response status is 400
    And the response body does not contain a JWT token

  Scenario: Register accepts a unicode username
    When a client registers user "éléonore" with email "elo@example.com" and password "Sup3rS3cret!"
    Then the response status is 200
    And the response body contains a JWT token

  Scenario: Register accepts a long biography
    When a client registers user "frank" with email "frank@example.com" password "Sup3rS3cret!" biography "Hello world from a moderately long biography that fits comfortably under the 500 character cap that the user-service enforces in its UserRegisterRequest validation rules."
    Then the response status is 200
    And the response body contains a JWT token

  # B1 / AS-1 — AuthenticationService.register profilePictureId branches (3 branches)
  # Also covers UserMapper.mapRequestToEntity profilePictureId != null branch (UM-1 partial).

  Scenario: Register with a non-existent profilePictureId is rejected with 400
    When a client registers user "signup-badpic" with email "signup-badpic@example.com" and password "Sup3rS3cret!" and profilePictureId "00000000-0000-0000-0000-000000000099"
    Then the response status is 400
    And the response body does not contain a JWT token

  Scenario: Register with a valid profilePictureId succeeds
    Given user "signup-prepic" is registered with email "signup-prepic@example.com" and password "Sup3rS3cret!"
    When user "signup-prepic" uploads a 32x32 image
    Then the response status is 200
    And the response body has an id
    When a client registers user "signup-goodpic" with email "signup-goodpic@example.com" and password "Sup3rS3cret!" and profilePictureId from last upload
    Then the response status is 200
    And the response body contains a JWT token
