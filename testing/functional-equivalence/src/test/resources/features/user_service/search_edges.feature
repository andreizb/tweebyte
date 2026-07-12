Feature: User search edge cases
  GET /user-service/users/search/{term} — extended coverage for substring matching
  edge cases: case-insensitive, single-character, suffix, prefix, mixed-case.
  All paths require Bearer JWT (gateway-enforced).

  Scenario: Search is case-insensitive (uppercase term matches lowercase user)
    Given user "ProtoCase" is registered with email "protocase@example.com" and password "Sup3rS3cret!"
    When user "ProtoCase" searches users for "protocase"
    Then the response status is 200
    And the response body is a list containing user "ProtoCase"

  Scenario: Search by suffix fragment matches the user
    Given user "trailingblob" is registered with email "trailing@example.com" and password "Sup3rS3cret!"
    When user "trailingblob" searches users for "blob"
    Then the response status is 200
    And the response body is a list containing user "trailingblob"

  Scenario: Search by prefix fragment matches the user
    Given user "prefixwins" is registered with email "prefixwins@example.com" and password "Sup3rS3cret!"
    When user "prefixwins" searches users for "prefix"
    Then the response status is 200
    And the response body is a list containing user "prefixwins"

  Scenario: Search with a single-character fragment can match
    Given user "zinc" is registered with email "zinc@example.com" and password "Sup3rS3cret!"
    When user "zinc" searches users for "z"
    Then the response status is 200
    And the response body is a list containing user "zinc"

  Scenario: Search of digits-only term against a digit-bearing username matches
    Given user "user42" is registered with email "user42@example.com" and password "Sup3rS3cret!"
    When user "user42" searches users for "42"
    Then the response status is 200
    And the response body is a list containing user "user42"

  Scenario: Search with multiple matches returns all of them
    Given user "shared01" is registered with email "shared01@example.com" and password "Sup3rS3cret!"
    And user "shared02" is registered with email "shared02@example.com" and password "Sup3rS3cret!"
    And user "shared03" is registered with email "shared03@example.com" and password "Sup3rS3cret!"
    When user "shared01" searches users for "shared"
    Then the response status is 200
    And the response body is a list of size 3

  Scenario: Search returns an empty list when no user matches a long random fragment
    Given user "nomatch" is registered with email "nomatch@example.com" and password "Sup3rS3cret!"
    When user "nomatch" searches users for "qzqzqzqzqzqzqzqz"
    Then the response status is 200
    And the response body is a list of size 0

  Scenario: Search rejects requests with no JWT
    When a client searches users for "anyterm"
    Then the response status is 401
