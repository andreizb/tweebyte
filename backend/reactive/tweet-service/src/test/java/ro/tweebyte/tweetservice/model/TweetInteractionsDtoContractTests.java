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

class TweetInteractionsDtoContractTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void builderCarriesCountsAndTopReply() {
		ReplyDto topReply = ReplyDto.builder().id(UUID.randomUUID()).content("answer").build();

		TweetInteractionsDto interactions = TweetInteractionsDto.builder()
			.likes(11L)
			.replies(12L)
			.retweets(13L)
			.topReply(topReply)
			.build();

		assertThat(interactions.getLikes()).isEqualTo(11L);
		assertThat(interactions.getReplies()).isEqualTo(12L);
		assertThat(interactions.getRetweets()).isEqualTo(13L);
		assertThat(interactions.getTopReply()).isSameAs(topReply);
	}

	@Test
	void noArgsConstructorDefaultsToZeroCountsAndNoTopReply() {
		TweetInteractionsDto interactions = new TweetInteractionsDto();

		assertThat(interactions.getLikes()).isZero();
		assertThat(interactions.getReplies()).isZero();
		assertThat(interactions.getRetweets()).isZero();
		assertThat(interactions.getTopReply()).isNull();
	}

	@Test
	void settersMutateCountsAndTopReply() {
		ReplyDto topReply = ReplyDto.builder().id(UUID.randomUUID()).build();
		TweetInteractionsDto interactions = new TweetInteractionsDto();

		interactions.setLikes(1L);
		interactions.setReplies(2L);
		interactions.setRetweets(3L);
		interactions.setTopReply(topReply);

		assertThat(interactions).isEqualTo(new TweetInteractionsDto(1L, 2L, 3L, topReply));
	}

	@Test
	void jsonDeserializationBindsTopReplyAndIgnoresUnknownFields() throws JsonProcessingException {
		UUID replyId = UUID.randomUUID();
		String json = """
				{"likes":8,"replies":5,"retweets":3,"top_reply":{"id":"%s","content":"top"},"ignored":"value"}
				""".formatted(replyId);

		TweetInteractionsDto interactions = this.objectMapper.readValue(json, TweetInteractionsDto.class);

		assertThat(interactions.getLikes()).isEqualTo(8L);
		assertThat(interactions.getReplies()).isEqualTo(5L);
		assertThat(interactions.getRetweets()).isEqualTo(3L);
		assertThat(interactions.getTopReply().getId()).isEqualTo(replyId);
		assertThat(interactions.getTopReply().getContent()).isEqualTo("top");
	}

	@Test
	void jsonSerializationUsesSnakeCaseForTopReply() throws JsonProcessingException {
		ReplyDto topReply = ReplyDto.builder().id(UUID.randomUUID()).content("reply").build();

		String json = this.objectMapper.writeValueAsString(new TweetInteractionsDto(1L, 2L, 3L, topReply));

		assertThat(json).contains("\"top_reply\"");
		assertThat(json).doesNotContain("topReply");
	}

}
