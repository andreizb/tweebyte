/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.repository;

/**
 * Native-query projection for {@link FollowRepository#countFollowersAndFollowing}: both the
 * follower and following counts of one user fetched in a single round-trip.
 *
 * @author Andrei Zbarcea
 */
public interface FollowCountsRow {

	Long getFollowers();

	Long getFollowing();

}
