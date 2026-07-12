/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

/**
 * The kind of entity a like targets.
 *
 * @author Andrei Zbarcea
 */
public enum LikeableType {

	/**
	 * The like targets a tweet.
	 */
	TWEET,

	/**
	 * The like targets a reply.
	 */
	REPLY

}
