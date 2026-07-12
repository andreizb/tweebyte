/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

/**
 * REST controllers exposing the user, authentication and media HTTP API. Handlers return
 * {@link java.util.concurrent.CompletableFuture} so request threads are released while
 * the work runs on the service executors.
 *
 * @author Andrei Zbarcea
 */
package ro.tweebyte.userservice.controller;
