/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.repository;

import java.util.List;

import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;

/**
 * Aggregate of a tweet and its eagerly-joined relations, reconstructed in memory from the
 * flat row set the single-join update read returns ({@link TweetRepositoryCustom}). It is
 * the R2DBC equivalent of the async stack's {@code @EntityGraph(hashtags, mentions)} load:
 * the "before" side of the tweet-update diff, fetched in one SELECT instead of three.
 *
 * @param tweet the owning tweet row (id, user_id, version, content, created_at, media_ids)
 * @param hashtags the hashtag entities currently linked to the tweet, de-duplicated by id
 * @param mentions the mention entities currently attached to the tweet, de-duplicated by id
 * @author Andrei Zbarcea
 */
public record TweetWithRelations(TweetEntity tweet, List<HashtagEntity> hashtags, List<MentionEntity> mentions) {
}
