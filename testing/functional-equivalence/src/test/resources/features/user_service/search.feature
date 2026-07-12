Feature: User search
  GET /user-service/users/search/{term} — substring match against user_name.
  Requires Bearer JWT (gateway-enforced; only /auth/* is exempted).

  Scenario: Searching for an existing username returns at least that user
    Given user "alice" is registered with email "alice@example.com" and password "Sup3rS3cret!"
    When user "alice" searches users for "alice"
    Then the response status is 200
    And the response body is a list containing user "alice"

  Scenario: Searching for a fragment of a username matches it
    Given user "alicia" is registered with email "alicia@example.com" and password "Sup3rS3cret!"
    When user "alicia" searches users for "alic"
    Then the response status is 200
    And the response body is a list containing user "alicia"

  Scenario: Searching for a term that matches no one returns an empty list
    Given user "bob" is registered with email "bob@example.com" and password "Sup3rS3cret!"
    When user "bob" searches users for "nobodymatchesthisterm"
    Then the response status is 200
    And the response body is a list of size 0

  Scenario: Searching with multiple matches returns them all
    Given user "alpha1" is registered with email "alpha1@example.com" and password "Sup3rS3cret!"
    And user "alpha2" is registered with email "alpha2@example.com" and password "Sup3rS3cret!"
    When user "alpha1" searches users for "alpha"
    Then the response status is 200
    And the response body is a list of size 2

  Scenario: Searching for a unicode-only fragment returns empty
    Given user "carl" is registered with email "carl@example.com" and password "Sup3rS3cret!"
    When user "carl" searches users for "ñoñoexists"
    Then the response status is 200
    And the response body is a list of size 0
