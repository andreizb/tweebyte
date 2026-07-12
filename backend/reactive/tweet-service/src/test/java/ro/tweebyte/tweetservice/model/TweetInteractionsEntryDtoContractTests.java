/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.model;

import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TweetInteractionsEntryDtoContractTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void toInteractionsCopiesCountsAndTopReply() {
		ReplyDto topReply = ReplyDto.builder().id(UUID.randomUUID()).content("useful reply").build();
		TweetInteractionsEntryDto entry = new TweetInteractionsEntryDto(UUID.randomUUID(), 7L, 3L, 2L, topReply);

		TweetInteractionsDto interactions = entry.toInteractions();

		assertThat(interactions.getLikes()).isEqualTo(7L);
		assertThat(interactions.getReplies()).isEqualTo(3L);
		assertThat(interactions.getRetweets()).isEqualTo(2L);
		assertThat(interactions.getTopReply()).isSameAs(topReply);
	}

	@Test
	void toInteractionsPreservesAbsentTopReply() {
		TweetInteractionsEntryDto entry = new TweetInteractionsEntryDto(UUID.randomUUID(), 0L, 0L, 0L, null);

		TweetInteractionsDto interactions = entry.toInteractions();

		assertThat(interactions.getLikes()).isZero();
		assertThat(interactions.getReplies()).isZero();
		assertThat(interactions.getRetweets()).isZero();
		assertThat(interactions.getTopReply()).isNull();
	}

	@Test
	void jsonUsesSnakeCaseTweetAndTopReplyNames() throws JsonProcessingException {
		UUID tweetId = UUID.randomUUID();
		ReplyDto topReply = ReplyDto.builder().id(UUID.randomUUID()).content("top").build();

		String json = this.objectMapper
			.writeValueAsString(new TweetInteractionsEntryDto(tweetId, 4L, 5L, 6L, topReply));

		assertThat(json).contains("\"tweet_id\":\"" + tweetId + "\"", "\"likes\":4", "\"replies\":5",
				"\"retweets\":6", "\"top_reply\"");
		assertThat(json).doesNotContain("tweetId", "topReply");
	}

	@Test
	void jsonDeserializationIgnoresUnknownFields() throws JsonProcessingException {
		UUID tweetId = UUID.randomUUID();
		String json = """
				{"tweet_id":"%s","likes":1,"replies":2,"retweets":3,"top_reply":null,"ignored":true}
				""".formatted(tweetId);

		TweetInteractionsEntryDto entry = this.objectMapper.readValue(json, TweetInteractionsEntryDto.class);

		assertThat(entry.tweetId()).isEqualTo(tweetId);
		assertThat(entry.likes()).isEqualTo(1L);
		assertThat(entry.replies()).isEqualTo(2L);
		assertThat(entry.retweets()).isEqualTo(3L);
		assertThat(entry.topReply()).isNull();
	}

}
