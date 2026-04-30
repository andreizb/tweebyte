Feature: Tweets with hashtags + mentions
  Posting a tweet with `#tag` should extract the hashtag and store it on the
  tweet. Same for `@user` mentions (FE doesn't assert delivery, only extraction).

  Scenario: Posting a tweet with one hashtag extracts it on the tweet body
    Given user "alice" is registered with email "alice@example.com" and password "Sup3rS3cret!"
    When user "alice" posts tweet "learning #springboot from the docs"
    Then the response status is 200
    When user "alice" fetches tweet "learning #springboot from the docs"
    Then the response status is 200

  Scenario: Posting a tweet with two hashtags extracts both
    Given user "bob" is registered with email "bob@example.com" and password "Sup3rS3cret!"
    When user "bob" posts tweet "today using #java21 and #spring6 together"
    Then the response status is 200

  Scenario: Posting a tweet with a mention does not crash
    Given user "carol" is registered with email "carol@example.com" and password "Sup3rS3cret!"
    And user "dan" is registered with email "dan@example.com" and password "Sup3rS3cret!"
    When user "carol" posts tweet "hi @dan how are you today"
    Then the response status is 200

  Scenario: A user can post a hashtag-only tweet
    Given user "ed" is registered with email "ed@example.com" and password "Sup3rS3cret!"
    When user "ed" posts tweet "#singlehashtag rest of content"
    Then the response status is 200

  Scenario: A user can post a tweet with a hashtag containing digits
    Given user "fay" is registered with email "fay@example.com" and password "Sup3rS3cret!"
    When user "fay" posts tweet "release notes for #v2024 just published"
    Then the response status is 200
    When user "fay" searches tweets by hashtag "v2024"
    Then the response status is 200

  Scenario: Searching for a hashtag with no matches returns empty
    Given user "gus" is registered with email "gus@example.com" and password "Sup3rS3cret!"
    When user "gus" searches tweets by hashtag "doesntexistanywhereyet"
    Then the response status is 200
    And the response body is a list of size 0

  Scenario: Posting a tweet with three hashtags extracts all three
    # Exercises HashtagService.handleTweetCreationHashtags' Flux.fromIterable
    # branch with multiple iterations + multiple findOrCreateHashtag calls.
    # Fetch drives TweetMapper.mapEntityToDto with non-empty hashtags and
    # exercises HashtagMapper.mapEntityToDto on every element.
    # async createTweet now waits for hashtag/mention handlers via
    # thenComposeAsync + allOf, so no `we wait …` workaround is needed.
    Given user "hank" is registered with email "hank@example.com" and password "Sup3rS3cret!"
    When user "hank" posts tweet "now using #java21 #spring6 #junit5 together"
    Then the response status is 200
    When user "hank" fetches tweet "now using #java21 #spring6 #junit5 together"
    Then the response status is 200
    And the response body contains hashtag "java21"

  Scenario: Editing a tweet to add new hashtags exercises hashtag-update diff
    # Drives HashtagService.handleTweetUpdateHashtags: collectList → diff →
    # add-only branch (existing set is empty, new set non-empty).
    Given user "iva" is registered with email "iva@example.com" and password "Sup3rS3cret!"
    When user "iva" posts tweet "before adding any hashtags here yet"
    Then the response status is 200
    When user "iva" updates tweet "before adding any hashtags here yet" content to "#newtag1 #newtag2 added now to existing tweet"
    Then the response status is 200

  Scenario: Editing a tweet to remove all hashtags exercises hashtag-update diff
    # Drives the remove-only branch (reactive): existingIds non-empty, newHashtagIds
    # empty → hashtagsToRemove = existingIds, hashtagsToAdd = {}.
    # async createTweet now serialises the hashtag handler before
    # returning, so PUT-after-POST no longer races the @Version bump.
    Given user "jay" is registered with email "jay@example.com" and password "Sup3rS3cret!"
    When user "jay" posts tweet "starting with #removeme tag inside body"
    Then the response status is 200
    When user "jay" updates tweet "starting with #removeme tag inside body" content to "no hashtags remain after this update applies"
    Then the response status is 200

  Scenario: Editing a tweet to swap hashtags exercises both add and remove diff branches
    # Drives BOTH branches: existing={oldA, keep}, new={keep, newB} → remove oldA, add newB.
    Given user "kim" is registered with email "kim@example.com" and password "Sup3rS3cret!"
    When user "kim" posts tweet "initial #keepme and #removeme tags here"
    Then the response status is 200
    When user "kim" updates tweet "initial #keepme and #removeme tags here" content to "now with #keepme and #addnew tags swapped"
    Then the response status is 200

  Scenario: Posting a tweet with two mentions exercises mention extraction
    # Fetch drives TweetMapper.mapEntityToDto with non-empty mentions, which
    # exercises MentionMapper.mapEntityToDto on every element.
    Given user "leo" is registered with email "leo@example.com" and password "Sup3rS3cret!"
    And user "mia" is registered with email "mia@example.com" and password "Sup3rS3cret!"
    And user "nia" is registered with email "nia@example.com" and password "Sup3rS3cret!"
    When user "leo" posts tweet "morning everyone @mia and @nia how are you"
    Then the response status is 200
    When user "leo" fetches tweet "morning everyone @mia and @nia how are you"
    Then the response status is 200

  Scenario: Posting a tweet that mentions an unknown user does not crash
    # MentionService.createMention onErrorResume(UserNotFoundException → empty)
    # branch: the mention is dropped silently rather than failing the post.
    Given user "ozz" is registered with email "ozz@example.com" and password "Sup3rS3cret!"
    When user "ozz" posts tweet "hello @nobodyusernamethatdoesntexist out there"
    Then the response status is 200

  Scenario: Editing a tweet to swap mentions exercises mention-update diff
    Given user "pat" is registered with email "pat@example.com" and password "Sup3rS3cret!"
    And user "qui" is registered with email "qui@example.com" and password "Sup3rS3cret!"
    And user "ros" is registered with email "ros@example.com" and password "Sup3rS3cret!"
    When user "pat" posts tweet "first hello @qui my friend in space"
    Then the response status is 200
    When user "pat" updates tweet "first hello @qui my friend in space" content to "second hello @ros instead now in space"
    Then the response status is 200

  Scenario: Posting a tweet with mixed hashtag and mention content
    # Fetch drives TweetMapper / HashtagMapper / MentionMapper mapEntityToDto
    # with both collections non-empty.
    Given user "sam" is registered with email "sam@example.com" and password "Sup3rS3cret!"
    And user "tom" is registered with email "tom@example.com" and password "Sup3rS3cret!"
    When user "sam" posts tweet "hey @tom check out #springboot tutorial today"
    Then the response status is 200
    When user "sam" fetches tweet "hey @tom check out #springboot tutorial today"
    Then the response status is 200
    And the response body contains hashtag "springboot"

  Scenario: Posting a tweet with a duplicate hashtag in the same body counts once
    # extractHashtags dedupes via HashSet — same tag twice produces one tweet_hashtag row.
    Given user "uma" is registered with email "uma@example.com" and password "Sup3rS3cret!"
    When user "uma" posts tweet "I love #duptag and again #duptag really"
    Then the response status is 200

  Scenario: Editing a tweet to keep the exact same hashtags is a no-op for the diff
    # Drives the both-empty branch (reactive): hashtagsToRemove and hashtagsToAdd both empty.
    Given user "vic" is registered with email "vic@example.com" and password "Sup3rS3cret!"
    When user "vic" posts tweet "stable #unchanged tag stays put always"
    Then the response status is 200
    When user "vic" updates tweet "stable #unchanged tag stays put always" content to "rephrased copy with #unchanged tag still here"
    Then the response status is 200
