/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Combined user-profile payload returned by the single
 * {@code POST /follows/{userId}/profile-interactions} endpoint: the user's follower/following
 * counts plus the per-tweet interactions for the profile's tweet page. Lets the user-profile
 * read fetch both in one round-trip instead of a separate follow-counts call and a nested
 * tweet-interactions call. The values are composed from the existing follow-counts and
 * tweet-interactions reads — this DTO only groups them.
 *
 * @author Andrei Zbarcea
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Accessors(chain = true)
public class ProfileInteractionsDto {

	@JsonProperty("follow_counts")
	private FollowCountsDto followCounts;

	@JsonProperty("tweet_interactions")
	private List<TweetInteractionsEntryDto> tweetInteractions;

}
