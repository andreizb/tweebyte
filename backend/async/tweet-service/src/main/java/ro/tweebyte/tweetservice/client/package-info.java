/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

/**
 * Blocking {@code RestClient} fan-out wrappers for the downstream interaction- and
 * user-services, dispatched on the dedicated {@code httpClientExecutor} so each
 * cross-service round-trip parks its own pool thread.
 *
 * @author Andrei Zbarcea
 */
package ro.tweebyte.tweetservice.client;
