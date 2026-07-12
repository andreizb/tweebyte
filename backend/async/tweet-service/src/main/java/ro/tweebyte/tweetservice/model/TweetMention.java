/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.model;

import java.util.UUID;

/**
 * Projection for the batched mention lookup: each row pairs a tweet id with one of its
 * mentions. Selecting the tweet_id FK as a scalar (m.tweetEntity.id) keeps the query to a
 * single statement and avoids initialising the eager {@code @ManyToOne} back-reference
 * per mention.
 *
 * @param tweetId id of the tweet this mention belongs to
 * @param id id of the mention row
 * @param userId id of the mentioned user
 * @param text the mention text
 * @author Andrei Zbarcea
 */
public record TweetMention(UUID tweetId, UUID id, UUID userId, String text) {
}
