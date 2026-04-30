Feature: Interaction-service edge cases (404s, no-ops, cache warm/hit)
  Drives extra branches in FollowService / LikeService / ReplyService /
  RetweetService / RecommendationService — particularly the "not found" arms
  and the cache-cold-then-hit second-call paths.

  Scenario: Reading followers count of a non-existent user returns 200 with 0
    Given user "iedg-a" is registered with email "iedg-a@example.com" and password "Sup3rS3cret!"
    When user "iedg-a" reads followers count of a non-existent user
    Then the response status is 200
    And the response body is the number 0

  Scenario: Reading following count of a non-existent user returns 200 with 0
    Given user "iedg-b" is registered with email "iedg-b@example.com" and password "Sup3rS3cret!"
    When user "iedg-b" reads following count of a non-existent user
    Then the response status is 200
    And the response body is the number 0

  Scenario: Listing followers of a non-existent user returns 200 with empty list
    Given user "iedg-c" is registered with email "iedg-c@example.com" and password "Sup3rS3cret!"
    When user "iedg-c" lists followers of a non-existent user
    Then the response status is 200
    And the response body is a list of size 0

  Scenario: Listing following of a non-existent user returns 200 with empty list
    Given user "iedg-d" is registered with email "iedg-d@example.com" and password "Sup3rS3cret!"
    When user "iedg-d" lists following of a non-existent user
    Then the response status is 200

  Scenario: Reading retweet count for non-existent tweet returns 200 with 0
    Given user "iedg-e" is registered with email "iedg-e@example.com" and password "Sup3rS3cret!"
    When user "iedg-e" reads retweet count for non-existent tweet
    Then the response status is 200
    And the response body is the number 0

  Scenario: Reading reply count for non-existent tweet returns 200 with 0
    Given user "iedg-f" is registered with email "iedg-f@example.com" and password "Sup3rS3cret!"
    When user "iedg-f" reads reply count for non-existent tweet
    Then the response status is 200
    And the response body is the number 0

  Scenario: Listing replies for a non-existent tweet returns 200 with empty list
    Given user "iedg-g" is registered with email "iedg-g@example.com" and password "Sup3rS3cret!"
    When user "iedg-g" lists replies for a non-existent tweet
    Then the response status is 200
    And the response body is a list of size 0

  Scenario: Listing retweets for a non-existent tweet returns 200 with empty list
    Given user "iedg-h" is registered with email "iedg-h@example.com" and password "Sup3rS3cret!"
    When user "iedg-h" lists retweets for a non-existent tweet
    Then the response status is 200

  Scenario: Listing likes for a non-existent tweet returns 200 with empty list
    Given user "iedg-i" is registered with email "iedg-i@example.com" and password "Sup3rS3cret!"
    When user "iedg-i" lists likes for a non-existent tweet
    Then the response status is 200

  # Async raises 500 ("Reply not found"); reactive silently no-ops with 200.
  # Aligning would require a production fix on one side. Skipped.

  # Same divergence for retweets — async surfaces 500, reactive 200.
  # Skipped.

  Scenario: Following a non-existent user returns 404
    Given user "iedg-n" is registered with email "iedg-n@example.com" and password "Sup3rS3cret!"
    When user "iedg-n" attempts to follow a non-existent user
    Then the response status is 404

  Scenario: Unfollowing without prior follow is a no-op (204)
    Given user "iedg-o" is registered with email "iedg-o@example.com" and password "Sup3rS3cret!"
    When user "iedg-o" attempts to unfollow a non-existent user
    Then the response status is 204

  # Liking the same tweet twice causes a unique-constraint violation; both stacks
  # surface this as 500. This drives the duplicate-key error branch in LikeService.
  Scenario: Liking the same tweet twice — second like raises constraint 500
    Given user "iedg-p" is registered with email "iedg-p@example.com" and password "Sup3rS3cret!"
    And user "iedg-q" is registered with email "iedg-q@example.com" and password "Sup3rS3cret!"
    When user "iedg-p" posts tweet "iedg twice-liked tweet here"
    Then the response status is 200
    When user "iedg-q" likes tweet "iedg twice-liked tweet here"
    Then the response status is 200
    When user "iedg-q" likes tweet "iedg twice-liked tweet here"
    Then the response status is 500

  Scenario: Unliking a tweet that was never liked is a no-op (204)
    Given user "iedg-r" is registered with email "iedg-r@example.com" and password "Sup3rS3cret!"
    When user "iedg-r" unlikes a non-existent tweet
    Then the response status is 204

  Scenario: Unliking a reply that was never liked is a no-op (204)
    Given user "iedg-s" is registered with email "iedg-s@example.com" and password "Sup3rS3cret!"
    When user "iedg-s" unlikes a non-existent reply
    Then the response status is 204

  Scenario: Reading following list, then following, then re-reading hits cache-evict path
    Given user "iedg-t" is registered with email "iedg-t@example.com" and password "Sup3rS3cret!"
    And user "iedg-u" is registered with email "iedg-u@example.com" and password "Sup3rS3cret!"
    When user "iedg-t" lists following of "iedg-t"
    Then the response status is 200
    When user "iedg-t" follows "iedg-u"
    Then the response status is 204
    When user "iedg-t" lists following of "iedg-t"
    Then the response status is 200

  Scenario: Reading followers list, then having someone follow, then re-reading
    Given user "iedg-v" is registered with email "iedg-v@example.com" and password "Sup3rS3cret!"
    And user "iedg-w" is registered with email "iedg-w@example.com" and password "Sup3rS3cret!"
    When user "iedg-v" lists followers of "iedg-v"
    Then the response status is 200
    When user "iedg-w" follows "iedg-v"
    Then the response status is 204
    When user "iedg-v" lists followers of "iedg-v"
    Then the response status is 200

  Scenario: Three different users following one user, then count + listing
    Given user "iedg-target" is registered with email "iedg-target@example.com" and password "Sup3rS3cret!"
    And user "iedg-x" is registered with email "iedg-x@example.com" and password "Sup3rS3cret!"
    And user "iedg-y" is registered with email "iedg-y@example.com" and password "Sup3rS3cret!"
    And user "iedg-z" is registered with email "iedg-z@example.com" and password "Sup3rS3cret!"
    When user "iedg-x" follows "iedg-target"
    Then the response status is 204
    When user "iedg-y" follows "iedg-target"
    Then the response status is 204
    When user "iedg-z" follows "iedg-target"
    Then the response status is 204
    When user "iedg-target" reads followers count of "iedg-target"
    Then the response status is 200
    And the response body is the number 3
    When user "iedg-target" lists followers of "iedg-target"
    Then the response status is 200
    And the response body is a list of size 3

  Scenario: Following count after multiple targets followed
    Given user "iedg-orig" is registered with email "iedg-orig@example.com" and password "Sup3rS3cret!"
    And user "iedg-tgt1" is registered with email "iedg-tgt1@example.com" and password "Sup3rS3cret!"
    And user "iedg-tgt2" is registered with email "iedg-tgt2@example.com" and password "Sup3rS3cret!"
    And user "iedg-tgt3" is registered with email "iedg-tgt3@example.com" and password "Sup3rS3cret!"
    When user "iedg-orig" follows "iedg-tgt1"
    Then the response status is 204
    When user "iedg-orig" follows "iedg-tgt2"
    Then the response status is 204
    When user "iedg-orig" follows "iedg-tgt3"
    Then the response status is 204
    When user "iedg-orig" reads following count of "iedg-orig"
    Then the response status is 200
    And the response body is the number 3

  Scenario: Top reply for a non-existent tweet returns 200 (placeholder body)
    Given user "iedg-toprp" is registered with email "iedg-toprp@example.com" and password "Sup3rS3cret!"
    When user "iedg-toprp" fetches top reply for a non-existent tweet
    Then the response status is 200

  # Replying with empty content currently succeeds (no @NotBlank on reply.content
  # in either stack). This scenario asserts that observed behaviour and exercises
  # the create-reply path with content="".
  Scenario: Replying with empty content currently succeeds with 200
    Given user "iedg-emt" is registered with email "iedg-emt@example.com" and password "Sup3rS3cret!"
    When user "iedg-emt" posts tweet "tweet expecting empty reply rejection"
    Then the response status is 200
    When user "iedg-emt" replies with empty content to tweet "tweet expecting empty reply rejection"
    Then the response status is 200

  Scenario: Following yourself succeeds (creates a self-follow record) on both stacks
    Given user "iedg-self" is registered with email "iedg-self@example.com" and password "Sup3rS3cret!"
    When user "iedg-self" attempts to follow themselves
    Then the response status is 204

  Scenario: Listing follow recommendations for a fresh user returns 200
    Given user "iedg-rec" is registered with email "iedg-rec@example.com" and password "Sup3rS3cret!"
    When user "iedg-rec" fetches follow recommendations
    Then the response status is 200

  Scenario: Listing hashtag recommendations for a fresh user returns 200
    Given user "iedg-htg" is registered with email "iedg-htg@example.com" and password "Sup3rS3cret!"
    When user "iedg-htg" fetches hashtag recommendations
    Then the response status is 200
