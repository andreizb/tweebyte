/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.model;

import java.util.UUID;

/**
 * Projection for the combined per-page relation read: one row per hashtag OR mention of a tweet,
 * tagged by {@code kind} ('H' = hashtag, 'M' = mention). A single {@code UNION ALL} over the
 * hashtag join and the mentions table resolves both relation families for a whole page of tweets
 * in one query, replacing the prior pair of IN-list reads. {@code userId} is null for hashtag rows.
 *
 * @param tweetId identifier of the tweet the relation belongs to
 * @param kind 'H' for a hashtag row, 'M' for a mention row
 * @param id identifier of the hashtag or mention row
 * @param userId the mentioned user's id (mention rows only; null for hashtags)
 * @param text the hashtag text or mention text
 * @author Andrei Zbarcea
 */
public record TweetRelation(UUID tweetId, String kind, UUID id, UUID userId, String text) {
}
