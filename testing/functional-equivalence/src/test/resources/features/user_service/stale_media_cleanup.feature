Feature: Stale media cleanup tick — StaleMediaCleanupService
  The user-service StaleMediaCleanupService runs with initial-delay-hours=0 in the
  functional-equivalence profile (boot-time first tick). With a short period configured
  in the FE profile, subsequent ticks happen during the suite run. A media asset is
  "stale" when it was uploaded more than stale-after-days ago AND is not referenced by
  any user profile, tweet, interaction (reply/retweet media_ids), or as a preview-source.
  The FE suite cannot back-date uploaded assets, so these scenarios use the
  media-reference endpoints to confirm that an uploaded-then-dereferenced asset stays
  reachable while referenced and becomes a candidate for cleanup once all references drop.

  # ---------------------------------------------------------------------------
  # B3-3b: user-service stale-media cleanup
  # StaleMediaCleanupService.cleanupStaleMedia
  # ---------------------------------------------------------------------------

  Scenario: Uploading media and confirming it is referenced keeps it alive
    # Exercises the "referenced" side of StaleMediaCleanupService: an asset referenced
    # by a tweet's media_ids is returned by the tweet-service /media/referenced endpoint,
    # so the cleanup sweep adds it to the referenced set and skips deletion.
    Given user "sm-keep-a" is registered with email "sm-keep-a@example.com" and password "Sup3rS3cret!"
    When user "sm-keep-a" uploads a 32x32 image
    Then the response status is 200
    And the response body has an id
    When user "sm-keep-a" posts tweet "tweet referencing uploaded media for cleanup test" with the uploaded media id
    Then the response status is 200
    When user "sm-keep-a" fetches tweet referenced media ids
    Then the response status is 200
    And the referenced media list contains the uploaded id
