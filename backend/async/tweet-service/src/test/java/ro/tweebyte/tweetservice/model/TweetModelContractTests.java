/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TweetModelContractTests {

	private static ObjectMapper mapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new JavaTimeModule());
		mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		return mapper;
	}

	@Test
	void creationRequestGetsMediaIds() {
		UUID[] mediaIds = { UUID.randomUUID(), UUID.randomUUID() };
		TweetCreationRequest request = new TweetCreationRequest();
		request.setMediaIds(mediaIds);
		assertThat(request.getMediaIds()).containsExactly(mediaIds);
	}

	@Test
	void creationRequestSetsMediaIds() {
		UUID[] mediaIds = { UUID.randomUUID() };
		TweetCreationRequest request = new TweetCreationRequest();
		assertThat(request.getMediaIds()).isNull();
		request.setMediaIds(mediaIds);
		assertThat(request.getMediaIds()).containsExactly(mediaIds);
	}

	@Test
	void creationRequestBuilderIncludesMediaIds() {
		UUID[] mediaIds = { UUID.randomUUID() };
		TweetCreationRequest request = TweetCreationRequest.builder()
			.id(UUID.randomUUID())
			.userId(UUID.randomUUID())
			.content("A long enough tweet")
			.mediaIds(mediaIds)
			.build();
		assertThat(request.getMediaIds()).containsExactly(mediaIds);
	}

	@Test
	void creationRequestAllArgsConstructorIncludesMediaIds() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID[] mediaIds = { UUID.randomUUID() };
		TweetCreationRequest request = new TweetCreationRequest(id, userId, "A long enough tweet", mediaIds);
		assertThat(request.getId()).isEqualTo(id);
		assertThat(request.getUserId()).isEqualTo(userId);
		assertThat(request.getMediaIds()).containsExactly(mediaIds);
	}

	@Test
	void creationRequestChainAccessorsReturnSameInstance() {
		TweetCreationRequest request = new TweetCreationRequest();
		TweetCreationRequest returned = request.setContent("A long enough tweet").setMediaIds(new UUID[] {});
		assertThat(returned).isSameAs(request);
		assertThat(request.getContent()).isEqualTo("A long enough tweet");
	}

	@Test
	void creationRequestEqualsAndHashCodeUseState() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		TweetCreationRequest first = TweetCreationRequest.builder().id(id).userId(userId).content("A long enough tweet").build();
		TweetCreationRequest second = TweetCreationRequest.builder().id(id).userId(userId).content("A long enough tweet").build();
		assertThat(second).isEqualTo(first).hasSameHashCodeAs(first);
	}

	@Test
	void creationRequestToStringIncludesContent() {
		TweetCreationRequest request = TweetCreationRequest.builder().content("A long enough tweet").build();
		assertThat(request.toString()).contains("A long enough tweet");
	}

	@Test
	void creationRequestJsonUsesSnakeCaseMediaIds() throws JsonProcessingException {
		UUID mediaId = UUID.randomUUID();
		TweetCreationRequest request = TweetCreationRequest.builder()
			.content("A long enough tweet")
			.mediaIds(new UUID[] { mediaId })
			.build();
		String json = mapper().writeValueAsString(request);
		assertThat(json).contains("\"media_ids\":[\"" + mediaId + "\"]").doesNotContain("mediaIds");
	}

	@Test
	void creationRequestIgnoresUnknownJsonFields() throws JsonProcessingException {
		String json = "{\"content\":\"A long enough tweet\",\"ignored\":\"value\"}";
		TweetCreationRequest request = mapper().readValue(json, TweetCreationRequest.class);
		assertThat(request.getContent()).isEqualTo("A long enough tweet");
	}

	@Test
	void creationRequestOmitsNullFieldsFromJson() throws JsonProcessingException {
		TweetCreationRequest request = TweetCreationRequest.builder().content("A long enough tweet").build();
		String json = mapper().writeValueAsString(request);
		assertThat(json).contains("\"content\":\"A long enough tweet\"").doesNotContain("media_ids", "userId", "id");
	}

	@Test
	void tweetDtoGetsMediaIds() {
		UUID[] mediaIds = { UUID.randomUUID(), UUID.randomUUID() };
		TweetDto dto = new TweetDto();
		dto.setMediaIds(mediaIds);
		assertThat(dto.getMediaIds()).containsExactly(mediaIds);
	}

	@Test
	void tweetDtoSetsMediaIds() {
		UUID[] mediaIds = { UUID.randomUUID() };
		TweetDto dto = new TweetDto();
		assertThat(dto.getMediaIds()).isNull();
		dto.setMediaIds(mediaIds);
		assertThat(dto.getMediaIds()).containsExactly(mediaIds);
	}

	@Test
	void tweetDtoBuilderIncludesMediaIds() {
		UUID[] mediaIds = { UUID.randomUUID() };
		TweetDto dto = TweetDto.builder().id(UUID.randomUUID()).content("tweet").mediaIds(mediaIds).build();
		assertThat(dto.getMediaIds()).containsExactly(mediaIds);
	}

	@Test
	void tweetDtoAllArgsConstructorIncludesEveryField() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.now();
		UUID[] mediaIds = { UUID.randomUUID() };
		Set<MentionDto> mentions = new HashSet<>();
		Set<HashtagDto> hashtags = new HashSet<>();
		ReplyDto topReply = new ReplyDto();
		List<ReplyDto> replies = new ArrayList<>();
		UserDto user = new UserDto();

		TweetDto dto = new TweetDto(id, userId, "tweet", createdAt, mediaIds, mentions, hashtags, 1L, 2L, 3L,
				topReply, replies, user);

		assertThat(dto.getId()).isEqualTo(id);
		assertThat(dto.getUserId()).isEqualTo(userId);
		assertThat(dto.getCreatedAt()).isEqualTo(createdAt);
		assertThat(dto.getMediaIds()).containsExactly(mediaIds);
		assertThat(dto.getTopReply()).isSameAs(topReply);
		assertThat(dto.getReplies()).isSameAs(replies);
		assertThat(dto.getUser()).isSameAs(user);
	}

	@Test
	void tweetDtoEqualsAndHashCodeUseState() {
		UUID id = UUID.randomUUID();
		TweetDto first = TweetDto.builder().id(id).content("tweet").likesCount(1L).build();
		TweetDto second = TweetDto.builder().id(id).content("tweet").likesCount(1L).build();
		assertThat(second).isEqualTo(first).hasSameHashCodeAs(first);
	}

	@Test
	void tweetDtoToStringIncludesContent() {
		TweetDto dto = TweetDto.builder().content("tweet body").build();
		assertThat(dto.toString()).contains("tweet body");
	}

	@Test
	void tweetDtoJsonUsesSnakeCaseFields() throws JsonProcessingException {
		UUID userId = UUID.randomUUID();
		UUID mediaId = UUID.randomUUID();
		TweetDto dto = TweetDto.builder().userId(userId).mediaIds(new UUID[] { mediaId }).likesCount(4L).build();
		String json = mapper().writeValueAsString(dto);
		assertThat(json).contains("\"user_id\":\"" + userId + "\"", "\"media_ids\":[\"" + mediaId + "\"]",
				"\"likes_count\":4");
		assertThat(json).doesNotContain("userId", "mediaIds", "likesCount");
	}

	@Test
	void tweetDtoIgnoresUnknownJsonFields() throws JsonProcessingException {
		String json = "{\"content\":\"tweet\",\"unknown\":\"value\"}";
		TweetDto dto = mapper().readValue(json, TweetDto.class);
		assertThat(dto.getContent()).isEqualTo("tweet");
	}

	@Test
	void tweetDtoOmitsNullFieldsFromJson() throws JsonProcessingException {
		TweetDto dto = TweetDto.builder().content("tweet").build();
		String json = mapper().writeValueAsString(dto);
		assertThat(json).contains("\"content\":\"tweet\"").doesNotContain("media_ids", "user_id", "likes_count");
	}

	@Test
	void tweetDtoRoundTripsDateAndCounts() throws JsonProcessingException {
		LocalDateTime createdAt = LocalDateTime.of(2026, 7, 5, 12, 30);
		TweetDto dto = TweetDto.builder().createdAt(createdAt).repliesCount(2L).retweetsCount(3L).build();
		String json = mapper().writeValueAsString(dto);
		TweetDto roundTripped = mapper().readValue(json, TweetDto.class);
		assertThat(roundTripped.getCreatedAt()).isEqualTo(createdAt);
		assertThat(roundTripped.getRepliesCount()).isEqualTo(2L);
		assertThat(roundTripped.getRetweetsCount()).isEqualTo(3L);
	}

}
