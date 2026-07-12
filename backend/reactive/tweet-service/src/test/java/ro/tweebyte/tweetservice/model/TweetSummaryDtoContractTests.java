/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.model;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TweetSummaryDtoContractTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void allArgsConstructorCarriesSummaryTokens() {
		UUID id = UUID.randomUUID();
		List<String> hashtags = List.of("java", "webflux");
		List<String> mentions = List.of("alice", "bob");

		TweetSummaryDto summary = new TweetSummaryDto(id, hashtags, mentions);

		assertThat(summary.getId()).isEqualTo(id);
		assertThat(summary.getHashtags()).isEqualTo(hashtags);
		assertThat(summary.getMentions()).isEqualTo(mentions);
	}

	@Test
	void settersReplaceSummaryTokenLists() {
		TweetSummaryDto summary = new TweetSummaryDto();
		UUID id = UUID.randomUUID();

		summary.setId(id);
		summary.setHashtags(List.of("cache"));
		summary.setMentions(List.of("carol"));

		assertThat(summary.getId()).isEqualTo(id);
		assertThat(summary.getHashtags()).containsExactly("cache");
		assertThat(summary.getMentions()).containsExactly("carol");
	}

	@Test
	void jsonDeserializationIgnoresUnknownFields() throws JsonProcessingException {
		UUID id = UUID.randomUUID();
		String json = """
				{"id":"%s","hashtags":["java"],"mentions":["alice"],"ignored":"value"}
				""".formatted(id);

		TweetSummaryDto summary = this.objectMapper.readValue(json, TweetSummaryDto.class);

		assertThat(summary.getId()).isEqualTo(id);
		assertThat(summary.getHashtags()).containsExactly("java");
		assertThat(summary.getMentions()).containsExactly("alice");
	}

	@Test
	void jsonSerializationOmitsNullTokenLists() throws JsonProcessingException {
		UUID id = UUID.randomUUID();

		String json = this.objectMapper.writeValueAsString(new TweetSummaryDto(id, null, null));

		assertThat(json).contains("\"id\":\"" + id + "\"");
		assertThat(json).doesNotContain("hashtags", "mentions");
	}

	@Test
	void jsonSerializationKeepsEmptyTokenLists() throws JsonProcessingException {
		UUID id = UUID.randomUUID();

		String json = this.objectMapper.writeValueAsString(new TweetSummaryDto(id, List.of(), List.of()));

		assertThat(json).contains("\"hashtags\":[]", "\"mentions\":[]");
	}

}
