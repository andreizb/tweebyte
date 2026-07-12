/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.model;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserWireContractTests {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Test
	void mediaAccessRequestBuilderCarriesPassword() {
		MediaAccessRequest request = MediaAccessRequest.builder().password("gate-pass").build();

		assertThat(request.getPassword()).isEqualTo("gate-pass");
	}

	@Test
	void mediaAccessRequestRejectsBlankPassword() {
		MediaAccessRequest request = MediaAccessRequest.builder().password("   ").build();

		assertThat(violationsFor(request)).extracting(ConstraintViolation::getMessage)
			.contains("Password is required");
	}

	@Test
	void mediaAccessRequestRejectsMissingPassword() {
		MediaAccessRequest request = new MediaAccessRequest();

		assertThat(violationsFor(request)).extracting(ConstraintViolation::getMessage)
			.contains("Password is required");
	}

	@Test
	void mediaAccessRequestJsonUsesPasswordAndIgnoresUnknownFields() throws JsonProcessingException {
		String json = "{\"password\":\"gate-pass\",\"ignored\":\"value\"}";

		MediaAccessRequest request = MAPPER.readValue(json, MediaAccessRequest.class);

		assertThat(request.getPassword()).isEqualTo("gate-pass");
		assertThat(MAPPER.writeValueAsString(request)).contains("\"password\":\"gate-pass\"");
	}

	@Test
	void followCountsJsonRoundTripsFollowersAndFollowing() throws JsonProcessingException {
		FollowCountsDto counts = new FollowCountsDto(7L, 11L);

		FollowCountsDto roundTripped = MAPPER.readValue(MAPPER.writeValueAsString(counts), FollowCountsDto.class);

		assertThat(roundTripped.getFollowers()).isEqualTo(7L);
		assertThat(roundTripped.getFollowing()).isEqualTo(11L);
	}

	@Test
	void followCountsChainAccessorsReturnSameInstance() {
		FollowCountsDto counts = new FollowCountsDto();

		FollowCountsDto returned = counts.setFollowers(3L).setFollowing(5L);

		assertThat(returned).isSameAs(counts);
		assertThat(counts.getFollowers()).isEqualTo(3L);
		assertThat(counts.getFollowing()).isEqualTo(5L);
	}

	@Test
	void profileInteractionsJsonUsesSnakeCaseEnvelopeFields() throws JsonProcessingException {
		UUID tweetId = UUID.randomUUID();
		ProfileInteractionsDto dto = new ProfileInteractionsDto(new FollowCountsDto(1L, 2L),
				List.of(new TweetInteractionsEntryDto(tweetId, 3L, 4L, 5L, null)));

		String json = MAPPER.writeValueAsString(dto);

		assertThat(json).contains("\"follow_counts\"", "\"tweet_interactions\"", "\"tweet_id\":\"" + tweetId + "\"");
		assertThat(json).doesNotContain("followCounts", "tweetInteractions", "tweetId");
	}

	@Test
	void profileInteractionsChainAccessorsReturnSameInstance() {
		FollowCountsDto counts = new FollowCountsDto(1L, 2L);
		List<TweetInteractionsEntryDto> interactions = List.of();
		ProfileInteractionsDto dto = new ProfileInteractionsDto();

		ProfileInteractionsDto returned = dto.setFollowCounts(counts).setTweetInteractions(interactions);

		assertThat(returned).isSameAs(dto);
		assertThat(dto.getFollowCounts()).isSameAs(counts);
		assertThat(dto.getTweetInteractions()).isSameAs(interactions);
	}

	@Test
	void tweetInteractionsEntryJsonUsesSnakeCaseFields() throws JsonProcessingException {
		UUID tweetId = UUID.randomUUID();
		TweetDto.ReplyDto topReply = TweetDto.ReplyDto.builder().id(UUID.randomUUID()).content("top").build();
		TweetInteractionsEntryDto entry = new TweetInteractionsEntryDto(tweetId, 9L, 8L, 7L, topReply);

		String json = MAPPER.writeValueAsString(entry);

		assertThat(json).contains("\"tweet_id\":\"" + tweetId + "\"", "\"top_reply\"");
		assertThat(json).doesNotContain("tweetId", "topReply");
	}

	@Test
	void tweetInteractionsEntryCarriesTopReply() {
		TweetDto.ReplyDto topReply = TweetDto.ReplyDto.builder().id(UUID.randomUUID()).content("top").build();
		TweetInteractionsEntryDto entry = new TweetInteractionsEntryDto(UUID.randomUUID(), 1L, 2L, 3L, topReply);

		assertThat(entry.getTopReply()).isSameAs(topReply);
		assertThat(entry.getLikes()).isEqualTo(1L);
		assertThat(entry.getReplies()).isEqualTo(2L);
		assertThat(entry.getRetweets()).isEqualTo(3L);
	}

	@Test
	void tweetInteractionsEntryIgnoresUnknownJsonFields() throws JsonProcessingException {
		UUID tweetId = UUID.randomUUID();
		String json = "{\"tweet_id\":\"" + tweetId + "\",\"likes\":1,\"replies\":2,\"retweets\":3,"
				+ "\"unknown\":\"value\"}";

		TweetInteractionsEntryDto entry = MAPPER.readValue(json, TweetInteractionsEntryDto.class);

		assertThat(entry.getTweetId()).isEqualTo(tweetId);
		assertThat(entry.getLikes()).isEqualTo(1L);
	}

	private static Set<ConstraintViolation<MediaAccessRequest>> violationsFor(MediaAccessRequest request) {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			return factory.getValidator().validate(request);
		}
	}

}
