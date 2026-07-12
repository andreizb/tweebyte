/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.equivalence.support;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Per-scenario shared state. Cucumber-Spring would normally inject a singleton here
 * via @ScenarioScope, but to avoid the spring-boot-test ceremony we keep scenario state
 * in a ThreadLocal and clear it in @Before / @After hooks.
 *
 * Holds: registered users (by handle), their JWTs, their UUIDs, and the latest REST
 * response so subsequent Then-steps can assert against it.
 */
public class ScenarioContext {

	private static final ThreadLocal<ScenarioContext> CTX = ThreadLocal.withInitial(ScenarioContext::new);

	public static ScenarioContext current() {
		return CTX.get();
	}

	public static void reset() {
		CTX.remove();
	}

	public final Map<String, String> jwtByHandle = new HashMap<>();

	public final Map<String, UUID> userIdByHandle = new HashMap<>();

	public final Map<String, String> emailByHandle = new HashMap<>();

	public final Map<String, String> passwordByHandle = new HashMap<>();

	/** Tweet UUIDs tracked by content-string (used as a stable key in scenarios). */
	public final Map<String, UUID> lastTweetIdByContent = new HashMap<>();

	/** Tweet UUIDs tracked by author handle (most recent post). */
	public final Map<String, UUID> lastTweetIdByHandle = new HashMap<>();

	public int lastStatus;

	public String lastBody;

	/**
	 * Raw byte count of the last response body — exact for binary (media) downloads,
	 * where lastBody.length() under-counts after UTF-8 decoding collapses byte runs.
	 */
	public int lastBodyByteLength;

	public JsonNode lastJson;

	public String lastContentType;

	/**
	 * Original-asset id from the last successful POST /media (pure upload); the source a
	 * subsequent /preview derives from and the target of download-by-id steps.
	 */
	public UUID lastUploadId;

	/**
	 * Preview-asset id from the last successful POST /media/{id}/preview; the target of
	 * download-the-preview and /reveal steps.
	 */
	public UUID lastPreviewId;

	/**
	 * Stashed upload id from the first upload in a dedup scenario, so the second upload's
	 * id can be compared against it in {@code both upload responses return the same id}.
	 */
	public UUID firstUploadId;

	/**
	 * Number of SSE chunks received before a mid-stream cancel. Set by the B3-1
	 * cancel-mid-stream step; asserted by {@code the stream cancel was detected}.
	 */
	public int lastCancelChunksReceived = -1;

	/**
	 * Whether the B3-2 socket-abort step completed without an unexpected exception. Set by
	 * {@code downloads the uploaded media and aborts mid-stream}; asserted by
	 * {@code the mid-stream abort completed without error}.
	 */
	public boolean lastAbortCompleted;

}
