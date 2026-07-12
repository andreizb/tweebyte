/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.model;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Per-tweet interaction row deserialized from interaction-service's combined
 * {@code POST /follows/{userId}/profile-interactions} response: a tweet's like/reply/retweet
 * counts and its top reply. The user-profile read merges these onto the un-enriched tweets it
 * fetched from tweet-service. The JSON field names mirror interaction-service's wire shape
 * exactly so the fields deserialize rather than silently null out; {@code top_reply} maps to
 * the same {@link TweetDto.ReplyDto} shape the enriched tweet path uses.
 *
 * @author Andrei Zbarcea
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Accessors(chain = true)
public class TweetInteractionsEntryDto {

	@JsonProperty("tweet_id")
	private UUID tweetId;

	@JsonProperty("likes")
	private long likes;

	@JsonProperty("replies")
	private long replies;

	@JsonProperty("retweets")
	private long retweets;

	@JsonProperty("top_reply")
	private TweetDto.ReplyDto topReply;

}
