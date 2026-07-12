/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Consolidated per-tweet interaction snapshot deserialized from interaction-service's
 * {@code POST /tweets/interactions}, letting tweet-enrichment fetch a page's like/reply/
 * retweet counts plus top reply in one round-trip instead of four batched POSTs. The
 * enrichment mapper still reads each field back into the same likes_count / replies_count /
 * retweets_count / top_reply tweet fields.
 *
 * @author Andrei Zbarcea
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public class TweetInteractionsDto {

	@JsonProperty("likes")
	private long likes;

	@JsonProperty("replies")
	private long replies;

	@JsonProperty("retweets")
	private long retweets;

	@JsonProperty("top_reply")
	private ReplyDto topReply;

}
