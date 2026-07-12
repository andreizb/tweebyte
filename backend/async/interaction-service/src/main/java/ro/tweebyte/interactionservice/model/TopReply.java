/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Projection for the batched top-reply-by-likes query. Carries the aggregated
 * {@code likeCount} that ReplyEntity has no field for, mirroring the single
 * findTopReplyByLikesForTweetId constructor projection. {@code tweetId} keys the row to
 * its tweet so one query resolves the top reply for a whole page of tweets; the service
 * picks the first row per tweet (the query orders by likeCount desc, createdAt desc
 * within each tweet).
 *
 * @param tweetId identifier of the tweet the top reply belongs to
 * @param id identifier of the reply
 * @param userId identifier of the user who authored the reply
 * @param content the reply text
 * @param createdAt timestamp when the reply was created
 * @param likeCount aggregated number of likes the reply has received
 * @author Andrei Zbarcea
 */
public record TopReply(UUID tweetId, UUID id, UUID userId, String content, LocalDateTime createdAt, Long likeCount) {
}
