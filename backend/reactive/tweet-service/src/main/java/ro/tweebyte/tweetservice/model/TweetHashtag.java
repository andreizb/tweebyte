/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.model;

import java.util.UUID;

/**
 * Projection for the batched hashtag lookup: each row pairs a tweet id with one of its
 * hashtags, so a single join over the tweet_hashtag table resolves the hashtags for a
 * whole page of tweets at once.
 *
 * @param tweetId identifier of the tweet the hashtag belongs to
 * @param id identifier of the hashtag
 * @param text the hashtag text
 * @author Tweebyte Engineering
 */
public record TweetHashtag(UUID tweetId, UUID id, String text) {
}
