Feature: Media upload, preview, download and reveal
  user-service owns media as a content-addressed store reachable through the gateway
  at /user-service/media (JWT-protected; only /auth/* is exempt).

    POST /media               — pure upload; stores the bytes as-is, returns {id}.
    POST /media/{id}/preview  — runs the CPU pipeline over original {id}, stores the
                                derived JPEG behind a bcrypt password gate, returns {id}.
    GET  /media/{id}          — streams the stored bytes (206 Partial Content).
    POST /media/{id}/reveal   — password-checks a preview, streams the original behind it.

  Both stacks behave identically; FE asserts status, content-type and byte sizes
  (never pixels — the JPEG output is deterministic per-stack but not bit-equal across JVMs).

  Scenario: An authenticated user can upload an image and download it back
    Given user "kilo" is registered with email "kilo@example.com" and password "Sup3rS3cret!"
    When user "kilo" uploads a 64x48 image
    Then the response status is 200
    And the response body has an id
    When user "kilo" downloads the uploaded media
    Then the response status is 206
    And the response body has a content-length of at least 1024 bytes

  Scenario: Previewing an uploaded original returns a JPEG preview id
    Given user "lima" is registered with email "lima@example.com" and password "Sup3rS3cret!"
    When user "lima" uploads a 96x32 image
    Then the response status is 200
    When user "lima" previews the uploaded media with password "open-sesame"
    Then the response status is 200
    And the response body has an id
    When user "lima" downloads the preview
    Then the response status is 206
    And the response content-type is "image/jpeg"

  Scenario: The original behind a preview is revealed with the correct password
    Given user "mike" is registered with email "mike@example.com" and password "Sup3rS3cret!"
    When user "mike" uploads a 64x64 image
    And user "mike" previews the uploaded media with password "open-sesame"
    Then the response status is 200
    When user "mike" reveals the preview with password "open-sesame"
    Then the response status is 206

  Scenario: Revealing a preview with the wrong password is forbidden
    Given user "november" is registered with email "november@example.com" and password "Sup3rS3cret!"
    When user "november" uploads a 64x64 image
    And user "november" previews the uploaded media with password "open-sesame"
    Then the response status is 200
    When user "november" reveals the preview with password "wrong-password"
    Then the response status is 403

  Scenario: Previewing a non-existent original returns 404
    Given user "oscar" is registered with email "oscar@example.com" and password "Sup3rS3cret!"
    When user "oscar" previews a non-existent media with password "open-sesame"
    Then the response status is 404

  Scenario: Downloading a non-existent media returns 404
    Given user "papa" is registered with email "papa@example.com" and password "Sup3rS3cret!"
    When user "papa" downloads a non-existent media
    Then the response status is 404

  Scenario: Previewing a non-image upload is rejected with 415
    # POST /media accepts any bytes and stores them as a blob, recording the part's
    # content type (here text/plain). /preview never takes a content type from the
    # client: it dispatches on the stored original's content type. Only image/* has a
    # registered pipeline, so a text/plain source hits the default branch →
    # UnsupportedMediaTypeException → 415. (A source typed image/* but carrying
    # undecodable bytes still surfaces 500 via ImageIO.read returning null.)
    Given user "quebec" is registered with email "quebec@example.com" and password "Sup3rS3cret!"
    When user "quebec" uploads a non-image file
    Then the response status is 200
    When user "quebec" previews the uploaded media with password "open-sesame"
    Then the response status is 415

  Scenario: Uploading without a JWT is rejected at the gateway
    When a client uploads a 32x32 image without auth
    Then the response status is 401

  Scenario: Downloading without a JWT is rejected at the gateway
    Given user "romeo" is registered with email "romeo@example.com" and password "Sup3rS3cret!"
    When user "romeo" uploads a 32x32 image
    And a client downloads the uploaded media without auth
    Then the response status is 401

  # B1 / MS-1 — MediaService.exists — 4 branches (cache-hit, cache-miss, repo-true, repo-false)

  Scenario: Existence check returns true for a known media asset
    Given user "sierra" is registered with email "sierra@example.com" and password "Sup3rS3cret!"
    When user "sierra" uploads a 32x32 image
    Then the response status is 200
    And the response body has an id
    When user "sierra" checks whether the uploaded media exists
    Then the response status is 200
    And the response body has exists "true"

  Scenario: Existence check returns false for an unknown media id
    Given user "tango" is registered with email "tango@example.com" and password "Sup3rS3cret!"
    When user "tango" checks whether a non-existent media exists
    Then the response status is 200
    And the response body has exists "false"

  # B1 / MS-2 — MediaService.upload null content-type branch (1 branch)

  Scenario: Upload without a part content-type stores the asset as octet-stream
    Given user "uniform" is registered with email "uniform@example.com" and password "Sup3rS3cret!"
    When user "uniform" uploads a 32x32 image without part content-type
    Then the response status is 200
    And the response body has an id

  # B1 / MS-3 — MediaService.previewImage oversized and corrupted branches (3 branches)

  Scenario: Previewing an image with a corrupted header returns 500
    Given user "victor" is registered with email "victor@example.com" and password "Sup3rS3cret!"
    When user "victor" uploads a corrupted image file
    Then the response status is 200
    When user "victor" previews the uploaded media with password "open-sesame"
    Then the response status is 500

  Scenario: Previewing an oversized image returns 500
    Given user "whiskey" is registered with email "whiskey@example.com" and password "Sup3rS3cret!"
    When user "whiskey" uploads a 4097x64 image
    Then the response status is 200
    When user "whiskey" previews the uploaded media with password "open-sesame"
    Then the response status is 500

  # B1 / MS-4 — MediaService.reveal source-is-null and preview-not-found branches (3 branches)

  Scenario: Revealing a non-preview asset (original) returns 404
    Given user "xray" is registered with email "xray@example.com" and password "Sup3rS3cret!"
    When user "xray" uploads a 64x64 image
    Then the response status is 200
    When user "xray" reveals the uploaded media as a preview with password "open-sesame"
    Then the response status is 404

  Scenario: Revealing a non-existent preview id returns 404
    Given user "yankee" is registered with email "yankee@example.com" and password "Sup3rS3cret!"
    When user "yankee" reveals a non-existent preview with password "open-sesame"
    Then the response status is 404

  # B1 / MS-5 — MediaService.store content-addressed dedup (cache-hit branch, 1 branch)
  # Also covers MC-3 (MediaCache.getByChecksum non-null fast-return).

  Scenario: Re-uploading identical bytes returns the same content-addressed id
    Given user "zulu" is registered with email "zulu@example.com" and password "Sup3rS3cret!"
    When user "zulu" uploads a solid 48x48 image
    Then the response status is 200
    And the response body has an id
    When user "zulu" uploads a solid 48x48 image
    Then the response status is 200
    And the response body has an id
    And both upload responses return the same id

  # B1 / MC-1 — MediaCache.getById cache-hit branch (2 branches)

  Scenario: Downloading the same asset twice uses the in-process cache on the second hit
    Given user "alfa" is registered with email "alfa@example.com" and password "Sup3rS3cret!"
    When user "alfa" uploads a 64x48 image
    Then the response status is 200
    When user "alfa" downloads the uploaded media
    Then the response status is 206
    When user "alfa" downloads the uploaded media
    Then the response status is 206
    And the response body has a content-length of at least 1024 bytes

  Scenario: Flushing the media cache still allows the asset to be downloaded from storage
    Given user "bravo-cache" is registered with email "bravo-cache@example.com" and password "Sup3rS3cret!"
    When user "bravo-cache" uploads a 64x48 image
    Then the response status is 200
    When user "bravo-cache" downloads the uploaded media
    Then the response status is 206
    When user "bravo-cache" flushes the media cache
    Then the response status is 204
    When user "bravo-cache" downloads the uploaded media
    Then the response status is 206

  Scenario: Existence check stays true after flushing the media cache
    Given user "charlie-cache" is registered with email "charlie-cache@example.com" and password "Sup3rS3cret!"
    When user "charlie-cache" uploads a 32x32 image
    Then the response status is 200
    When user "charlie-cache" flushes the media cache
    Then the response status is 204
    When user "charlie-cache" checks whether the uploaded media exists
    Then the response status is 200
    And the response body has exists "true"

  # B3-2 socket-abort — MediaService.isClientAbort
  # Drives the mid-stream client-disconnect branch in MediaService.streamThrottled /
  # streamThrottled (reactive). RestAssured cannot abort a streaming connection; we use
  # a raw socket/HttpClient helper that reads the 206 status line then drops the connection
  # while bytes are still being streamed, causing a SocketException / ClosedChannelException
  # on the server side. The service must handle this gracefully (no 5xx, no uncaught
  # exception logged as ERROR) — FE asserts the upload still returns 200 and the abort step
  # completes without throwing.

  Scenario: Mid-stream socket abort is handled gracefully by the media download endpoint
    # Upload a moderately-sized image so the throttled streamer emits at least one chunk
    # before the client closes the connection. The disconnect surfaces as a benign
    # SocketException / ClosedChannelException inside streamThrottled — isClientAbort must
    # classify it as non-fatal and swallow it without re-throwing.
    Given user "abort-a" is registered with email "abort-a@example.com" and password "Sup3rS3cret!"
    When user "abort-a" uploads a 256x256 image
    Then the response status is 200
    And the response body has an id
    When user "abort-a" downloads the uploaded media and aborts mid-stream
    Then the mid-stream abort completed without error
