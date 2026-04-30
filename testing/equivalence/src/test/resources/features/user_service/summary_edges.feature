Feature: User summary edge cases
  GET /user-service/users/summary/{userId} and /summary/name/{userName} — additional
  edge-case coverage to exercise mapper paths (summary DTOs strip email/birthDate/etc.).

  Scenario: Summary by id returns user_name but no email
    Given user "mike" is registered with email "mike@example.com" and password "Sup3rS3cret!"
    And user "november" is registered with email "november@example.com" and password "Sup3rS3cret!"
    When user "november" fetches the public summary of "mike"
    Then the response status is 200
    And the response body has user_name "mike"
    And the response body has no email field

  Scenario: Summary by name returns user_name but no email
    Given user "oscar" is registered with email "oscar@example.com" and password "Sup3rS3cret!"
    And user "papa" is registered with email "papa@example.com" and password "Sup3rS3cret!"
    When user "papa" fetches the public summary of "oscar" by name
    Then the response status is 200
    And the response body has user_name "oscar"
    And the response body has no email field

  Scenario: Summary by name is case-sensitive (mismatched case yields 404)
    Given user "quebec" is registered with email "quebec@example.com" and password "Sup3rS3cret!"
    And user "romeo" is registered with email "romeo@example.com" and password "Sup3rS3cret!"
    When user "romeo" fetches the public summary by name "QUEBEC"
    Then the response status is 404

  Scenario: Summary by name with empty-string-like garbage yields 404
    Given user "sierra" is registered with email "sierra@example.com" and password "Sup3rS3cret!"
    When user "sierra" fetches the public summary by name "this-name-does-not-exist-anywhere-001"
    Then the response status is 404

  Scenario: After updating own userName, summary by new name resolves
    Given user "tango" is registered with email "tango@example.com" and password "Sup3rS3cret!"
    When user "tango" updates their userName to "tango-renamed"
    Then the response status is 204
    When user "tango" fetches the public summary by name "tango-renamed"
    Then the response status is 200
    And the response body has user_name "tango-renamed"

  Scenario: After updating own userName, the old name no longer resolves
    Given user "uniform" is registered with email "uniform@example.com" and password "Sup3rS3cret!"
    When user "uniform" updates their userName to "uniform-renamed"
    Then the response status is 204
    When user "uniform" fetches the public summary by name "uniform"
    Then the response status is 404
