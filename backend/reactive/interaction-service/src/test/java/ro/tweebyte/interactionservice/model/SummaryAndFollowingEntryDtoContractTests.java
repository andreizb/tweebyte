/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SummaryAndFollowingEntryDtoContractTests {

	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
		.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

	@Test
	void tweetSummaryDtoBindsTokenListsAndOmitsNulls() throws JsonProcessingException {
		UUID id = UUID.randomUUID();
		TweetSummaryDto summary = this.objectMapper
			.readValue("{\"id\":\"" + id + "\",\"hashtags\":[\"java\"],\"mentions\":[\"alice\"],\"ignored\":1}",
					TweetSummaryDto.class);

		String json = this.objectMapper.writeValueAsString(new TweetSummaryDto(id, null, null));

		assertThat(summary.getId()).isEqualTo(id);
		assertThat(summary.getHashtags()).containsExactly("java");
		assertThat(summary.getMentions()).containsExactly("alice");
		assertThat(json).contains("\"id\":\"" + id + "\"").doesNotContain("hashtags", "mentions");
	}

	@Test
	void followingEntryDtoUsesWireNamesAndIsoCreatedAt() throws JsonProcessingException {
		UUID followedId = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.of(2026, 7, 5, 12, 30);
		FollowingEntryDto entry = new FollowingEntryDto(followedId, "alice", createdAt);

		String json = this.objectMapper.writeValueAsString(entry);
		FollowingEntryDto roundTripped = this.objectMapper.readValue(json, FollowingEntryDto.class);

		assertThat(json).contains("\"followed_id\":\"" + followedId + "\"", "\"user_name\":\"alice\"",
				"\"created_at\":\"2026-07-05T12:30:00\"");
		assertThat(json).doesNotContain("followedId", "userName", "createdAt");
		assertThat(roundTripped).isEqualTo(entry);
	}

}
