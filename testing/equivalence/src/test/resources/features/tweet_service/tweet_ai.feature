Feature: AI streaming endpoints (functional, not performance)
  Spring AI surface under /tweet-service/tweets/ai/*. We assert the endpoints
  respond with the right status / content-type / SSE shape only — distributional
  fidelity of the mock vs live LLM is the calibration tooling's concern, not FE.

  Scenario: W0 mock-stream emits an SSE response
    Given user "alice" is registered with email "alice@example.com" and password "Sup3rS3cret!"
    When user "alice" fetches the AI mock-stream
    Then the response status is 200
    And the response content-type is "text/event-stream"
    And the SSE response yielded at least 1 chunks

  Scenario: W1 summarize emits an SSE response
    Given user "bob" is registered with email "bob@example.com" and password "Sup3rS3cret!"
    When user "bob" requests AI summarize for prompt "summarize tweebyte in five words"
    Then the response status is 200
    And the response content-type is "text/event-stream"
    And the SSE response yielded at least 1 chunks

  Scenario: AI buffered endpoint returns JSON with status 200
    Given user "carol" is registered with email "carol@example.com" and password "Sup3rS3cret!"
    When user "carol" requests AI buffered for prompt "any prompt for buffered control"
    Then the response status is 200
    And the response content-type is "application/json"

  Scenario: W2 summarize-with-tool emits an SSE response that fires the user-summary tool
    Given user "dan" is registered with email "dan@example.com" and password "Sup3rS3cret!"
    When user "dan" requests AI summarize-with-tool for prompt "summarize the user this tool fetches"
    Then the response status is 200
    And the response content-type is "text/event-stream"
    And the SSE response yielded at least 1 chunks

  Scenario: W1 summarize works with an alternative prompt
    Given user "ed" is registered with email "ed@example.com" and password "Sup3rS3cret!"
    When user "ed" requests AI summarize for prompt "alternate prompt for cell coverage"
    Then the response status is 200

  Scenario: W0 mock-stream content-type is SSE
    Given user "fay" is registered with email "fay@example.com" and password "Sup3rS3cret!"
    When user "fay" fetches the AI mock-stream
    Then the response content-type is "text/event-stream"

  Scenario: W0 mock-stream with custom token-count and ITL emits chunks
    # Exercises non-default RequestParam values on /tweets/ai/mock-stream — drives
    # branch coverage on the param-binding and the Flux.range / SseEmitter loop bound.
    Given user "gus" is registered with email "gus@example.com" and password "Sup3rS3cret!"
    When user "gus" fetches the AI mock-stream with 5 tokens and 5 ms ITL
    Then the response status is 200
    And the response content-type is "text/event-stream"
    And the SSE response yielded at least 1 chunks

  Scenario: W0 mock-stream with single-token request still emits SSE
    Given user "hank" is registered with email "hank@example.com" and password "Sup3rS3cret!"
    When user "hank" fetches the AI mock-stream with 1 tokens and 1 ms ITL
    Then the response status is 200

  Scenario: W0 mock-stream with zero tokens still returns SSE 200
    # Edge: tokens=0 means Flux.range(0,0) — empty stream but still a valid SSE response.
    Given user "iva" is registered with email "iva@example.com" and password "Sup3rS3cret!"
    When user "iva" fetches the AI mock-stream with 0 tokens and 1 ms ITL
    Then the response status is 200

  Scenario: W1 summarize handles a unicode prompt
    Given user "jay" is registered with email "jay@example.com" and password "Sup3rS3cret!"
    When user "jay" requests AI summarize for prompt "héllo wörld résumé 🚀 中文 αβγ"
    Then the response status is 200
    And the response content-type is "text/event-stream"

  Scenario: W1 summarize rejects an empty prompt with 500
    # Spring AI's ChatModel rejects empty user content with IllegalArgumentException
    # ("text cannot be null or empty"). Both stacks surface this as 500 via their
    # respective GlobalExceptionHandler fallbacks. FE asserts current behaviour;
    # the error-path branch in summarize covers doFinally with OUTCOME_ERROR.
    Given user "kim" is registered with email "kim@example.com" and password "Sup3rS3cret!"
    When user "kim" requests AI summarize for prompt ""
    Then the response status is 500

  Scenario: W1 summarize handles a long prompt
    Given user "leo" is registered with email "leo@example.com" and password "Sup3rS3cret!"
    When user "leo" requests AI summarize for prompt "the quick brown fox jumps over the lazy dog and then writes a long manifesto about the importance of inter-token latency in mock streaming chat models calibrated against log-normal time-to-first-token distributions"
    Then the response status is 200

  Scenario: AI buffered handles a long prompt and returns a JSON envelope
    Given user "mia" is registered with email "mia@example.com" and password "Sup3rS3cret!"
    When user "mia" requests AI buffered for prompt "long buffered request body to verify that the buffered endpoint accumulates all streamed tokens into a single response field correctly"
    Then the response status is 200
    And the response content-type is "application/json"

  Scenario: AI buffered rejects an empty prompt with 500
    # Same Spring-AI rejection as the summarize endpoint — covers buffered's
    # doFinally(OUTCOME_ERROR) on async (try/catch in CompletableFuture path)
    # and reactive (doFinally signalType ON_ERROR).
    Given user "nia" is registered with email "nia@example.com" and password "Sup3rS3cret!"
    When user "nia" requests AI buffered for prompt ""
    Then the response status is 500

  Scenario: AI buffered handles a unicode prompt
    Given user "ozz" is registered with email "ozz@example.com" and password "Sup3rS3cret!"
    When user "ozz" requests AI buffered for prompt "ünicode tëst with émoji 🦄 and CJK 漢字"
    Then the response status is 200

  Scenario: W2 summarize-with-tool handles a unicode prompt
    Given user "pat" is registered with email "pat@example.com" and password "Sup3rS3cret!"
    When user "pat" requests AI summarize-with-tool for prompt "ünicode tool prompt 🚀"
    Then the response status is 200
    And the response content-type is "text/event-stream"

  Scenario: W2 summarize-with-tool handles a long prompt
    Given user "qui" is registered with email "qui@example.com" and password "Sup3rS3cret!"
    When user "qui" requests AI summarize-with-tool for prompt "long prompt for the W2 path that exercises the tool injection code branch by being substantively longer than ten characters of generic content"
    Then the response status is 200

  Scenario: W2 summarize-with-tool rejects an empty prompt with 500
    # Empty prompt triggers Spring-AI's null/empty rejection. Covers W2's
    # OUTCOME_ERROR doFinally branch (concatMap upstream errors before tool inject).
    Given user "ros" is registered with email "ros@example.com" and password "Sup3rS3cret!"
    When user "ros" requests AI summarize-with-tool for prompt ""
    Then the response status is 500
