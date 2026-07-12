/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Spring Data interface projection for the consolidated per-tweet interaction native query
 * ({@code ReplyRepository.findInteractionsForTweets}): the like/reply/retweet counts plus the
 * tweet's top reply, resolved in a single query so the page's cold-cache load costs one Hikari
 * connection acquire instead of four. The top-reply getters return null when the tweet has no
 * reply. The native query's quoted camelCase aliases map to these getters.
 *
 * @author Andrei Zbarcea
 */
public interface TweetInteractionRow {

	UUID getTweetId();

	Long getLikeCount();

	Long getReplyCount();

	Long getRetweetCount();

	UUID getTopReplyId();

	UUID getTopReplyUserId();

	String getTopReplyContent();

	LocalDateTime getTopReplyCreatedAt();

	Long getTopReplyLikeCount();

}
