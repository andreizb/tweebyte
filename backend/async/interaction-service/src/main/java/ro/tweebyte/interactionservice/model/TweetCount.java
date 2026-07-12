/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

import java.util.UUID;

/**
 * Projection for the batched per-tweet count queries (likes/replies/retweets). Each row
 * pairs a tweet id with its aggregate count, letting a single GROUP BY query replace the
 * per-tweet count fan-out.
 *
 * @param tweetId identifier of the tweet the count belongs to
 * @param total aggregate count for the tweet (likes, replies or retweets)
 * @author Andrei Zbarcea
 */
public record TweetCount(UUID tweetId, Long total) {
}
