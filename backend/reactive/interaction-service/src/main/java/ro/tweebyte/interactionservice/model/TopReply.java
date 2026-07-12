/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * R2DBC projection for the top-reply-by-likes query. ReplyEntity has no field for the
 * aggregated like count, so mapping the query onto it dropped the computed
 * {@code like_count}. This projection carries it through so the ReplyDto built in
 * ReplyService.getTopReplyForTweet reports likesCount, matching the async stack's JPQL
 * constructor projection. {@code tweetId} keys the row to its tweet in the batched
 * top-reply query.
 *
 * @param tweetId id of the tweet this reply belongs to
 * @param id id of the reply row
 * @param userId id of the author who wrote the reply
 * @param content body text of the reply
 * @param createdAt timestamp the reply was created
 * @param likeCount aggregated number of likes the reply received
 * @author Andrei Zbarcea
 */
public record TopReply(UUID tweetId, UUID id, UUID userId, String content, LocalDateTime createdAt, Long likeCount) {
}
