/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.model;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Wire row for the batched tweet-interactions response.
 *
 * @param tweetId the tweet the counts belong to
 * @param likes the like count
 * @param replies the reply count
 * @param retweets the retweet count
 * @param topReply the top reply, or {@code null} when the tweet has none
 * @author Andrei Zbarcea
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TweetInteractionsEntryDto(@JsonProperty("tweet_id") UUID tweetId,
		@JsonProperty("likes") long likes, @JsonProperty("replies") long replies,
		@JsonProperty("retweets") long retweets, @JsonProperty("top_reply") ReplyDto topReply) {

	public TweetInteractionsDto toInteractions() {
		return new TweetInteractionsDto(this.likes, this.replies, this.retweets, this.topReply);
	}

}
