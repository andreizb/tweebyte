/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InteractionProjectionRecordTests {

	@Test
	void tweetCountCarriesAggregateTotal() {
		UUID tweetId = UUID.randomUUID();

		TweetCount count = new TweetCount(tweetId, 42L);

		assertThat(count).isEqualTo(new TweetCount(tweetId, 42L));
		assertThat(count.tweetId()).isEqualTo(tweetId);
		assertThat(count.total()).isEqualTo(42L);
	}

	@Test
	void topReplyCarriesAggregatedLikeCount() {
		UUID tweetId = UUID.randomUUID();
		UUID replyId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();

		TopReply topReply = new TopReply(tweetId, replyId, userId, "best reply", createdAt, 17L);

		assertThat(topReply.tweetId()).isEqualTo(tweetId);
		assertThat(topReply.id()).isEqualTo(replyId);
		assertThat(topReply.userId()).isEqualTo(userId);
		assertThat(topReply.content()).isEqualTo("best reply");
		assertThat(topReply.createdAt()).isEqualTo(createdAt);
		assertThat(topReply.likeCount()).isEqualTo(17L);
	}

	@Test
	void tweetInteractionRowCarriesTopReplyColumns() {
		UUID tweetId = UUID.randomUUID();
		UUID replyId = UUID.randomUUID();
		UUID authorId = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();

		TweetInteractionRow row = new TweetInteractionRow(tweetId, 9L, 5L, 2L, replyId, authorId, "reply",
				createdAt, 11L);

		assertThat(row.tweetId()).isEqualTo(tweetId);
		assertThat(row.likeCount()).isEqualTo(9L);
		assertThat(row.replyCount()).isEqualTo(5L);
		assertThat(row.retweetCount()).isEqualTo(2L);
		assertThat(row.topReplyId()).isEqualTo(replyId);
		assertThat(row.topReplyUserId()).isEqualTo(authorId);
		assertThat(row.topReplyContent()).isEqualTo("reply");
		assertThat(row.topReplyCreatedAt()).isEqualTo(createdAt);
		assertThat(row.topReplyLikeCount()).isEqualTo(11L);
	}

	@Test
	void tweetInteractionRowAllowsNoTopReplyColumns() {
		UUID tweetId = UUID.randomUUID();

		TweetInteractionRow row = new TweetInteractionRow(tweetId, 1L, 0L, 0L, null, null, null, null, null);

		assertThat(row.tweetId()).isEqualTo(tweetId);
		assertThat(row.likeCount()).isEqualTo(1L);
		assertThat(row.replyCount()).isZero();
		assertThat(row.retweetCount()).isZero();
		assertThat(row.topReplyId()).isNull();
		assertThat(row.topReplyUserId()).isNull();
		assertThat(row.topReplyContent()).isNull();
		assertThat(row.topReplyCreatedAt()).isNull();
		assertThat(row.topReplyLikeCount()).isNull();
	}

}
