/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One consolidated interaction row per tweet for {@code ReplyRepository.findInteractionsForTweets}:
 * the like/reply/retweet counts plus the tweet's top reply, resolved in a single query so the
 * page's cold-cache load costs one r2dbc connection acquire instead of four. The top-reply columns
 * are null when the tweet has no reply (the LATERAL produced no row).
 *
 * @param tweetId the tweet these interactions belong to
 * @param likeCount number of likes on the tweet
 * @param replyCount number of replies on the tweet
 * @param retweetCount number of retweets of the tweet
 * @param topReplyId id of the tweet's top reply, or null when it has none
 * @param topReplyUserId author of the top reply, or null
 * @param topReplyContent body of the top reply, or null
 * @param topReplyCreatedAt creation timestamp of the top reply, or null
 * @param topReplyLikeCount likes on the top reply, or null
 * @author Andrei Zbarcea
 */
public record TweetInteractionRow(UUID tweetId, Long likeCount, Long replyCount, Long retweetCount, UUID topReplyId,
		UUID topReplyUserId, String topReplyContent, LocalDateTime topReplyCreatedAt, Long topReplyLikeCount) {
}
