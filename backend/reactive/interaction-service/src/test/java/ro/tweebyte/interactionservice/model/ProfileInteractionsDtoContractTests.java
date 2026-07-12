/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileInteractionsDtoContractTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void allArgsConstructorGroupsCountsAndTweetInteractions() {
		FollowCountsDto counts = new FollowCountsDto(10L, 4L);
		TweetInteractionsEntryDto entry = new TweetInteractionsEntryDto(UUID.randomUUID(), 3L, 2L, 1L, null);

		ProfileInteractionsDto profile = new ProfileInteractionsDto(counts, List.of(entry));

		assertThat(profile.getFollowCounts()).isSameAs(counts);
		assertThat(profile.getTweetInteractions()).containsExactly(entry);
	}

	@Test
	void chainSettersReturnSameInstance() {
		FollowCountsDto counts = new FollowCountsDto(1L, 2L);
		List<TweetInteractionsEntryDto> entries = List.of(new TweetInteractionsEntryDto(UUID.randomUUID(), 0L, 0L, 0L,
				null));
		ProfileInteractionsDto profile = new ProfileInteractionsDto();

		ProfileInteractionsDto returned = profile.setFollowCounts(counts).setTweetInteractions(entries);

		assertThat(returned).isSameAs(profile);
		assertThat(profile.getFollowCounts()).isSameAs(counts);
		assertThat(profile.getTweetInteractions()).isSameAs(entries);
	}

	@Test
	void jsonUsesNestedWireFieldNames() throws JsonProcessingException {
		UUID tweetId = UUID.randomUUID();
		ProfileInteractionsDto profile = new ProfileInteractionsDto(new FollowCountsDto(8L, 13L),
				List.of(new TweetInteractionsEntryDto(tweetId, 5L, 3L, 2L, null)));

		String json = this.objectMapper.writeValueAsString(profile);

		assertThat(json).contains("\"follow_counts\"", "\"tweet_interactions\"", "\"tweet_id\":\"" + tweetId + "\"",
				"\"followers\":8", "\"following\":13");
		assertThat(json).doesNotContain("followCounts", "tweetInteractions", "tweetId");
	}

	@Test
	void jsonDeserializationBindsNestedEntriesAndIgnoresUnknownFields() throws JsonProcessingException {
		UUID tweetId = UUID.randomUUID();
		String json = """
				{"follow_counts":{"followers":2,"following":6},"tweet_interactions":[{"tweet_id":"%s","likes":4,"replies":1,"retweets":0,"top_reply":null}],"ignored":"value"}
				""".formatted(tweetId);

		ProfileInteractionsDto profile = this.objectMapper.readValue(json, ProfileInteractionsDto.class);

		assertThat(profile.getFollowCounts()).isEqualTo(new FollowCountsDto(2L, 6L));
		assertThat(profile.getTweetInteractions()).containsExactly(new TweetInteractionsEntryDto(tweetId, 4L, 1L, 0L,
				null));
	}

}
