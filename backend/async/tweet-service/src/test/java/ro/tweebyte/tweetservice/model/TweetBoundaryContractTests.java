/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.model;

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

class TweetBoundaryContractTests {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Test
	void updateRequestImplementsTweetRequestView() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		TweetRequest request = TweetUpdateRequest.builder().id(id).userId(userId).content("long enough text").build();

		assertThat(request.getId()).isEqualTo(id);
		assertThat(request.getUserId()).isEqualTo(userId);
		assertThat(request.getContent()).isEqualTo("long enough text");
	}

	@Test
	void creationRequestImplementsTweetRequestView() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		TweetRequest request = TweetCreationRequest.builder().id(id).userId(userId).content("long enough text").build();

		assertThat(request.getId()).isEqualTo(id);
		assertThat(request.getUserId()).isEqualTo(userId);
		assertThat(request.getContent()).isEqualTo("long enough text");
	}

	@Test
	void updateRequestBuilderIncludesEveryServiceManagedField() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		TweetUpdateRequest request = TweetUpdateRequest.builder()
			.id(id)
			.userId(userId)
			.content("long enough text")
			.build();

		assertThat(request.getId()).isEqualTo(id);
		assertThat(request.getUserId()).isEqualTo(userId);
		assertThat(request.getContent()).isEqualTo("long enough text");
	}

	@Test
	void updateRequestAllArgsConstructorPreservesState() {
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		TweetUpdateRequest request = new TweetUpdateRequest(id, userId, "long enough text");

		assertThat(request.getId()).isEqualTo(id);
		assertThat(request.getUserId()).isEqualTo(userId);
		assertThat(request.getContent()).isEqualTo("long enough text");
	}

	@Test
	void updateRequestChainAccessorsReturnSameInstance() {
		TweetUpdateRequest request = new TweetUpdateRequest();

		TweetUpdateRequest returned = request.setId(UUID.randomUUID())
			.setUserId(UUID.randomUUID())
			.setContent("long enough text");

		assertThat(returned).isSameAs(request);
	}

	@Test
	void updateRequestRejectsBlankContent() {
		TweetUpdateRequest request = TweetUpdateRequest.builder().content("   ").build();

		assertThat(violationsFor(request)).extracting(ConstraintViolation::getMessage)
			.contains("Content is required");
	}

	@Test
	void updateRequestRejectsShortContent() {
		TweetUpdateRequest request = TweetUpdateRequest.builder().content("too short").build();

		assertThat(violationsFor(request)).extracting(ConstraintViolation::getMessage)
			.contains("Content must be at least 10 characters long");
	}

	@Test
	void updateRequestAcceptsTenCharacterContent() {
		TweetUpdateRequest request = TweetUpdateRequest.builder().content("1234567890").build();

		assertThat(violationsFor(request)).isEmpty();
	}

	@Test
	void updateRequestIgnoresUnknownJsonFields() throws JsonProcessingException {
		TweetUpdateRequest request = MAPPER.readValue("{\"content\":\"long enough text\",\"ignored\":true}",
				TweetUpdateRequest.class);

		assertThat(request.getContent()).isEqualTo("long enough text");
	}

	@Test
	void updateRequestOmitsEmptyContentFromJson() throws JsonProcessingException {
		TweetUpdateRequest request = TweetUpdateRequest.builder().content("").build();

		assertThat(MAPPER.writeValueAsString(request)).doesNotContain("content");
	}

	@Test
	void creationRequestRejectsBlankContent() {
		TweetCreationRequest request = TweetCreationRequest.builder().content("   ").build();

		assertThat(violationsFor(request)).extracting(ConstraintViolation::getMessage)
			.contains("Content is required");
	}

	@Test
	void creationRequestRejectsShortContent() {
		TweetCreationRequest request = TweetCreationRequest.builder().content("too short").build();

		assertThat(violationsFor(request)).extracting(ConstraintViolation::getMessage)
			.contains("Content must be at least 10 characters long");
	}

	@Test
	void creationRequestAcceptsValidContentWithMediaIds() {
		TweetCreationRequest request = TweetCreationRequest.builder()
			.content("long enough text")
			.mediaIds(new UUID[] { UUID.randomUUID() })
			.build();

		assertThat(violationsFor(request)).isEmpty();
	}

	@Test
	void interactionsDtoBuilderCarriesCountsAndTopReply() {
		ReplyDto topReply = ReplyDto.builder().id(UUID.randomUUID()).content("top").build();

		TweetInteractionsDto dto = TweetInteractionsDto.builder()
			.likes(1L)
			.replies(2L)
			.retweets(3L)
			.topReply(topReply)
			.build();

		assertThat(dto.getLikes()).isEqualTo(1L);
		assertThat(dto.getReplies()).isEqualTo(2L);
		assertThat(dto.getRetweets()).isEqualTo(3L);
		assertThat(dto.getTopReply()).isSameAs(topReply);
	}

	@Test
	void interactionsDtoJsonUsesTopReplySnakeCase() throws JsonProcessingException {
		TweetInteractionsDto dto = TweetInteractionsDto.builder()
			.likes(1L)
			.replies(2L)
			.retweets(3L)
			.topReply(ReplyDto.builder().content("top").build())
			.build();

		String json = MAPPER.writeValueAsString(dto);

		assertThat(json).contains("\"top_reply\"");
		assertThat(json).doesNotContain("topReply");
	}

	@Test
	void interactionsDtoIgnoresUnknownFields() throws JsonProcessingException {
		TweetInteractionsDto dto = MAPPER.readValue("{\"likes\":1,\"replies\":2,\"retweets\":3,\"ignored\":true}",
				TweetInteractionsDto.class);

		assertThat(dto.getLikes()).isEqualTo(1L);
		assertThat(dto.getReplies()).isEqualTo(2L);
		assertThat(dto.getRetweets()).isEqualTo(3L);
	}

	@Test
	void interactionsEntryToInteractionsCopiesCountsAndTopReply() {
		ReplyDto topReply = ReplyDto.builder().id(UUID.randomUUID()).content("top").build();
		TweetInteractionsEntryDto entry = new TweetInteractionsEntryDto(UUID.randomUUID(), 4L, 5L, 6L, topReply);

		TweetInteractionsDto dto = entry.toInteractions();

		assertThat(dto.getLikes()).isEqualTo(4L);
		assertThat(dto.getReplies()).isEqualTo(5L);
		assertThat(dto.getRetweets()).isEqualTo(6L);
		assertThat(dto.getTopReply()).isSameAs(topReply);
	}

	@Test
	void interactionsEntryJsonUsesSnakeCase() throws JsonProcessingException {
		UUID tweetId = UUID.randomUUID();
		TweetInteractionsEntryDto entry = new TweetInteractionsEntryDto(tweetId, 4L, 5L, 6L, null);

		String json = MAPPER.writeValueAsString(entry);

		assertThat(json).contains("\"tweet_id\":\"" + tweetId + "\"", "\"top_reply\":null");
		assertThat(json).doesNotContain("tweetId", "topReply");
	}

	@Test
	void interactionsEntryIgnoresUnknownFields() throws JsonProcessingException {
		UUID tweetId = UUID.randomUUID();
		String json = "{\"tweet_id\":\"" + tweetId + "\",\"likes\":4,\"replies\":5,\"retweets\":6,\"ignored\":true}";

		TweetInteractionsEntryDto entry = MAPPER.readValue(json, TweetInteractionsEntryDto.class);

		assertThat(entry.tweetId()).isEqualTo(tweetId);
		assertThat(entry.likes()).isEqualTo(4L);
	}

	@Test
	void interactionsEntryRecordEqualityUsesState() {
		UUID tweetId = UUID.randomUUID();

		TweetInteractionsEntryDto first = new TweetInteractionsEntryDto(tweetId, 1L, 2L, 3L, null);
		TweetInteractionsEntryDto second = new TweetInteractionsEntryDto(tweetId, 1L, 2L, 3L, null);

		assertThat(second).isEqualTo(first).hasSameHashCodeAs(first);
	}

	@Test
	void summaryAllArgsConstructorPreservesLists() {
		UUID id = UUID.randomUUID();
		List<String> hashtags = List.of("java", "spring");
		List<String> mentions = List.of("alice");

		TweetSummaryDto summary = new TweetSummaryDto(id, hashtags, mentions);

		assertThat(summary.getId()).isEqualTo(id);
		assertThat(summary.getHashtags()).isSameAs(hashtags);
		assertThat(summary.getMentions()).isSameAs(mentions);
	}

	@Test
	void summaryJsonUsesIdHashtagsAndMentions() throws JsonProcessingException {
		UUID id = UUID.randomUUID();
		TweetSummaryDto summary = new TweetSummaryDto(id, List.of("java"), List.of("alice"));

		String json = MAPPER.writeValueAsString(summary);

		assertThat(json).contains("\"id\":\"" + id + "\"", "\"hashtags\":[\"java\"]",
				"\"mentions\":[\"alice\"]");
	}

	@Test
	void summaryOmitsNullCollections() throws JsonProcessingException {
		TweetSummaryDto summary = new TweetSummaryDto(UUID.randomUUID(), null, null);

		assertThat(MAPPER.writeValueAsString(summary)).doesNotContain("hashtags", "mentions");
	}

	@Test
	void summaryIgnoresUnknownFields() throws JsonProcessingException {
		UUID id = UUID.randomUUID();

		TweetSummaryDto summary = MAPPER.readValue("{\"id\":\"" + id + "\",\"ignored\":true}",
				TweetSummaryDto.class);

		assertThat(summary.getId()).isEqualTo(id);
	}

	@Test
	void summaryEqualsAndHashCodeUseState() {
		UUID id = UUID.randomUUID();

		TweetSummaryDto first = new TweetSummaryDto(id, List.of("java"), List.of("alice"));
		TweetSummaryDto second = new TweetSummaryDto(id, List.of("java"), List.of("alice"));

		assertThat(second).isEqualTo(first).hasSameHashCodeAs(first);
	}

	@Test
	void tweetRelationRecordAccessorsExposeHashtagRows() {
		UUID tweetId = UUID.randomUUID();
		UUID id = UUID.randomUUID();

		TweetRelation relation = new TweetRelation(tweetId, "H", id, null, "java");

		assertThat(relation.tweetId()).isEqualTo(tweetId);
		assertThat(relation.kind()).isEqualTo("H");
		assertThat(relation.id()).isEqualTo(id);
		assertThat(relation.userId()).isNull();
		assertThat(relation.text()).isEqualTo("java");
	}

	@Test
	void tweetRelationRecordAccessorsExposeMentionRows() {
		UUID userId = UUID.randomUUID();

		TweetRelation relation = new TweetRelation(UUID.randomUUID(), "M", UUID.randomUUID(), userId, "alice");

		assertThat(relation.kind()).isEqualTo("M");
		assertThat(relation.userId()).isEqualTo(userId);
		assertThat(relation.text()).isEqualTo("alice");
	}

	@Test
	void tweetRelationEqualityUsesEveryColumn() {
		UUID tweetId = UUID.randomUUID();
		UUID id = UUID.randomUUID();

		TweetRelation first = new TweetRelation(tweetId, "H", id, null, "java");
		TweetRelation second = new TweetRelation(tweetId, "H", id, null, "java");

		assertThat(second).isEqualTo(first).hasSameHashCodeAs(first);
	}

	@Test
	void tweetHashtagRecordAccessorsExposeJoinProjection() {
		UUID tweetId = UUID.randomUUID();
		UUID id = UUID.randomUUID();

		TweetHashtag hashtag = new TweetHashtag(tweetId, id, "java");

		assertThat(hashtag.tweetId()).isEqualTo(tweetId);
		assertThat(hashtag.id()).isEqualTo(id);
		assertThat(hashtag.text()).isEqualTo("java");
	}

	@Test
	void tweetHashtagEqualityUsesState() {
		UUID tweetId = UUID.randomUUID();
		UUID id = UUID.randomUUID();

		TweetHashtag first = new TweetHashtag(tweetId, id, "java");
		TweetHashtag second = new TweetHashtag(tweetId, id, "java");

		assertThat(second).isEqualTo(first).hasSameHashCodeAs(first);
	}

	@Test
	void tweetMentionRecordAccessorsExposeJoinProjection() {
		UUID tweetId = UUID.randomUUID();
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		TweetMention mention = new TweetMention(tweetId, id, userId, "alice");

		assertThat(mention.tweetId()).isEqualTo(tweetId);
		assertThat(mention.id()).isEqualTo(id);
		assertThat(mention.userId()).isEqualTo(userId);
		assertThat(mention.text()).isEqualTo("alice");
	}

	@Test
	void tweetMentionEqualityUsesState() {
		UUID tweetId = UUID.randomUUID();
		UUID id = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		TweetMention first = new TweetMention(tweetId, id, userId, "alice");
		TweetMention second = new TweetMention(tweetId, id, userId, "alice");

		assertThat(second).isEqualTo(first).hasSameHashCodeAs(first);
	}

	@Test
	void hashtagProjectionContractCanExposeRepositoryValues() {
		UUID id = UUID.randomUUID();
		HashtagProjection projection = new HashtagProjectionStub(id, "java", 12L);

		assertThat(projection.getId()).isEqualTo(id);
		assertThat(projection.getText()).isEqualTo("java");
		assertThat(projection.getCount()).isEqualTo(12L);
	}

	@Test
	void hashtagProjectionContractAllowsNullAggregateCount() {
		HashtagProjection projection = new HashtagProjectionStub(UUID.randomUUID(), "java", null);

		assertThat(projection.getCount()).isNull();
	}

	@Test
	void mentionDtoJsonUsesSnakeCaseUserId() throws JsonProcessingException {
		UUID userId = UUID.randomUUID();
		MentionDto dto = MentionDto.builder().id(UUID.randomUUID()).userId(userId).text("alice").build();

		String json = MAPPER.writeValueAsString(dto);

		assertThat(json).contains("\"user_id\":\"" + userId + "\"");
		assertThat(json).doesNotContain("userId");
	}

	@Test
	void hashtagDtoIgnoresUnknownFields() throws JsonProcessingException {
		HashtagDto dto = MAPPER.readValue("{\"text\":\"java\",\"count\":2,\"ignored\":true}", HashtagDto.class);

		assertThat(dto.getText()).isEqualTo("java");
		assertThat(dto.getCount()).isEqualTo(2L);
	}

	@Test
	void tweetDtoNestedCollectionsSurviveBuilder() {
		Set<MentionDto> mentions = Set.of(MentionDto.builder().text("alice").build());
		Set<HashtagDto> hashtags = Set.of(HashtagDto.builder().text("java").build());

		TweetDto dto = TweetDto.builder().mentions(mentions).hashtags(hashtags).build();

		assertThat(dto.getMentions()).isSameAs(mentions);
		assertThat(dto.getHashtags()).isSameAs(hashtags);
	}

	@Test
	void replyDtoJsonUsesSnakeCaseUserNameAndLikesCount() throws JsonProcessingException {
		ReplyDto reply = ReplyDto.builder().userName("alice").likesCount(4L).build();

		String json = MAPPER.writeValueAsString(reply);

		assertThat(json).contains("\"user_name\":\"alice\"", "\"likes_count\":4");
		assertThat(json).doesNotContain("userName", "likesCount");
	}

	private static <T> Set<ConstraintViolation<T>> violationsFor(T request) {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			return factory.getValidator().validate(request);
		}
	}

	private record HashtagProjectionStub(UUID id, String text, Long count) implements HashtagProjection {

		@Override
		public UUID getId() {
			return this.id;
		}

		@Override
		public String getText() {
			return this.text;
		}

		@Override
		public Long getCount() {
			return this.count;
		}

	}

}
