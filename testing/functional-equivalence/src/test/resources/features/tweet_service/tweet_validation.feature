Feature: Tweet content validation
  POST /tweet-service/tweets/{userId} validates `content` (NotBlank, Size min 10).

  Scenario: Posting an empty tweet is rejected with 400
    Given user "alice" is registered with email "alice@example.com" and password "Sup3rS3cret!"
    When user "alice" posts tweet ""
    Then the response status is 400

  Scenario: Posting a too-short tweet is rejected with 400
    Given user "bob" is registered with email "bob@example.com" and password "Sup3rS3cret!"
    When user "bob" posts tweet "hi"
    Then the response status is 400

  Scenario: Posting a tweet at the minimum length succeeds
    Given user "carol" is registered with email "carol@example.com" and password "Sup3rS3cret!"
    When user "carol" posts tweet "ten chars!"
    Then the response status is 200

  Scenario: Posting a long tweet succeeds
    Given user "dan" is registered with email "dan@example.com" and password "Sup3rS3cret!"
    When user "dan" posts tweet "a long tweet about everything that fits inside the unconstrained text body that the tweet service is happy to persist as long as it is at least the minimum length"
    Then the response status is 200

  Scenario: Posting a unicode tweet succeeds
    Given user "ed" is registered with email "ed@example.com" and password "Sup3rS3cret!"
    When user "ed" posts tweet "héllo wörld with émojis 🚀🦄"
    Then the response status is 200

  Scenario: Posting a 9-character tweet (just below min) is rejected with 400
    Given user "fay" is registered with email "fay@example.com" and password "Sup3rS3cret!"
    When user "fay" posts tweet "abcdefghi"
    Then the response status is 400

  Scenario: Updating a tweet to too-short content returns 400
    Given user "gus" is registered with email "gus@example.com" and password "Sup3rS3cret!"
    When user "gus" posts tweet "tweet to be edited shortly"
    Then the response status is 200
    When user "gus" updates tweet "tweet to be edited shortly" content to "hi"
    Then the response status is 400
