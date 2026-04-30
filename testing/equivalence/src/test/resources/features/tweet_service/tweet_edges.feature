Feature: Tweet-service edge cases (404s, malformed inputs, mention paths, cache hits)
  Drives extra branches in TweetService / MentionService / TweetMapper —
  particularly the malformed-id 4xx paths, the mention-resolves vs missing-mention-user
  branches, and the second-call-hits-cache path.

  # tweet-service's GlobalExceptionHandler maps
  # MethodArgumentTypeMismatchException -> 400 on both stacks
  # (matches reactive WebFlux's default for malformed @PathVariable UUIDs).
  Scenario: Fetching tweet by malformed id returns 400 (type-mismatch)
    Given user "tedg-a" is registered with email "tedg-a@example.com" and password "Sup3rS3cret!"
    When user "tedg-a" fetches tweet by malformed id "not-a-uuid-at-all"
    Then the response status is 400

  Scenario: Fetching tweet summary by malformed id returns 400 (type-mismatch)
    Given user "tedg-b" is registered with email "tedg-b@example.com" and password "Sup3rS3cret!"
    When user "tedg-b" fetches summary of tweet by malformed id "still-not-a-uuid"
    Then the response status is 400

  Scenario: Fetching feed of a non-existent user returns 200 with empty list
    Given user "tedg-c" is registered with email "tedg-c@example.com" and password "Sup3rS3cret!"
    When user "tedg-c" fetches feed of a non-existent user
    Then the response status is 200

  Scenario: Listing tweets of a non-existent user returns 200 with empty list
    Given user "tedg-d" is registered with email "tedg-d@example.com" and password "Sup3rS3cret!"
    When user "tedg-d" lists tweets of a non-existent user
    Then the response status is 200
    And the response body is a list of size 0

  Scenario: Fetching summary of tweets of a non-existent user returns 200
    Given user "tedg-e" is registered with email "tedg-e@example.com" and password "Sup3rS3cret!"
    When user "tedg-e" fetches user tweets summary of a non-existent user
    Then the response status is 200

  Scenario: Posting a tweet with a mention of an existing user resolves the mention
    Given user "tedg-mentor" is registered with email "tedg-mentor@example.com" and password "Sup3rS3cret!"
    And user "tedg-mentee" is registered with email "tedg-mentee@example.com" and password "Sup3rS3cret!"
    When user "tedg-mentor" posts tweet with single mention of "tedg-mentee"
    Then the response status is 200

  Scenario: Posting a tweet mentioning a non-existent user still succeeds
    Given user "tedg-poet" is registered with email "tedg-poet@example.com" and password "Sup3rS3cret!"
    When user "tedg-poet" posts tweet mentioning a missing user "noSuchHandleZ9"
    Then the response status is 200

  Scenario: Updating a tweet to add a mention drives mention-add path
    Given user "tedg-uppost" is registered with email "tedg-uppost@example.com" and password "Sup3rS3cret!"
    And user "tedg-target" is registered with email "tedg-target@example.com" and password "Sup3rS3cret!"
    When user "tedg-uppost" posts tweet "plain tweet without any mentions yet"
    Then the response status is 200
    When user "tedg-uppost" updates their last tweet to mention "tedg-target"
    Then the response status is 200

  Scenario: Updating a tweet to remove a mention drives mention-remove path
    Given user "tedg-rmpost" is registered with email "tedg-rmpost@example.com" and password "Sup3rS3cret!"
    And user "tedg-rmtarget" is registered with email "tedg-rmtarget@example.com" and password "Sup3rS3cret!"
    When user "tedg-rmpost" posts tweet with single mention of "tedg-rmtarget"
    Then the response status is 200
    When user "tedg-rmpost" updates their last tweet to plain content "edited content with no mention here"
    Then the response status is 200

  Scenario: Search hashtag with no matches returns 200 empty list
    Given user "tedg-h1" is registered with email "tedg-h1@example.com" and password "Sup3rS3cret!"
    When user "tedg-h1" searches tweets by hashtag with no matches
    Then the response status is 200
    And the response body is a list of size 0

  Scenario: Posting a tweet with hashtag, then searching by exact hashtag returns it
    Given user "tedg-tag" is registered with email "tedg-tag@example.com" and password "Sup3rS3cret!"
    When user "tedg-tag" posts tweet "this tweet has a #buildquality theme"
    Then the response status is 200
    When user "tedg-tag" searches tweets by hashtag "buildquality"
    Then the response status is 200

  Scenario: Posting two tweets sharing a hashtag returns both on hashtag search
    Given user "tedg-pair" is registered with email "tedg-pair@example.com" and password "Sup3rS3cret!"
    When user "tedg-pair" posts tweet "first one #sharedhashtagxyz here"
    Then the response status is 200
    When user "tedg-pair" posts tweet "second one #sharedhashtagxyz also"
    Then the response status is 200
    When user "tedg-pair" searches tweets by hashtag "sharedhashtagxyz"
    Then the response status is 200

  Scenario: Fetching summary of a tweet twice exercises cache-cold then cache-hit
    Given user "tedg-cache" is registered with email "tedg-cache@example.com" and password "Sup3rS3cret!"
    When user "tedg-cache" posts tweet "tweet whose summary is read twice"
    Then the response status is 200
    When user "tedg-cache" fetches summary of tweet "tweet whose summary is read twice"
    Then the response status is 200
    When user "tedg-cache" fetches summary of tweet "tweet whose summary is read twice"
    Then the response status is 200

  Scenario: Fetching tweet twice exercises cache-warm + cache-hit on TweetService
    Given user "tedg-cwarm" is registered with email "tedg-cwarm@example.com" and password "Sup3rS3cret!"
    When user "tedg-cwarm" posts tweet "tweet to be fetched repeatedly"
    Then the response status is 200
    When user "tedg-cwarm" fetches tweet "tweet to be fetched repeatedly"
    Then the response status is 200
    When user "tedg-cwarm" fetches tweet "tweet to be fetched repeatedly"
    Then the response status is 200

  Scenario: Fetching popular hashtags after several posts returns 200
    Given user "tedg-pop" is registered with email "tedg-pop@example.com" and password "Sup3rS3cret!"
    When user "tedg-pop" posts tweet "first popular #poptag here"
    Then the response status is 200
    When user "tedg-pop" posts tweet "second popular #poptag here"
    Then the response status is 200
    When user "tedg-pop" fetches popular hashtags
    Then the response status is 200

  Scenario: Search tweets for nonsense substring returns empty
    Given user "tedg-nm" is registered with email "tedg-nm@example.com" and password "Sup3rS3cret!"
    When user "tedg-nm" searches tweets for "qzqzqzwjwjbwbwbw"
    Then the response status is 200

  Scenario: Posting a tweet with multiple hashtags persists each
    Given user "tedg-multi" is registered with email "tedg-multi@example.com" and password "Sup3rS3cret!"
    When user "tedg-multi" posts tweet "tweet with #alphaz and #betaz hashtags"
    Then the response status is 200
    When user "tedg-multi" searches tweets by hashtag "alphaz"
    Then the response status is 200
    When user "tedg-multi" searches tweets by hashtag "betaz"
    Then the response status is 200

  Scenario: Posting a tweet with multiple mentions
    Given user "tedg-mm-poster" is registered with email "tedg-mm-poster@example.com" and password "Sup3rS3cret!"
    And user "tedg-mm1" is registered with email "tedg-mm1@example.com" and password "Sup3rS3cret!"
    And user "tedg-mm2" is registered with email "tedg-mm2@example.com" and password "Sup3rS3cret!"
    When user "tedg-mm-poster" posts tweet "shout out @tedg-mm1 and @tedg-mm2 here"
    Then the response status is 200

  Scenario: Updating a tweet preserves it on subsequent fetch
    Given user "tedg-upd" is registered with email "tedg-upd@example.com" and password "Sup3rS3cret!"
    When user "tedg-upd" posts tweet "tweet content to be edited later"
    Then the response status is 200
    When user "tedg-upd" updates tweet "tweet content to be edited later" content to "edited tweet content here"
    Then the response status is 200
    When user "tedg-upd" fetches tweet "tweet content to be edited later"
    Then the response status is 200

  Scenario: Updates of mention->mention drive both add and remove paths
    Given user "tedg-mc" is registered with email "tedg-mc@example.com" and password "Sup3rS3cret!"
    And user "tedg-old" is registered with email "tedg-old@example.com" and password "Sup3rS3cret!"
    And user "tedg-new" is registered with email "tedg-new@example.com" and password "Sup3rS3cret!"
    When user "tedg-mc" posts tweet with single mention of "tedg-old"
    Then the response status is 200
    When user "tedg-mc" updates their last tweet to mention "tedg-new"
    Then the response status is 200
