/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.repository;

/**
 * Projection for {@link FollowRepository#countFollowersAndFollowing}: both the follower and
 * following counts of one user fetched in a single round-trip, replacing the two separate
 * COUNT queries the user-profile read previously issued.
 *
 * @param followers number of accepted followers (rows with followed_id = the user)
 * @param following number of accepted followings (rows with follower_id = the user)
 * @author Andrei Zbarcea
 */
public record FollowCountsRow(Long followers, Long following) {
}
