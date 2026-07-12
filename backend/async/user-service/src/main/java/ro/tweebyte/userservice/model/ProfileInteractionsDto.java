/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Combined user-profile payload deserialized from interaction-service's single
 * {@code POST /follows/{userId}/profile-interactions} response: the user's follow counts plus
 * the per-tweet interactions for the profile's tweet page. Lets the user-profile read fetch
 * both in one round-trip instead of a follow-counts call plus a nested tweet-interactions call.
 * The JSON field names mirror interaction-service's wire shape exactly so the fields
 * deserialize rather than silently null out.
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
