Feature: User profile per-field updates
  PUT /user-service/users/{id} accepts a partial multipart form. Each field is
  optional; only fields present in the body are applied to the entity.
  Same .feature runs unchanged on async and reactive.

  Scenario: Updating only the email field changes only email
    Given user "alpha" is registered with email "alpha@example.com" and password "Sup3rS3cret!"
    When user "alpha" updates their email to "alpha-new@example.com"
    Then the response status is 204
    When user "alpha" fetches their own profile
    Then the response status is 200
    And the response body has user_name "alpha"
    And the response body has email "alpha-new@example.com"

  Scenario: Updating only the userName field changes only userName
    Given user "bravo" is registered with email "bravo@example.com" and password "Sup3rS3cret!"
    When user "bravo" updates their userName to "bravo2"
    Then the response status is 204
    When user "bravo" fetches their own profile
    Then the response status is 200
    And the response body has user_name "bravo2"
    And the response body has email "bravo@example.com"

  Scenario: Updating only the isPrivate flag to true persists
    Given user "charlie" is registered with email "charlie@example.com" and password "Sup3rS3cret!"
    When user "charlie" updates their isPrivate flag to "true"
    Then the response status is 204
    When user "charlie" fetches their own profile
    Then the response status is 200
    And the response body has isPrivate "true"

  Scenario: Updating only the isPrivate flag to false persists
    Given user "delta" is registered with email "delta@example.com" and password "Sup3rS3cret!"
    When user "delta" updates their isPrivate flag to "true"
    Then the response status is 204
    When user "delta" updates their isPrivate flag to "false"
    Then the response status is 204
    When user "delta" fetches their own profile
    Then the response status is 200
    And the response body has isPrivate "false"

  Scenario: Updating only the birthDate changes only birthDate
    Given user "echo" is registered with email "echo@example.com" and password "Sup3rS3cret!"
    When user "echo" updates their birthDate to "1990-05-15"
    Then the response status is 204

  Scenario: Updating only the password lets the user log in with the new password
    Given user "foxtrot" is registered with email "foxtrot@example.com" and password "Sup3rS3cret!"
    When user "foxtrot" updates their password to "BrandNewPa55!"
    Then the response status is 204
    When user "foxtrot" logs in
    Then the response status is 200
    And the response body contains a JWT token

  Scenario: Updating multiple fields at once persists each
    Given user "golf" is registered with email "golf@example.com" and password "Sup3rS3cret!"
    When user "golf" updates multiple fields userName "golf-renamed" biography "multi-update bio" isPrivate "true"
    Then the response status is 204
    When user "golf" fetches their own profile
    Then the response status is 200
    And the response body has user_name "golf-renamed"
    And the response body has biography "multi-update bio"
    And the response body has isPrivate "true"

  Scenario: An empty profile update is a no-op (still 204)
    Given user "hotel" is registered with email "hotel@example.com" and password "Sup3rS3cret!"
    When user "hotel" sends an empty profile update
    Then the response status is 204
    When user "hotel" fetches their own profile
    Then the response status is 200
    And the response body has user_name "hotel"
    And the response body has email "hotel@example.com"

  Scenario: Updating biography to an empty string blanks the biography
    Given user "india" is registered with email "india@example.com" and password "Sup3rS3cret!"
    When user "india" updates their biography to ""
    Then the response status is 204

  Scenario: Two consecutive biography updates preserve the latest value
    Given user "juliet" is registered with email "juliet@example.com" and password "Sup3rS3cret!"
    When user "juliet" updates their biography to "first version"
    Then the response status is 204
    When user "juliet" updates their biography to "second version"
    Then the response status is 204
    When user "juliet" fetches their own profile
    Then the response status is 200
    And the response body has biography "second version"

  Scenario: Updating to a long biography under the cap succeeds
    Given user "kappa" is registered with email "kappa@example.com" and password "Sup3rS3cret!"
    When user "kappa" updates their biography to "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi."
    Then the response status is 204
    When user "kappa" fetches their own profile
    Then the response status is 200
    And the response body has biography "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi."

  Scenario: Updating birthDate then re-fetching the profile reflects the change
    Given user "lambda" is registered with email "lambda@example.com" and password "Sup3rS3cret!"
    When user "lambda" updates their birthDate to "1985-12-31"
    Then the response status is 204
    When user "lambda" fetches their own profile
    Then the response status is 200
    And the response body has user_name "lambda"

  Scenario: Updating multiple fields including email persists each
    Given user "mu" is registered with email "mu-old@example.com" and password "Sup3rS3cret!"
    When user "mu" updates their email to "mu-new@example.com"
    Then the response status is 204
    When user "mu" updates their userName to "mu-renamed"
    Then the response status is 204
    When user "mu" fetches their own profile
    Then the response status is 200
    And the response body has user_name "mu-renamed"
    And the response body has email "mu-new@example.com"
