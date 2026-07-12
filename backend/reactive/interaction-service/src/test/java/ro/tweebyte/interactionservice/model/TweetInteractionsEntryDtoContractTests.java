/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TweetInteractionsEntryDtoContractTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void recordAccessorsExposeCountsAndTopReply() {
		UUID tweetId = UUID.randomUUID();
		ReplyDto topReply = new ReplyDto().setId(UUID.randomUUID()).setContent("top");

		TweetInteractionsEntryDto entry = new TweetInteractionsEntryDto(tweetId, 6L, 4L, 2L, topReply);

		assertThat(entry.tweetId()).isEqualTo(tweetId);
		assertThat(entry.likes()).isEqualTo(6L);
		assertThat(entry.replies()).isEqualTo(4L);
		assertThat(entry.retweets()).isEqualTo(2L);
		assertThat(entry.topReply()).isSameAs(topReply);
	}

	@Test
	void jsonUsesTweetIdAndTopReplyWireNames() throws JsonProcessingException {
		UUID tweetId = UUID.randomUUID();

		String json = this.objectMapper
			.writeValueAsString(new TweetInteractionsEntryDto(tweetId, 1L, 2L, 3L, null));

		assertThat(json).contains("\"tweet_id\":\"" + tweetId + "\"", "\"top_reply\":null");
		assertThat(json).doesNotContain("tweetId", "topReply");
	}

	@Test
	void nullTopReplyKeepsCountsAvailable() {
		UUID tweetId = UUID.randomUUID();

		TweetInteractionsEntryDto entry = new TweetInteractionsEntryDto(tweetId, 0L, 9L, 5L, null);

		assertThat(entry).isEqualTo(new TweetInteractionsEntryDto(tweetId, 0L, 9L, 5L, null));
		assertThat(entry.topReply()).isNull();
	}

}
