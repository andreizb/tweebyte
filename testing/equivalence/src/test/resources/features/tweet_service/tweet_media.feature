Feature: Media filter endpoint
  POST /tweet-service/media/filter — multipart image upload, runs the synthetic
  image-processing pipeline (toRGB → 3× gaussian blur → sobel edge → resize → JPEG).
  FE asserts only status + content-type; pixel-level fidelity isn't part of the
  cross-stack contract (the pipeline is deterministic per-stack but the JPEG
  output is not bit-equal across JVMs).

  Scenario: Filtering a small JPEG returns 200 image/jpeg
    Given user "alice" is registered with email "alice@example.com" and password "Sup3rS3cret!"
    When user "alice" uploads a 32x32 image to the media filter
    Then the response status is 200
    And the response content-type is "image/jpeg"

  Scenario: Filtering a medium JPEG returns 200 image/jpeg
    Given user "bob" is registered with email "bob@example.com" and password "Sup3rS3cret!"
    When user "bob" uploads a 64x48 image to the media filter
    Then the response status is 200
    And the response content-type is "image/jpeg"

  Scenario: Filtering a non-square JPEG returns 200 image/jpeg
    Given user "carol" is registered with email "carol@example.com" and password "Sup3rS3cret!"
    When user "carol" uploads a 96x32 image to the media filter
    Then the response status is 200

  Scenario: Filtering an image without auth is rejected with 401
    When a client uploads a 32x32 image to the media filter without auth
    Then the response status is 401

  Scenario: Filtering a non-image upload returns 500
    # MediaService rejects an unreadable image with `throw new IOException("Invalid image")`,
    # which both stacks surface as 500 (no domain-specific handler maps IOException → 400).
    # FE asserts current behaviour rather than the REST-purist 400.
    Given user "ed" is registered with email "ed@example.com" and password "Sup3rS3cret!"
    When user "ed" uploads a non-image file to the media filter
    Then the response status is 500
