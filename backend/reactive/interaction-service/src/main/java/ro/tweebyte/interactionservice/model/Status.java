/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

/**
 * Lifecycle state of a follow relationship.
 *
 * @author Andrei Zbarcea
 */
public enum Status {

	/**
	 * The follow request is awaiting the followed user's decision.
	 */
	PENDING,

	/**
	 * The follow request has been accepted.
	 */
	ACCEPTED,

	/**
	 * The follow request has been rejected.
	 */
	REJECTED

}
